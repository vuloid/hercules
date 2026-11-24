# Security

## What the script check reads

When inspecting a ZIP module, Hercules reads non-directory entries whose names are root-level and end in `.sh` (case-sensitive), plus the exact path `META-INF/com/google/android/update-binary`. Nested shell scripts are not included. APKs are not inspected. Each selected file is read as text and capped at 65,536 characters. If script text is already supplied with the catalog entry, that text is inspected instead of opening the archive.

## Rules checked

The analyzer skips blank lines and lines whose trimmed text starts with `#`. It reports at most the first matching rule per line:

| Severity | Finding | Pattern checked |
| --- | --- | --- |
| High | Pipe to shell | `curl` or `wget` followed on the same line by a pipe into `sh`, `bash`, or `su` |
| High | Destructive delete | Recursive `rm` targeting `/`, `/system`, `/data`, `/vendor`, `/product`, `/sdcard`, or `/storage`, including a `/*` suffix |
| High | Raw disk write | `dd` command with an `of=/dev/` destination |
| Medium | Remote fetch | `curl` or `wget` followed on the same line by an `http://` or `https://` URL |
| Medium | Obfuscated execution | `base64 -d`, `base64 --decode`, or the `eval` command |
| Medium | SELinux disabled | `setenforce 0` |
| Low | Permissive permissions | `chmod 777`, `chmod 0777`, or recursive `chmod -R 777` / `chmod -R 0777` |

These checks are static regular expressions intended to highlight common dangerous patterns before flashing. They are not a safety guarantee: they do not analyze compiled native binaries, obfuscated payloads beyond the listed text patterns, or runtime behavior. Matching is line-based and can miss equivalent commands written in other forms.

## Reporting a vulnerability

Report security issues through [GitHub Security Advisories](https://github.com/vuloid/hercules/security/advisories/new) when private vulnerability reporting is available. Otherwise, open an [issue](https://github.com/vuloid/hercules/issues) and avoid including sensitive exploit details in a public report.
