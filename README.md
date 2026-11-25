# SwipeSort (Android)

A file manager built around one idea: **swipe left to delete, swipe right to sort into a folder.**
SwipeSort shows every photo, GIF, video, song and document on the phone, and lets you clear it out
or organise it one swipe at a time.

Kotlin · Jetpack Compose (Material 3) · Paging 3 · Coil · MediaStore · min SDK 26 · target SDK 35

---

## Architecture

```
┌──────────────────────────── UI (Compose) ─────────────────────────────┐
│ MainActivity ─ Root: access check ─► PermissionScreen                 │
│               └► MainTabs (bottom nav)                                │
│                   ├─ Files   → BrowseScreen      (paged list/grid)     │
│                   └─ Folders → FoldersTab        (list → folder/trash) │
│ components/  SwipeToSort · FileRow/FileCell · FolderPickerSheet ·      │
│              TransferBanner · dialogs                                  │
└───────────────▲───────────────────────────────────────▲───────────────┘
                │ StateFlow / PagingData / UiEvent      │
┌───────────────┴──── ViewModels ───────────────────────┴───────────────┐
│ BrowseViewModel   filter/search/sort → Pager, optimistic hide, undo   │
│ FoldersViewModel  folders CRUD, folder contents, Recently deleted     │
└───────────────▲───────────────────────────────────────────────────────┘
                │ suspend functions + StateFlows
┌───────────────┴──── Data ─────────────────────────────────────────────┐
│ FileRepository    MediaStore queries, folders, move/copy, trash, sync │
│ FilesPagingSource LIMIT/OFFSET pages over MediaStore.Files            │
│ TrashStore        SwipeSort/.trash + index (undo / restore / purge)   │
│ FileOps           rename-or-copy with progress                        │
│ StorageAccess     All-files-access + runtime permission logic         │
│ Settings          SharedPreferences → StateFlows                      │
└───────────────▲───────────────────────────────────────────────────────┘
                │ pure Kotlin, unit-tested
┌───────────────┴──── Domain ───────────────────────────────────────────┐
│ FileItem, SortFolder, TrashEntry, FileKind, FileFilter, SortOrder     │
│ MediaQueryBuilder (SQL), Names (validation, unique names, sizes),     │
│ TrashIndexCodec                                                       │
└───────────────────────────────────────────────────────────────────────┘
```
Dependencies are wired by hand in `SwipeSortApp` (a small container). ViewModels get the
repository and settings through `viewModelFactory { initializer { … } }`.

### Where files live
| What | Location on the phone |
|---|---|
| Your folders | `Internal storage/SwipeSort/<Folder name>/`. These are real folders, so any app (Gallery, Files, WhatsApp…) can open or share what's in them. |
| Recently deleted | `Internal storage/SwipeSort/.trash/`. It has a `.nomedia` marker, so gallery apps ignore it. Items are kept 30 days. |

The **Files** tab leaves out everything already inside `SwipeSort/`, so it works like an inbox:
as you sort, it empties.

---

## Key behaviours

### All-files access flow (`StorageAccess`, `PermissionScreen`, `MainActivity`)
1. The welcome screen explains why access is needed before any system prompt appears.
2. **Allow access** first shows the normal pop-ups. On Android 13+ that's photos/videos/audio
   (`READ_MEDIA_*`); on older versions it's storage.
3. On **Android 11+**, it then opens *Settings › All files access* for SwipeSort
   (`MANAGE_EXTERNAL_STORAGE`). This switch has no result callback, so `MainActivity` re-checks
   `Environment.isExternalStorageManager()` on every `ON_RESUME`. The app opens as soon as the
   user flips it on.
4. If the user comes back without granting access, or denies with "Don't ask again", the screen
   shows a clear explanation, **Try again**, and **Open App info**.
5. **Android 10** uses `requestLegacyExternalStorage`. **Android 8–9** use
   `READ/WRITE_EXTERNAL_STORAGE`.

### Swipe gestures (`components/SwipeToSort.kt`)
- Built on Material 3 `SwipeToDismissBox`. A swipe counts past **35%** of the width; shorter swipes
  spring back. A coloured background with an icon shows which action you're about to trigger.
- **Swipe left** deletes. The item vanishes immediately (it's hidden from the paged list right
  away), the file moves to Recently deleted, and a snackbar offers **Undo**. With *Ask before
  deleting* turned on, the row snaps back and a dialog asks first.
- **Swipe right** opens **Add to folder**. The row snaps back and a bottom sheet lists your
  folders, with a box to **create a new folder on the spot**. The **Keep original** switch copies
  instead of moving. Once you pick a folder, the file leaves the list and a snackbar offers **Undo**.
- Inside a folder, the same gestures do **delete** and **move to another folder**. In Recently
  deleted they do **delete forever** (asks first) and **restore**.
- **Long-press** any file for the same actions plus Open and Share. This also serves accessibility.

### Large libraries
- The Files tab is a **Paging 3** stream over `MediaStore.Files`, loaded 60 rows at a time with
  LIMIT/OFFSET (query-args Bundle on Android 11+, a sort-order suffix on older versions). Duplicates
  caused by offset shifts are filtered out per page source.
- Thumbnails load through **Coil** at display size, with memory and disk caches. Videos show a
  frame. GIFs show a still with a "GIF" badge so a grid of them stays smooth.

### Moving & copying (`FileOps`)
- A move on the same storage is an instant rename.
- Copies, and moves across volumes (SD card ↔ phone), stream with progress. The progress banner
  appears for anything slower than 300 ms.
- Half-finished copies are cleaned up, the original's date is kept, and name clashes become
  `name (2).ext`.
- MediaStore is kept in sync. Android 11+ updates it automatically; older versions drop the stale
  entry and re-scan.

### Polish
- Undo for delete, move and copy.
- Search by file name.
- Filters: All / Images / GIFs / Videos / Audio / Documents.
- Sort by newest, oldest, name, or largest.
- List/grid toggle (remembered) and pull to refresh.
- Dark mode, plus Material You colours on Android 12+.
- A first-run swipe hint card.
- Tap to open with the system viewer or player (FileProvider).
- No internet permission.

---

## Build & run
1. Open the `SwipeSort` folder in **Android Studio** and let Gradle sync.
2. Run ▶ on a phone, or *Build › Build APK(s)*.
3. Run the unit tests (domain rules, trash and move/copy on real temp files) with `./gradlew test`.

> **Google Play note:** apps that request `MANAGE_EXTERNAL_STORAGE` must declare it in the Play
> Console. File managers are an allowed use. For personal sideloading, nothing extra is needed.

## Project structure
```
SwipeSort/
├─ build.gradle.kts, settings.gradle.kts, gradle.properties, gradle/wrapper/
└─ app/
   ├─ build.gradle.kts                     dependencies
   └─ src/
      ├─ main/
      │  ├─ AndroidManifest.xml            permissions, FileProvider
      │  ├─ res/                           icon (adaptive + themed), themes, file_paths
      │  └─ java/com/swipesort/
      │     ├─ SwipeSortApp.kt             DI container + Coil
      │     ├─ MainActivity.kt             access check, tabs
      │     ├─ domain/                     Model.kt, Rules.kt, TrashIndexCodec.kt
      │     ├─ data/                       FileRepository, FilesPagingSource, TrashStore,
      │     │                              FileOps, StorageAccess, Settings
      │     ├─ util/FileIntents.kt         open / share
      │     └─ ui/
      │        ├─ browse/                  BrowseViewModel, BrowseScreen
      │        ├─ folders/                 FoldersViewModel, FoldersScreens (list/detail/trash)
      │        ├─ components/              SwipeToSort, FileViews, Sheets
      │        ├─ permission/              PermissionScreen
      │        ├─ theme/                   Theme
      │        └─ UiEvent.kt, EventSnackbars.kt
      └─ test/java/com/swipesort/          DomainTest, TrashStoreTest
```
