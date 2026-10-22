# GUI redesign – Nowind Companion

## Context
The app is a single-activity Compose app whose screens are swiped with a `HorizontalPager`
(Home / placeholder Settings / Debug log). Disk selection is hardcoded in `FTDIClient.prepareDisks()`,
DOS2 is hardcoded off, and `FTDIClient` is created inside `setContent`, so it is re-created on
recomposition and never stopped. Goal: a menu-driven, visually attractive app with a Nowind status
screen, a Browse screen for download.file-hunter.com, and persistent Settings.

## Decisions
- Navigation Compose, menu as start destination.
- Browse = parse the HTML directory index (jsoup).
- Download queue + local library; "Download & host" jumps the queue and loads the disk when done.
- DOS2 setting: on = DOS2 on MSX2 and newer, off = DOS1 (default off).

## Architecture (all under `apps/Nowindcompanion/app/src/main/java/nl/myquest/nowindcompanion/`)
1. **Lifecycle fix first.** Own `FTDIClient` in an app-scoped holder (`NowindApp : Application` or a
   `NowindServer` singleton) with `start()/stop()` and a `StateFlow<ConnectionState>`
   (Searching, DeviceFound, Hosting, Disconnected). Remove creation from `setContent`
   (`MainActivity.kt:235`). Reuse the unused `FTDIClient.HostState` (`FTDIClient.kt:68`).
2. **Settings** (`SettingsRepository`, DataStore Preferences): `enableDos2` (default false),
   `debugEnabled` (default true). Exposed as `Flow`, mirrored into `@Volatile` fields for FTDIClient's I/O thread.
3. **Debug switch.** New `Debug.log()` wrapper replacing live `println`/`Log.*` in `FTDIClient.kt`,
   `ReadOperation.kt`, `CommandQueue.kt:100`, `NowindViewModel.write()`; also gate `logRawBytes`.
   Disabled = no output and no `viewModel.messages` growth.
4. **DOS2.** In `GETDOSVERSION` (`FTDIClient.kt` ~454) send 2 only if `enableDos2` and msx id >= `MsxVersion.Two`, else 1.
5. **Disk library** (`DiskLibrary`): pulls disk handling out of FTDIClient – `downloadFile`, `load`,
   `prepareDisks`. Stores zips in `getExternalFilesDir`, exposes downloaded list, `host(file)` which loads the
   image and sets `diskChanged = true`. Download queue (coroutine worker, one at a time, progress `StateFlow`,
   cancel); `downloadAndHost(item)` enqueues at the front and hosts on completion. Close streams properly,
   validate size/`.dsk` entry. Remove the hardcoded Puyo/Aleste downloads.
6. **Browse backend** (`FileHunterClient`): fetch a folder URL, parse links with jsoup, return folders and
   `.zip`/`.dsk` files; start at `https://download.file-hunter.com/`; keep breadcrumb stack.

## Screens (Compose Material3, `ui/` package)
- **MenuScreen** – large cards with icon + title + subtitle: Nowind, Browse, Settings; connection chip in header;
  Nowind logo (`res/drawable/nowind.jpg`).
- **NowindStatusScreen** – rows: serial number (`DeviceInfo`/`FtDeviceInfoListNode.serialNumber`), Android version
  (`Build.VERSION.RELEASE`), app version (`PackageManager` versionName), SDK version (`Build.VERSION.SDK_INT`),
  FTDI driver version (`D2xxManager.getLibraryVersion()`), connection status (colour-coded `ConnectionState`);
  optional "Debug log" button to the existing `DebugScreen`.
- **BrowseScreen** – breadcrumb + list of folders/files; per file two buttons: "Queue" and "Download & host";
  queue sheet with progress; downloaded files marked; loading/error/empty states; runs network on `Dispatchers.IO`.
- **SettingsScreen** – two switches: "Enable DOS2", "Enable debug information", persisted.
- **Theme** – refresh `ui/theme/` (keep dynamic colour, define a Nowind brand palette fallback, dark mode,
  fix `window.statusBarColor` usage in `Theme.kt`), shared top bar with back navigation. Delete dead code in
  `MainActivity.kt` (`MainActivityExample`, `ColorBox`, `Greeting`, etc.).

## Build changes (`app/build.gradle.kts`, inline versions, no catalog)
Add: `androidx.navigation:navigation-compose`, `androidx.datastore:datastore-preferences`, `org.jsoup:jsoup`,
`androidx.compose.material:material-icons-extended`, explicit `kotlinx-coroutines-android`.
Manifest permissions already sufficient (INTERNET present).

## Implementation order (one commit each)
1. Lifecycle/ownership fix of FTDIClient + ConnectionState.
2. Settings repository + Debug wrapper + DOS2 hook.
3. Navigation scaffold, theme, MenuScreen.
4. NowindStatusScreen.
5. SettingsScreen.
6. FileHunterClient + DiskLibrary + download queue.
7. BrowseScreen.

## Verification
- `./gradlew assembleDebug` and `./gradlew test` (add unit tests: HTML listing parser with a saved sample page,
  download queue ordering incl. "download & host" priority, DOS2 reply logic).
- On a device/emulator: menu navigation and back; status values correct, connection status changes when the
  FTDI adapter is plugged/unplugged; Browse folders, queue two files, download & host one and confirm the MSX
  boots it; toggle DOS2 and verify GETDOSVERSION reply (log); disable debug and verify no println/log output and
  no messages added; settings survive app restart.

## Risks / open points
- download.file-hunter.com index HTML format must be checked on a real response before finalising the parser.
- Hosting a new disk while a transfer is in progress: swap `diskimage` between commands only.
- `compileSdk` 34 / `targetSdk` 33 are old; raising them is out of scope unless a dependency requires it.
