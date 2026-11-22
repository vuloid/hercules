# Hercules architecture

Root module store for Android. Kotlin, Jetpack Compose (Material 3), MVVM, single activity. No GMS, Firebase, analytics, ads or network fonts.

## Setup

- Put `inter_regular.ttf`, `inter_semibold.ttf` and `jetbrains_mono.ttf` in `app/src/main/res/font/`. The build fails without them.
- Generate the wrapper once: `gradle wrapper --gradle-version 8.9`.
- Remote catalog: set `CATALOG_URL` in `app/build.gradle.kts` (`buildConfigField`). Blank means the built-in sample data in `MockModules`.

## Layout

```
app/src/main/java/app/hercules/store
  HerculesApp.kt            Application, AppContainer (manual DI: OkHttp, ModuleRepository, RootExecutor)
  MainActivity.kt           Single activity, edge-to-edge, HerculesTheme { HerculesRoot() }
  data/
    ModuleRepository.kt     Catalog StateFlow, download Flow, script inspection
    MockModules.kt          Sample catalog (illustrative, not live data)
    ScriptAnalyzer.kt       Regex rules over install/service scripts
    GithubUrl.kt            https://github.com/owner/repo parser
    model/ModuleItem.kt     ModuleItem, CatalogState, InstalledModule, DownloadState, ScriptReport
    remote/ModuleApi.kt     OkHttp + kotlinx.serialization: catalog JSON, GitHub API, file download
  root/
    RootExecutor.kt         su runner, manager detection, installer commands, module.prop parser
  ui/
    HerculesRoot.kt         Scaffold, bottom navigation, NavHost, console sheet host
    ViewModelFactory.kt     Single factory wired to AppContainer
    theme/                  Color (graphite fallback), Type (Inter, JetBrains Mono), Theme (dynamic color on 12+)
    components/             HerculesCard, MonoBadge, buttons, formatters
    catalog/                CatalogViewModel, CatalogScreen, FilterRow, ModuleCard
    details/                DetailsViewModel, ModuleDetailsSheet (info, changelog, Inspect Scripts)
    install/                InstallViewModel (activity scoped), ConsoleSheet (live log, Reboot)
    installed/              InstalledViewModel, InstalledScreen (versionCode comparison)
```

## Data flow

```
Compose screen -> ViewModel (StateFlow<UiState>) -> ModuleRepository / RootExecutor -> ModuleApi / su
```

- Catalog: `ModuleRepository.refresh()` loads `MockModules` or `CATALOG_URL` into `catalog: StateFlow<CatalogState>`. `CatalogViewModel` combines it with filter state (query, root filter, sort, hide outdated) and the GitHub lookup state into `CatalogUiState`. Pasting a GitHub URL triggers `lookupGithub` (repo info plus latest release asset) and shows a card above the list.
- Details: selecting a card sets `CatalogUiState.selected`; `ModuleDetailsSheet` renders it. Inspect Scripts calls `ModuleRepository.inspectScripts`, which uses embedded scripts or downloads the archive and reads top-level `*.sh` plus `update-binary`, then runs `ScriptAnalyzer`.
- Install: any Install or Update button calls `InstallViewModel.install(module)`. Pipeline: `ModuleRepository.download` (cached in `cacheDir/downloads`) -> `RootExecutor.detectManager` (ksud, apd, magisk) -> `RootExecutor.installModule`, which streams stdout and stderr as `ShellEvent`s into `InstallUiState.log`. `ConsoleSheet` renders the log; Reboot appears on success. The sheet cannot be dismissed while running.
- Installed: `InstalledViewModel.refresh()` reads `/data/adb/modules/*/module.prop` through `su`, then matches by `id` against the catalog and flags entries where catalog `versionCode` is greater.

## Catalog JSON schema

```json
{ "modules": [ {
  "id": "tricky_store", "name": "TrickyStore", "author": "5ec1cff",
  "description": "...", "version": "1.3.0", "versionCode": 130,
  "stars": 1420, "updatedAt": 1760000000000, "sizeBytes": 612000,
  "downloadUrl": "https://.../module.zip", "repoUrl": "https://github.com/...",
  "managers": ["MAGISK", "KERNELSU", "APATCH"], "lsposed": false,
  "readme": "", "changelog": ""
} ] }
```

`id` must equal the `id` in the module's `module.prop`; that is how installed modules are matched. Unknown keys are ignored.

## Extending

- New filter: add to `Filters` and `applyFilters` in `CatalogViewModel`, add a chip in `FilterRow`, add strings.
- New root manager: extend `RootManager`, `RootExecutor.installCommand` and the detection command.
- New script rule: add a `Rule` in `ScriptAnalyzer`, a `FindingKind` value, and a string mapped in `ModuleDetailsSheet`.
- Persistence (favorites, cached catalog): add a store behind `ModuleRepository`; screens do not change.

## Rules

- No comments, no TODO stubs, no emojis. Icons are `Icons.Rounded.*` only.
- Cards: 12.dp padding, 14.dp corners, 0.dp elevation, 1.dp `outlineVariant` border at 0.3 alpha.
- One accent: primary color for Install, Update and selected chips.
- Inter for prose, JetBrains Mono for versions, ids, counts and logs.
- All UI strings live in `res/values/strings.xml`.
