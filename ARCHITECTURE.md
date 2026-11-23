# Architecture

Hercules 1.0.0 is a single-activity Android app built with Kotlin, Jetpack Compose, and Material 3. It targets Android 8.0 (API 26) and uses the checked-in Gradle wrapper, bundled Inter and JetBrains Mono fonts, and a bundled catalog for offline fallback.

## Project structure

```text
app/src/main/java/app/hercules/store/
  HerculesApp.kt                 Application and AppContainer
  MainActivity.kt                Edge-to-edge Compose activity
  data/
    ModuleRepository.kt           Catalog, downloads, and script inspection
    InstalledStore.kt             Installed modules and package state
    MockModules.kt                Last-resort sample data
    ScriptAnalyzer.kt             Installer-script checks
    GithubUrl.kt                  GitHub repository URL parser
    model/ModuleItem.kt           Catalog, install, and report models
    remote/ModuleApi.kt           Catalog, GitHub, and download requests
  root/RootExecutor.kt            su commands and root-manager integration
  ui/
    HerculesRoot.kt               Shared state, three tabs, settings, and sheet hosts
    ViewModelFactory.kt           AppContainer-backed view model factory
    catalog/                      Featured, Browse, filters, rows, and domain icons
    components/                   Glass surfaces, buttons, badges, and insets
    details/                      Module details and script reports
    install/                      Download/install state, console, and local ZIP staging
    installed/                    Installed-module list and update comparison
    status/                       Root manager status pill
    theme/                        Material color schemes and typography
app/src/main/res/font/            Inter and JetBrains Mono font files
app/src/main/resources/modules.json  Bundled catalog fallback
gradle/wrapper/                   Checked-in Gradle wrapper
```

## Navigation and presentation

`HerculesRoot` owns the shared `CatalogViewModel`, so Featured category selections and Browse filters use the same state. The three tabs are Featured, Browse, and Installed. Featured combines Spotlight banners, a 2-by-2 category grid, and recently updated packages. Browse uses flat 73 dp module rows with 0.5 dp dividers inset to the title. `ModuleDomainIcon` chooses a rounded Material icon from package metadata. GET and UPDATE actions use the shared `#14B8A6` accent.

`GlassSurface` applies Haze backdrop effects to the floating tab bar, status and settings controls, and sheets. `SettingsSheet` updates the Liquid Glass toggle immediately and persists it in `SharedPreferences` from `HerculesRoot`. There is no separate `SettingsStore` class in this release. When the toggle is off, glass surfaces use opaque Material surfaces.

## Data and installation flow

```text
Compose screen -> ViewModel -> ModuleRepository -> ModuleApi
                           -> InstalledStore -> RootExecutor -> su
```

`ModuleRepository` fetches the catalog from `BuildConfig.CATALOG_URL` and falls back to the bundled resource if the request fails. `ModuleApi` handles catalog and GitHub requests and streams package downloads. `CatalogViewModel` combines repository state with search, root filters, sorting, selected categories, and installed status.

Selecting a package opens `ModuleDetailsSheet`. ZIP script inspection reads embedded script entries or downloads the ZIP, then passes the results to `ScriptAnalyzer`. Script inspection is not a prerequisite for installation.

`InstallViewModel` downloads packages into the app cache and streams installer output to `ConsoleSheet`. ZIP modules are installed through the detected Magisk, KernelSU, or APatch command. APK modules use `pm install -r` and can then be enabled in an Xposed manager. Installed state comes from root-manager module metadata and the package manager; version codes drive update status.

## Design and code conventions

Keep screen layout in Compose and move catalog, install, and installed-state work into the existing view models and repositories. Reuse Material 3 color and type roles; use Inter for interface text and JetBrains Mono for identifiers, versions, and console output. Use `Icons.Rounded.*` for vector icons.

Package lists stay flat: the module tile, title, one-line summary, and action capsule share a row, with a hairline divider aligned to the title. Keep action capsules on the shared teal accent and installed states visually quiet. The bundled catalog remains available as the offline fallback, and unknown catalog fields are ignored by the serializer.

When adding a filter, update `RootFilter`, `CatalogViewModel` filtering, and the corresponding string resource. Root-manager support belongs in `RootManager` and `RootExecutor`; script checks belong in `ScriptAnalyzer` and the detail-sheet finding labels.

## Build

Use JDK 17 and Android SDK platform 35:

```sh
./gradlew assembleDebug
```

The Windows wrapper is available as `gradlew.bat`.
