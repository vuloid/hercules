#!/usr/bin/env python3
import argparse
import bz2
import json
import lzma
import os
import random
import re
import struct
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import zlib
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from itertools import zip_longest
from pathlib import Path

API_ROOT = "https://api.github.com"
RAW_ROOT = "https://raw.githubusercontent.com"
USER_AGENT = "Hercules-Catalog-Indexer"
API_VERSION = "2022-11-28"
MANAGERS = ("MAGISK", "KERNELSU", "APATCH")
README_LIMIT = 6000
CHANGELOG_LIMIT = 3000
DESCRIPTION_LIMIT = 500
ID_PATTERN = re.compile(r"^[A-Za-z0-9._-]{1,96}$")
PLACEHOLDER_PATTERN = re.compile(r"\$\{|\$\w+|\{\{|%[A-Z_]+%")
EXCLUDED_DIRS = {
    "test",
    "tests",
    "testing",
    "sample",
    "samples",
    "example",
    "examples",
    "template",
    "templates",
    "docs",
    "fixtures",
}
README_NAMES = ("readme.md", "readme.markdown", "readme.rst", "readme.txt", "readme")
UPDATE_JSON_KEYS = ("updateJson", "updateJsonUrl", "update_json")
STAR_BUCKETS = ("stars:>100", "stars:25..100", "stars:5..25", "stars:1..4")
SEARCH_TERMS = (
    "topic:magisk-module",
    "topic:kernelsu-module",
    "topic:apatch-module",
    "topic:zygisk-module",
    "topic:zygisk",
    "topic:lsposed-module",
    "topic:xposed-module",
    '"magisk module" in:name,description',
    '"kernelsu module" in:name,description',
    '"lsposed" in:name,description',
)
SEARCH_QUERIES = tuple(
    "{} {}".format(term, bucket) for bucket in STAR_BUCKETS for term in SEARCH_TERMS
)
SEARCH_PAGE_SIZE = 100
SEARCH_MAX_PAGES = 10
ORGANIZATIONS = (
    "Xposed-Modules-Repo",
    "Magisk-Modules-Alt-Repo",
    "Magisk-Modules-Repo",
)
INDEX_URLS = (
    "https://magisk-modules-alt-repo.github.io/json/modules.json",
    "https://apt.izzysoft.de/magisk/json/modules.json",
)
INDEX_URL_KEYS = {
    "source",
    "sources",
    "homepage",
    "support",
    "repo",
    "repository",
    "github",
    "url",
    "prop_url",
    "propUrl",
    "zip_url",
    "zipUrl",
    "notes_url",
    "notesUrl",
    "changelog",
}
GITHUB_REFERENCE = re.compile(
    r"^https?://(?:www\.)?(?:github\.com|raw\.githubusercontent\.com)/"
    r"([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)"
)
RESERVED_OWNERS = {
    "sponsors",
    "orgs",
    "topics",
    "features",
    "marketplace",
    "settings",
    "apps",
    "users",
    "user-attachments",
}
XPOSED_ORGANIZATION = "Xposed-Modules-Repo"
DISPLAY_NAME_LIMIT = 50
PACKAGE_NAME_PATTERN = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)+$")
SENTENCE_BREAK = re.compile(r"(?<=[.!?])\s+")
XPOSED_KEYWORDS = {"lsposed", "xposed"}
MAX_RATE_WAIT = 300.0
DEFAULT_MAX_REPOS = 2500
DEFAULT_WORKERS = 10
SEED_REPOS = (
    "5ec1cff/TrickyStore",
    "osm0sis/PlayIntegrityFork",
    "Dr-TSNG/ZygiskNext",
    "LSPosed/LSPosed.github.io",
    "JingMatrix/LSPosed",
    "snake-4/Zygisk-Assistant",
    "HuskyDG/magic_overlayfs",
    "yc9559/uperf",
    "Magisk-Modules-Repo/busybox-ndk",
    "Magisk-Modules-Repo/ssh",
)
FIELD_TYPES = {
    "id": str,
    "name": str,
    "author": str,
    "description": str,
    "version": str,
    "versionCode": int,
    "stars": int,
    "updatedAt": int,
    "sizeBytes": int,
    "downloadUrl": str,
    "repoUrl": str,
    "managers": list,
    "lsposed": bool,
    "readme": str,
    "changelog": str,
}
REQUIRED_FIELDS = tuple(FIELD_TYPES)


class GithubError(RuntimeError):
    pass


class NotFound(GithubError):
    pass


class Github:
    def __init__(self, token=None, timeout=30.0, retries=5):
        self.token = token
        self.timeout = timeout
        self.retries = retries
        self.search_interval = 2.2 if token else 6.5
        self._lock = threading.Lock()
        self._search_lock = threading.Lock()
        self._next_search = 0.0
        self._resume = {}
        self.rate_limit_remaining = None
        self.stop_for_rate_limit = threading.Event()

    def _headers(self, url, accept):
        headers = {
            "Accept": accept,
            "User-Agent": USER_AGENT,
            "X-GitHub-Api-Version": API_VERSION,
        }
        host = urllib.parse.urlparse(url).hostname
        if self.token and host in ("api.github.com", "raw.githubusercontent.com"):
            headers["Authorization"] = "Bearer " + self.token
        return headers

    def _await(self, host, url):
        while True:
            with self._lock:
                remaining = self._resume.get(host, 0.0) - time.time()
            if remaining <= 0:
                return
            if remaining > MAX_RATE_WAIT:
                raise GithubError("Rate limit exhausted for {}".format(url))
            time.sleep(remaining)

    def _pause(self, host, delay):
        with self._lock:
            until = time.time() + delay + random.uniform(0.0, 1.5)
            self._resume[host] = max(self._resume.get(host, 0.0), until)

    @staticmethod
    def _rate_limit_delay(error, body):
        if error.code not in (403, 429):
            return None
        headers = error.headers or {}
        retry_after = headers.get("Retry-After")
        if retry_after:
            try:
                return max(1.0, float(retry_after))
            except ValueError:
                return 30.0
        if headers.get("X-RateLimit-Remaining") == "0":
            try:
                return max(1.0, float(headers.get("X-RateLimit-Reset")) - time.time() + 2.0)
            except (TypeError, ValueError):
                return 60.0
        if error.code == 429 or "rate limit" in body.lower():
            return 60.0
        return None

    def _request(self, url, accept):
        host = urllib.parse.urlparse(url).hostname
        last_error = None
        for attempt in range(self.retries + 1):
            self._await(host, url)
            request = urllib.request.Request(url, headers=self._headers(url, accept))
            try:
                with urllib.request.urlopen(request, timeout=self.timeout) as response:
                    remaining = response.headers.get("X-RateLimit-Remaining")
                    if remaining is not None:
                        try:
                            remaining = int(remaining)
                            with self._lock:
                                if self.rate_limit_remaining is None or remaining < self.rate_limit_remaining:
                                    self.rate_limit_remaining = remaining
                            if remaining < 50:
                                self.stop_for_rate_limit.set()
                        except ValueError:
                            pass
                    return response.read()
            except urllib.error.HTTPError as error:
                if error.code == 404:
                    raise NotFound(url) from error
                last_error = error
                body = error.read().decode("utf-8", "replace")[:300]
                delay = self._rate_limit_delay(error, body)
                if delay is not None:
                    self._pause(host, delay)
                    if delay > MAX_RATE_WAIT or attempt >= self.retries:
                        raise GithubError("Rate limited for {}".format(url)) from error
                    continue
                if error.code >= 500 and attempt < self.retries:
                    time.sleep(2 ** attempt)
                    continue
                raise GithubError("HTTP {} for {}: {}".format(error.code, url, body)) from error
            except (urllib.error.URLError, TimeoutError, OSError) as error:
                last_error = error
                if attempt < self.retries:
                    time.sleep(2 ** attempt)
                    continue
                raise GithubError("Network error for {}: {}".format(url, error)) from error
        raise GithubError("Request failed for {}: {}".format(url, last_error))

    def json(self, path, params=None):
        url = path if path.startswith("http") else API_ROOT + path
        if params:
            url += "?" + urllib.parse.urlencode(params)
        try:
            payload = self._request(url, "application/vnd.github+json")
        except NotFound:
            return None
        try:
            return json.loads(payload.decode("utf-8"))
        except ValueError as error:
            raise GithubError("Invalid JSON from {}: {}".format(url, error)) from error

    def text(self, url, limit=1_000_000):
        try:
            payload = self._request(url, "text/plain")
        except NotFound:
            return ""
        return payload[:limit].decode("utf-8", "replace")

    def search_page(self, query, page):
        with self._search_lock:
            wait = self._next_search - time.monotonic()
            if wait > 0:
                time.sleep(wait)
            self._next_search = time.monotonic() + self.search_interval
        data = self.json(
            "/search/repositories",
            {
                "q": query,
                "sort": "stars",
                "order": "desc",
                "per_page": SEARCH_PAGE_SIZE,
                "page": page,
            },
        )
        return data if isinstance(data, dict) else {}

    def organization_page(self, organization, page):
        data = self.json(
            "/orgs/{}/repos".format(organization),
            {
                "type": "public",
                "sort": "updated",
                "direction": "desc",
                "per_page": 100,
                "page": page,
            },
        )
        return data if isinstance(data, list) else []


def raw_url(full_name, branch, path):
    return "{}/{}/{}/{}".format(
        RAW_ROOT,
        full_name,
        urllib.parse.quote(branch, safe="/"),
        urllib.parse.quote(path, safe="/"),
    )


def to_epoch_ms(value):
    if not value:
        return 0
    try:
        parsed = datetime.fromisoformat(str(value).replace("Z", "+00:00"))
    except ValueError:
        return 0
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return int(parsed.timestamp() * 1000)


def parse_int(value, default=0):
    try:
        return int(str(value).strip())
    except (TypeError, ValueError):
        return default


def numeric_version_code(version):
    digits = re.sub(r"[^0-9]", "", version or "")
    if not digits:
        return 0
    return min(int(digits), 2_147_483_647)


def single_line(value, limit):
    return re.sub(r"\s+", " ", value or "").strip()[:limit]


def clean_text(text, limit):
    if not text:
        return ""
    text = text.replace("\r\n", "\n").replace("\r", "\n")
    text = re.sub(r"<!--.*?-->", "", text, flags=re.S)
    text = re.sub(r"<picture>.*?</picture>", "", text, flags=re.S | re.I)
    text = re.sub(r"<img[^>]*>", "", text, flags=re.I)
    text = re.sub(r"^\s*\[!\[.*$", "", text, flags=re.M)
    text = re.sub(r"!\[[^\]]*\]\([^)]*\)", "", text)
    text = re.sub(r"\[([^\]]+)\]\([^)]*\)", r"\1", text)
    text = re.sub(r"<[^>]+>", "", text)
    text = re.sub(r"^#{1,6}\s+", "", text, flags=re.M)
    text = re.sub(r"^\s*[-*_]{3,}\s*$", "", text, flags=re.M)
    text = re.sub(r"^\s*>\s?", "", text, flags=re.M)
    text = re.sub(
        r"^\s*\[!(?:TIP|NOTE|IMPORTANT|WARNING|CAUTION)\]\s*$", "", text, flags=re.M
    )
    text = re.sub(r"```[^\n]*\n", "", text)
    text = re.sub(r"```", "", text)
    text = re.sub(r"`([^`\n]*)`", r"\1", text)
    text = re.sub(r"\*\*([^*\n]+)\*\*", r"\1", text)
    text = re.sub(r"__([^_\n]+)__", r"\1", text)
    text = re.sub(r"^\s*([-*+])\s+", "- ", text, flags=re.M)
    text = re.sub(r"[ \t]+\n", "\n", text)
    text = re.sub(r"\n{3,}", "\n\n", text).strip()
    if len(text) > limit:
        text = text[:limit]
        cut = max(text.rfind("\n"), text.rfind(". "))
        if cut > limit // 2:
            text = text[:cut]
        text = text.rstrip() + "..."
    return text


def parse_module_prop(text):
    values = {}
    for raw in (text or "").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        if key:
            values[key] = value
    return values


def select_module_prop(paths):
    path_set = set(paths)
    candidates = []
    for path in paths:
        if path.rsplit("/", 1)[-1] != "module.prop":
            continue
        if path.startswith("META-INF/"):
            continue
        parent = path.rsplit("/", 1)[0] if "/" in path else ""
        segments = [segment.lower() for segment in parent.split("/") if segment]
        if any(segment in EXCLUDED_DIRS for segment in segments):
            continue
        score = -len(segments) * 6
        if not parent:
            score += 30
        siblings = {
            item.rsplit("/", 1)[-1]
            for item in paths
            if (item.rsplit("/", 1)[0] if "/" in item else "") == parent
        }
        if "customize.sh" in siblings:
            score += 20
        if "META-INF" in siblings:
            score += 12
        if "service.sh" in siblings:
            score += 8
        if "post-fs-data.sh" in siblings:
            score += 6
        if "system" in siblings:
            score += 6
        candidates.append((score, path))
    if not candidates:
        return "module.prop" if "module.prop" in path_set else None
    candidates.sort(key=lambda item: (-item[0], item[1]))
    return candidates[0][1]


def select_readme(paths):
    for name in README_NAMES:
        for path in paths:
            if path.lower() == name:
                return path
    matches = [
        path
        for path in paths
        if path.rsplit("/", 1)[-1].lower() in README_NAMES and path.count("/") <= 1
    ]
    if not matches:
        return None
    return min(matches, key=lambda path: (path.count("/"), path))


def has_placeholder(value):
    return bool(value and PLACEHOLDER_PATTERN.search(str(value)))


def prop_is_usable(prop):
    if not ID_PATTERN.match((prop or {}).get("id", "").strip()):
        return False
    if has_placeholder(prop.get("version")):
        return False
    return not has_placeholder(prop.get("versionCode"))


def resolve_asset(url):
    try:
        request = urllib.request.Request(
            url, headers={"User-Agent": USER_AGENT, "Accept": "*/*"}, method="HEAD"
        )
        with urllib.request.urlopen(request, timeout=30) as response:
            size = parse_int(response.headers.get("Content-Length"))
            if size > 0:
                return response.geturl(), size
    except (urllib.error.URLError, TimeoutError, OSError):
        pass
    request = urllib.request.Request(
        url,
        headers={"User-Agent": USER_AGENT, "Accept": "*/*", "Range": "bytes=0-0"},
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        content_range = response.headers.get("Content-Range") or ""
        match = re.search(r"/(\d+)\s*$", content_range)
        size = int(match.group(1)) if match else parse_int(response.headers.get("Content-Length"))
        return response.geturl(), size


def fetch_range(url, start, length):
    if start < 0 or length <= 0:
        return b""
    end = start + length - 1
    request = urllib.request.Request(
        url,
        headers={
            "User-Agent": USER_AGENT,
            "Accept": "*/*",
            "Range": "bytes={}-{}".format(start, end),
        },
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        status = getattr(response, "status", 200)
        if status == 206:
            return response.read()
        if status == 200 and start == 0:
            return response.read(length)
        return b""


def zip64_values(extra, compressed, uncompressed, offset):
    position = 0
    while position + 4 <= len(extra):
        tag, size = struct.unpack_from("<HH", extra, position)
        body = extra[position + 4:position + 4 + size]
        if tag == 0x0001:
            cursor = 0
            if uncompressed == 0xFFFFFFFF and cursor + 8 <= len(body):
                uncompressed = struct.unpack_from("<Q", body, cursor)[0]
                cursor += 8
            if compressed == 0xFFFFFFFF and cursor + 8 <= len(body):
                compressed = struct.unpack_from("<Q", body, cursor)[0]
                cursor += 8
            if offset == 0xFFFFFFFF and cursor + 8 <= len(body):
                offset = struct.unpack_from("<Q", body, cursor)[0]
            break
        position += 4 + size
    return compressed, uncompressed, offset


def read_zip_entry(url, method, compressed, offset):
    if compressed <= 0 or compressed > 1_000_000:
        return ""
    header = fetch_range(url, offset, 30)
    if len(header) < 30 or header[:4] != b"PK\x03\x04":
        return ""
    name_length = struct.unpack_from("<H", header, 26)[0]
    extra_length = struct.unpack_from("<H", header, 28)[0]
    data = fetch_range(url, offset + 30 + name_length + extra_length, compressed)
    if method == 0:
        return data.decode("utf-8", "replace")
    if method == 8:
        try:
            return zlib.decompress(data, -15).decode("utf-8", "replace")
        except zlib.error:
            return ""
    if method == 12:
        try:
            return bz2.decompress(data).decode("utf-8", "replace")
        except (OSError, ValueError):
            return ""
    if method == 14 and len(data) >= 4:
        properties_length = struct.unpack_from("<H", data, 2)[0]
        properties = data[4:4 + properties_length]
        if len(properties) == 5:
            marker = properties[0]
            dictionary = struct.unpack_from("<I", properties, 1)[0]
            filters = [
                {
                    "id": lzma.FILTER_LZMA1,
                    "dict_size": dictionary,
                    "lc": marker % 9,
                    "lp": (marker // 9) % 5,
                    "pb": marker // 45,
                }
            ]
            try:
                return lzma.decompress(
                    data[4 + properties_length:], format=lzma.FORMAT_RAW, filters=filters
                ).decode("utf-8", "replace")
            except lzma.LZMAError:
                return ""
    if method == 95:
        try:
            return lzma.decompress(data).decode("utf-8", "replace")
        except lzma.LZMAError:
            return ""
    return ""


def read_zip_module_prop(url):
    try:
        asset_url, total = resolve_asset(url)
        if total <= 0:
            return None, False
        tail_length = min(total, 131072)
        tail = fetch_range(asset_url, total - tail_length, tail_length)
        end = tail.rfind(b"PK\x05\x06")
        if end < 0 or end + 22 > len(tail):
            return None, False
        total_entries = struct.unpack_from("<H", tail, end + 10)[0]
        directory_size = struct.unpack_from("<I", tail, end + 12)[0]
        directory_offset = struct.unpack_from("<I", tail, end + 16)[0]
        if (
            total_entries == 0xFFFF
            or directory_size == 0xFFFFFFFF
            or directory_offset == 0xFFFFFFFF
        ):
            return None, False
        if directory_size <= 0 or directory_size > 8_000_000:
            return None, False
        directory = fetch_range(asset_url, directory_offset, directory_size)
        candidates = []
        xposed = False
        position = 0
        while position + 46 <= len(directory) and directory[position:position + 4] == b"PK\x01\x02":
            flags = struct.unpack_from("<H", directory, position + 8)[0]
            method = struct.unpack_from("<H", directory, position + 10)[0]
            compressed = struct.unpack_from("<I", directory, position + 20)[0]
            uncompressed = struct.unpack_from("<I", directory, position + 24)[0]
            name_length = struct.unpack_from("<H", directory, position + 28)[0]
            extra_length = struct.unpack_from("<H", directory, position + 30)[0]
            comment_length = struct.unpack_from("<H", directory, position + 32)[0]
            offset = struct.unpack_from("<I", directory, position + 42)[0]
            name = directory[position + 46:position + 46 + name_length].decode(
                "utf-8", "replace"
            )
            extra = directory[
                position + 46 + name_length:position + 46 + name_length + extra_length
            ]
            compressed, uncompressed, offset = zip64_values(
                extra, compressed, uncompressed, offset
            )
            if not flags & 0x0001 and name.rsplit("/", 1)[-1] == "module.prop":
                if name.startswith("META-INF/xposed/"):
                    xposed = True
                elif not name.startswith("META-INF/"):
                    candidates.append(
                        {
                            "name": name,
                            "method": method,
                            "compressed": compressed,
                            "offset": offset,
                        }
                    )
            position += 46 + name_length + extra_length + comment_length
        if not candidates:
            return None, xposed
        chosen = select_module_prop([candidate["name"] for candidate in candidates])
        entry = next(candidate for candidate in candidates if candidate["name"] == chosen)
        prop = parse_module_prop(
            read_zip_entry(asset_url, entry["method"], entry["compressed"], entry["offset"])
        )
        if not ID_PATTERN.match(prop.get("id", "").strip()):
            return None, xposed
        return prop, xposed
    except (
        urllib.error.URLError,
        TimeoutError,
        OSError,
        struct.error,
        ValueError,
        StopIteration,
    ):
        return None, False


def detect_managers(info, prop, paths, readme):
    topics = " ".join(info.get("topics") or []).lower()
    description = (info.get("description") or "").lower()
    prop_blob = " ".join("{}={}".format(k, v) for k, v in prop.items()).lower()
    path_blob = " ".join(paths).lower()
    probe = " ".join((topics, description, prop_blob, readme[:4000].lower(), path_blob))
    detected = []
    if "kernelsu" in probe or "ksud" in probe or re.search(r"(^|[^a-z0-9])ksu([^a-z0-9]|$)", probe):
        detected.append("KERNELSU")
    if "apatch" in probe or re.search(r"(^|[^a-z0-9])apd([^a-z0-9]|$)", probe):
        detected.append("APATCH")
    if detected:
        return ["MAGISK"] + detected
    return list(MANAGERS)


def detect_lsposed(info, prop, paths, zip_xposed=False):
    if zip_xposed:
        return True
    for path in paths:
        if path == "META-INF/xposed" or path.startswith("META-INF/xposed/"):
            return True
        if path.endswith("/META-INF/xposed/module.prop"):
            return True
    if any("xposed" in key.lower() for key in prop):
        return True
    topics = {str(topic).lower() for topic in (info.get("topics") or [])}
    return "lsposed" in topics or "xposed" in topics


def latest_release(github, full_name):
    release = github.json("/repos/{}/releases/latest".format(full_name))
    if isinstance(release, dict):
        return release
    releases = github.json("/repos/{}/releases".format(full_name), {"per_page": 10})
    if not isinstance(releases, list):
        return None
    for item in releases:
        if isinstance(item, dict) and not item.get("draft"):
            return item
    return None


def release_assets(release):
    if not isinstance(release, dict):
        return []
    assets = [
        asset
        for asset in (release.get("assets") or [])
        if asset.get("name") and asset.get("browser_download_url")
    ]
    zips = sorted(
        (asset for asset in assets if asset["name"].lower().endswith(".zip")),
        key=lambda asset: (0 if "module" in asset["name"].lower() else 1, asset["name"].lower()),
    )
    apks = sorted(
        (asset for asset in assets if asset["name"].lower().endswith(".apk")),
        key=lambda asset: asset["name"].lower(),
    )
    return [
        {"url": asset["browser_download_url"], "size": parse_int(asset.get("size"))}
        for asset in zips + apks
    ]


def remote_size(url):
    request = urllib.request.Request(
        url, headers={"User-Agent": USER_AGENT, "Accept": "*/*"}, method="HEAD"
    )
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return parse_int(response.headers.get("Content-Length"))
    except (urllib.error.URLError, TimeoutError, OSError, ValueError):
        return 0


def is_xposed_repository(info, full_name):
    owner = full_name.split("/", 1)[0].lower()
    if owner == XPOSED_ORGANIZATION.lower():
        return True
    topics = {str(topic).lower() for topic in (info.get("topics") or [])}
    if topics & XPOSED_KEYWORDS:
        return True
    text = " ".join((info.get("name") or "", info.get("description") or "")).lower()
    return any(keyword in text for keyword in XPOSED_KEYWORDS)


def xposed_apk(info, full_name, release_downloads):
    if not is_xposed_repository(info, full_name):
        return None
    for asset in release_downloads:
        if urllib.parse.urlparse(str(asset["url"])).path.lower().endswith(".apk"):
            return asset
    return None


def repository_display_name(info, full_name, module_id):
    repo_name = info.get("name") or module_id
    owner = full_name.split("/", 1)[0].lower()
    if owner != XPOSED_ORGANIZATION.lower() or not PACKAGE_NAME_PATTERN.match(repo_name):
        return single_line(repo_name, 100)
    description = single_line(info.get("description"), DESCRIPTION_LIMIT)
    sentence = SENTENCE_BREAK.split(description, 1)[0].rstrip(".!?").strip()
    label = sentence[:DISPLAY_NAME_LIMIT].rstrip()
    return label or single_line(repo_name, 100)


def apk_module_id(info, full_name):
    name = info.get("name") or full_name.split("/", 1)[-1]
    if ID_PATTERN.match(name):
        return name
    cleaned = re.sub(r"[^A-Za-z0-9._-]", "_", name).strip("_")[:96]
    return cleaned or re.sub(r"[^A-Za-z0-9._-]", "_", full_name)[:96]


def update_json_download(github, prop, branch, full_name):
    location = ""
    for key in UPDATE_JSON_KEYS:
        if prop.get(key):
            location = prop[key].strip()
            break
    if not location:
        return None
    if location.startswith("https://"):
        url = location
    elif location.startswith("http://"):
        return None
    else:
        url = raw_url(full_name, branch, location.lstrip("/"))
    data = github.json(url)
    if not isinstance(data, dict):
        return None
    zip_url = data.get("zipUrl") or data.get("zip_url") or data.get("downloadUrl")
    if not isinstance(zip_url, str) or not zip_url.startswith("https://"):
        return None
    size = parse_int(data.get("size"))
    if size <= 0:
        size = remote_size(zip_url)
    return {
        "url": zip_url,
        "size": size,
        "version": single_line(data.get("version"), 100),
        "versionCode": parse_int(data.get("versionCode") or data.get("version_code")),
    }


def repo_zip_download(github, full_name, branch):
    contents = github.json("/repos/{}/contents/".format(full_name))
    if not isinstance(contents, list):
        return None
    archives = [
        item
        for item in contents
        if item.get("type") == "file"
        and str(item.get("name", "")).lower().endswith(".zip")
        and not str(item.get("name", "")).lower().startswith("source")
    ]
    if not archives:
        return None
    archives.sort(key=lambda item: str(item.get("name", "")).lower())
    asset = archives[0]
    return {
        "url": raw_url(full_name, branch, asset["name"]),
        "size": parse_int(asset.get("size")),
    }


def build_module(github, full_name, info, warnings):
    if not isinstance(info, dict) or not info.get("default_branch"):
        info = github.json("/repos/{}".format(full_name))
    if not isinstance(info, dict):
        warnings.append("{}: repository not available".format(full_name))
        return None
    if info.get("fork") or info.get("archived"):
        return None
    branch = info.get("default_branch") or "main"
    tree = github.json(
        "/repos/{}/git/trees/{}".format(
            full_name, urllib.parse.quote(branch, safe="")
        ),
        {"recursive": 1},
    )
    paths = [
        entry["path"]
        for entry in (tree or {}).get("tree", [])
        if entry.get("type") == "blob" and entry.get("path")
    ]
    prop_path = select_module_prop(paths)
    repo_prop = (
        parse_module_prop(github.text(raw_url(full_name, branch, prop_path)))
        if prop_path
        else {}
    )

    release = latest_release(github, full_name)
    release_downloads = release_assets(release)
    download = release_downloads[0] if release_downloads else None
    update = None
    if download is None:
        update = update_json_download(github, repo_prop, branch, full_name)
        if update:
            download = {"url": update["url"], "size": update["size"]}
    if download is None:
        download = repo_zip_download(github, full_name, branch)
    if download is None:
        warnings.append("{}: no downloadable module archive".format(full_name))
        return None

    prop = repo_prop
    zip_xposed = False
    if not prop_is_usable(repo_prop) or parse_int(repo_prop.get("versionCode")) <= 0:
        candidates = release_downloads or [download]
        for candidate in candidates:
            if not urllib.parse.urlparse(str(candidate["url"])).path.lower().endswith(".zip"):
                continue
            zip_prop, candidate_xposed = read_zip_module_prop(candidate["url"])
            if zip_prop:
                download = candidate
                zip_xposed = candidate_xposed
                merged = dict(repo_prop)
                merged.update({key: value for key, value in zip_prop.items() if value})
                prop = merged
                break

    module_id = prop.get("id", "").strip()
    if not ID_PATTERN.match(module_id):
        apk = xposed_apk(info, full_name, release_downloads)
        if apk is None:
            warnings.append("{}: module.prop not found or invalid".format(full_name))
            return None
        download = apk
        prop = {}
        zip_xposed = True
        module_id = apk_module_id(info, full_name)
        update = None

    readme_path = select_readme(paths)
    readme = clean_text(
        github.text(raw_url(full_name, branch, readme_path)) if readme_path else "",
        README_LIMIT,
    )

    version = single_line(prop.get("version"), 100)
    version_code = parse_int(prop.get("versionCode"))
    if has_placeholder(version) or not version:
        version = single_line((release or {}).get("tag_name"), 100)
    if update:
        if version_code <= 0 and update["versionCode"] > 0:
            version_code = update["versionCode"]
        if update["version"] and (not version or has_placeholder(version)):
            version = update["version"]
    if has_placeholder(version):
        version = ""
    if version_code <= 0:
        version_code = numeric_version_code(version)
    version_code = min(max(version_code, 0), 2_147_483_647)

    owner = (info.get("owner") or {}).get("login") or ""
    name = single_line(prop.get("name"), 100)
    if not name or has_placeholder(name):
        name = repository_display_name(info, full_name, module_id)
    author = single_line(prop.get("author"), 100)
    if not author or has_placeholder(author):
        author = single_line(owner, 100)
    description = prop.get("description") or info.get("description") or ""
    if has_placeholder(description):
        description = info.get("description") or ""

    updated_at = to_epoch_ms(info.get("pushed_at"))
    if release:
        updated_at = max(updated_at, to_epoch_ms(release.get("published_at")))

    module = {
        "id": module_id,
        "name": name,
        "author": author,
        "description": single_line(description, DESCRIPTION_LIMIT),
        "version": version,
        "versionCode": version_code,
        "stars": max(0, parse_int(info.get("stargazers_count"))),
        "updatedAt": max(0, updated_at),
        "sizeBytes": max(0, parse_int(download.get("size"))),
        "downloadUrl": download["url"],
        "repoUrl": info.get("html_url") or "https://github.com/" + full_name,
        "managers": detect_managers(info, prop, paths, readme),
        "lsposed": detect_lsposed(info, prop, paths, zip_xposed),
        "readme": readme,
        "changelog": clean_text((release or {}).get("body") or "", CHANGELOG_LIMIT),
    }
    return module


def candidate_from_item(item):
    if not isinstance(item, dict):
        return None
    full_name = item.get("full_name")
    if not full_name or item.get("fork") or item.get("archived"):
        return None
    return full_name, item


def search_candidates(github, limit, warnings):
    found = {}
    for query in SEARCH_QUERIES:
        if len(found) >= limit:
            break
        for page in range(1, SEARCH_MAX_PAGES + 1):
            try:
                data = github.search_page(query, page)
            except GithubError as error:
                warnings.append("search '{}' page {} failed: {}".format(query, page, error))
                break
            items = data.get("items") or []
            for item in items:
                candidate = candidate_from_item(item)
                if candidate:
                    found.setdefault(candidate[0].lower(), candidate)
            total = parse_int(data.get("total_count"))
            if (
                len(items) < SEARCH_PAGE_SIZE
                or page * SEARCH_PAGE_SIZE >= total
                or len(found) >= limit
            ):
                break
    return list(found.values())


def organization_candidates(github, organization, limit, warnings):
    found = []
    page = 1
    while len(found) < limit:
        try:
            items = github.organization_page(organization, page)
        except GithubError as error:
            warnings.append("organization {} page {} failed: {}".format(organization, page, error))
            break
        for item in items:
            candidate = candidate_from_item(item)
            if candidate:
                found.append(candidate)
        if len(items) < 100:
            break
        page += 1
    return found[:limit]


def repository_from_url(value):
    match = GITHUB_REFERENCE.match(value.strip())
    if not match:
        return None
    owner, repo = match.groups()
    if owner.lower() in RESERVED_OWNERS:
        return None
    if repo.endswith(".git"):
        repo = repo[:-4]
    if not repo:
        return None
    return "{}/{}".format(owner, repo)


def index_references(node, key=None):
    if isinstance(node, str):
        if key in INDEX_URL_KEYS:
            yield node
    elif isinstance(node, dict):
        for child_key, value in node.items():
            yield from index_references(value, child_key)
    elif isinstance(node, list):
        for value in node:
            yield from index_references(value, key)


def index_candidates(github, url, warnings):
    try:
        data = github.json(url)
    except GithubError as error:
        warnings.append("index {} failed: {}".format(url, error))
        return []
    if data is None:
        warnings.append("index {} not available".format(url))
        return []
    found = {}
    for reference in index_references(data):
        full_name = repository_from_url(reference)
        if full_name:
            found.setdefault(full_name.lower(), (full_name, None))
    return list(found.values())


def interleave(groups):
    return [item for batch in zip_longest(*groups) for item in batch if item is not None]


def discover_repositories(github, limit, warnings):
    with ThreadPoolExecutor(max_workers=2 + len(ORGANIZATIONS)) as pool:
        search = pool.submit(search_candidates, github, limit, warnings)
        indexes = [pool.submit(index_candidates, github, url, warnings) for url in INDEX_URLS]
        organizations = [
            pool.submit(organization_candidates, github, organization, limit, warnings)
            for organization in ORGANIZATIONS
        ]
        searched = search.result()
        indexed = interleave([future.result() for future in indexes])
        organized = interleave([future.result() for future in organizations])
    return interleave([indexed, searched, organized])


def merge_candidates(candidates):
    merged = {}
    order = []
    for full_name, info in candidates:
        full_name = full_name.strip()
        key = full_name.lower()
        if not key:
            continue
        if key not in merged:
            merged[key] = (full_name, info)
            order.append(key)
        elif merged[key][1] is None and info is not None:
            merged[key] = (merged[key][0], info)
    return [merged[key] for key in order]


def repository_key(module):
    repo_url = module.get("repoUrl", "")
    match = GITHUB_REFERENCE.match(repo_url)
    return "{}/{}".format(match.group(1), match.group(2)).lower() if match else None


def load_existing_catalog(path):
    try:
        catalog = json.loads(Path(path).read_text(encoding="utf-8"))
    except FileNotFoundError:
        return {"modules": []}, {}, {}
    if not isinstance(catalog, dict) or not isinstance(catalog.get("modules"), list):
        raise ValueError("existing catalog must contain a modules array")
    by_repo = {}
    by_id = {}
    for module in catalog["modules"]:
        if not isinstance(module, dict):
            continue
        module_id = module.get("id")
        if module_id:
            by_id[module_id.lower()] = module
        key = repository_key(module)
        if key:
            by_repo[key] = module
    return catalog, by_repo, by_id


def should_refresh(full_name, info, by_repo, by_id, full_refresh):
    if full_refresh:
        return True
    key = full_name.lower()
    existing = by_repo.get(key)
    if existing is None:
        # Repository names are often also module IDs; retain that lookup fallback.
        existing = by_id.get(full_name.rsplit("/", 1)[-1].lower())
    if existing is None:
        return True
    pushed_at = to_epoch_ms((info or {}).get("pushed_at"))
    stored_at = parse_int(existing.get("updatedAt"))
    return pushed_at > stored_at


def validate_catalog(catalog):
    if not isinstance(catalog, dict):
        raise ValueError("catalog must be a JSON object")
    modules = catalog.get("modules")
    if not isinstance(modules, list):
        raise ValueError("catalog.modules must be an array")
    seen = set()
    for index, module in enumerate(modules):
        if not isinstance(module, dict):
            raise ValueError("module {} must be an object".format(index))
        missing = [field for field in REQUIRED_FIELDS if field not in module]
        if missing:
            raise ValueError("module {} is missing {}".format(index, ", ".join(missing)))
        extra = sorted(set(module) - set(REQUIRED_FIELDS))
        if extra:
            raise ValueError("module {} has unknown keys {}".format(index, ", ".join(extra)))
        for field, expected in FIELD_TYPES.items():
            value = module[field]
            if expected is int:
                if isinstance(value, bool) or not isinstance(value, int) or value < 0:
                    raise ValueError("module {} has invalid {}".format(index, field))
            elif expected is bool:
                if not isinstance(value, bool):
                    raise ValueError("module {} has invalid {}".format(index, field))
            elif not isinstance(value, expected):
                raise ValueError("module {} has invalid {}".format(index, field))
        if not ID_PATTERN.match(module["id"]):
            raise ValueError("module {} has invalid id".format(index))
        if module["id"] in seen:
            raise ValueError("duplicate module id {}".format(module["id"]))
        seen.add(module["id"])
        managers = module["managers"]
        if not managers or len(set(managers)) != len(managers):
            raise ValueError("module {} has invalid managers".format(index))
        if any(manager not in MANAGERS for manager in managers):
            raise ValueError("module {} has unknown manager".format(index))
        if not module["downloadUrl"].startswith("https://"):
            raise ValueError("module {} has invalid downloadUrl".format(index))
        if module["repoUrl"] and not module["repoUrl"].startswith("https://"):
            raise ValueError("module {} has invalid repoUrl".format(index))
    return modules


def write_catalog(path, catalog):
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_name(target.name + ".tmp")
    payload = json.dumps(catalog, ensure_ascii=False, indent=2) + "\n"
    temporary.write_text(payload, encoding="utf-8")
    temporary.replace(target)


def parse_args():
    parser = argparse.ArgumentParser(
        description="Scrape GitHub for Magisk, KernelSU and APatch modules and write catalog/modules.json."
    )
    parser.add_argument("--out", default="catalog/modules.json", help="output path")
    parser.add_argument(
        "--max-repos", type=int, default=DEFAULT_MAX_REPOS, help="maximum repositories to inspect"
    )
    parser.add_argument(
        "--workers", type=int, default=DEFAULT_WORKERS, help="parallel repository workers"
    )
    parser.add_argument("--seed-only", action="store_true", help="skip GitHub discovery")
    parser.add_argument("--no-seeds", action="store_true", help="skip the built-in seed repositories")
    parser.add_argument(
        "--include", action="append", default=[], metavar="OWNER/REPO", help="extra repository"
    )
    parser.add_argument("--validate-only", metavar="FILE", help="validate an existing catalog")
    parser.add_argument("--allow-empty", action="store_true", help="write an empty catalog")
    parser.add_argument(
        "--full-refresh", action="store_true", help="refresh details for all discovered repositories"
    )
    return parser.parse_args()


def run():
    args = parse_args()

    if args.validate_only:
        try:
            catalog = json.loads(Path(args.validate_only).read_text(encoding="utf-8"))
            modules = validate_catalog(catalog)
        except (OSError, ValueError, json.JSONDecodeError) as error:
            print("invalid catalog: {}".format(error), file=sys.stderr)
            return 1
        print("validated {} modules in {}".format(len(modules), args.validate_only))
        return 0

    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
    github = Github(token)
    if not token:
        print("warning: GITHUB_TOKEN is not set, anonymous rate limits apply", file=sys.stderr)

    try:
        existing_catalog, existing_by_repo, existing_by_id = load_existing_catalog(args.out)
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print("could not load existing catalog: {}".format(error), file=sys.stderr)
        return 1

    warnings = []
    candidates = [] if args.no_seeds else [(name, None) for name in SEED_REPOS]
    candidates += [(name, None) for name in args.include]
    if not args.seed_only:
        remaining = max(0, args.max_repos - len(candidates))
        if remaining:
            candidates += discover_repositories(github, remaining, warnings)

    unique_candidates = merge_candidates(candidates)[: max(0, args.max_repos)]
    refresh_candidates = [
        (full_name, info)
        for full_name, info in unique_candidates
        if should_refresh(full_name, info, existing_by_repo, existing_by_id, args.full_refresh)
    ]
    skipped = len(unique_candidates) - len(refresh_candidates)
    workers = max(1, args.workers)

    print(
        "refreshing {} repositories with {} workers ({} unchanged skipped)".format(
            len(refresh_candidates), workers, skipped
        ),
        file=sys.stderr,
    )
    modules = []
    with ThreadPoolExecutor(max_workers=workers) as pool:
        futures = {
            pool.submit(build_module, github, full_name, info, warnings): full_name
            for full_name, info in refresh_candidates
        }
        for future in as_completed(futures):
            if github.stop_for_rate_limit.is_set():
                warnings.append("GitHub API rate limit below 50; stopping detail refresh")
                for pending in futures:
                    pending.cancel()
                break
            full_name = futures[future]
            try:
                module = future.result()
            except GithubError as error:
                warnings.append("{}: {}".format(full_name, error))
                continue
            except Exception as error:
                warnings.append(
                    "{}: {}: {}".format(full_name, error.__class__.__name__, error)
                )
                continue
            if module:
                modules.append(module)
                print(
                    "indexed {} -> {}".format(full_name, module["id"]),
                    file=sys.stderr,
                )

            if github.stop_for_rate_limit.is_set():
                warnings.append("GitHub API rate limit below 50; stopping detail refresh")
                for pending in futures:
                    pending.cancel()
                break

    # Start with the entire existing catalog. Incremental runs only fetch a
    # subset of repositories, so their results must update this full map.
    merged = {
        module["id"]: module
        for module in existing_catalog["modules"]
        if module.get("id")
    }
    for module in modules:
        merged[module["id"]] = module
    ordered = sorted(merged.values(), key=lambda item: (-item["stars"], item["name"].lower(), item["id"]))
    catalog = {"modules": ordered}

    try:
        validate_catalog(catalog)
    except ValueError as error:
        print("generated catalog is invalid: {}".format(error), file=sys.stderr)
        return 1

    for line in sorted(warnings):
        print("warning: " + line, file=sys.stderr)

    if not ordered and not args.allow_empty:
        print("catalog is empty, refusing to overwrite (use --allow-empty)", file=sys.stderr)
        return 1

    write_catalog(args.out, catalog)
    print(
        "wrote {} modules to {} ({} warnings)".format(len(ordered), args.out, len(warnings))
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(run())
