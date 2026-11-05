# Handoff

Read this first after a `/clear`. The roadmap is `PLAN.md`.

## Where things are

- Repo: `D:\clearPDF_V2`, branch `clearpdf-v2`; remote `upstream` = artcraft-android/pdfcraft-android.
- Android project: `apps/pdfcraft-android/android` (to be moved to `android/`).
- Build (phone is arm64): `cd apps/pdfcraft-android/android; ./gradlew installDebug -Pclearpdf.abis=arm64-v8a`
  Debug id `com.chethan616.clearpdf.debug`. Release: `assembleRelease -Pclearpdf.abis=arm64-v8a`
  (signed with the local Play upload key from `key.properties`).
- Phone: `adb -s 5bdd59db` (OnePlus CPH2487, Android 16, 1240×2772 @560dpi). In Git Bash set
  `MSYS_NO_PATHCONV=1` before `adb shell …/sdcard…`. `pm clear` is blocked; reset with
  `adb shell "run-as com.chethan616.clearpdf.debug rm -rf shared_prefs files/notes"`.
- A sample PDF is on the phone: `/sdcard/Download/Quarterly-Report.pdf` (MediaStore id 1000144849).
  Quick Preview test: open Settings, then
  `adb shell am start -a android.intent.action.VIEW -t application/pdf -d content://media/external/file/1000144849 --grant-read-uri-permission -n com.chethan616.clearpdf.debug/com.chethan616.clearpdf.preview.QuickPreviewActivity`

## Code map (new UI)

| What | Where |
|---|---|
| Tokens, type, tones | `ui/theme/ClearTheme.kt` |
| Wallpaper, glass stack, overlay portal | `ui/glass/Backdrops.kt` |
| Glass controls, sheets, menus, loader | `ui/glass/*` (`Loader.kt` = M3 Expressive in glass) |
| Routing (welcome / desk / files / tools / settings / note / viewer) | `ui/ClearApp.kt` |
| Desk | `ui/home/HomeScreen.kt`, cards `ui/home/Cards.kt`, + and mic `ui/home/CreateDock.kt` |
| Notes store | `notes/Notes.kt` (JSON in `files/notes`, media in `files/notes/media`) |
| Note editor | `ui/notes/NoteEditor.kt` |
| Welcome | `ui/onboarding/Onboarding.kt` |
| Mascots | `ui/mascot/*` (copied from rewordium; never edit rewordium itself) |
| Quick Preview | `preview/QuickPreviewActivity.kt` + `preview/QuickPreview.kt` |
| Viewer | `ui/viewer/*` (still mostly the V2 base look) |

## Gotchas

- Apostrophes in `strings.xml` must be `\'`. Shell heredocs eat the backslash: use the Edit tool.
- `material3` is pinned to `1.5.0-beta02` in `gradle/libs.versions.toml` for `LoadingIndicator`.
- Glass can't sample other apps: Quick Preview's card is solid; the caller behind is dimmed and
  blurred with Android 12+ cross-window blur (`FLAG_BLUR_BEHIND`), when the phone allows it.
- Quick Preview copies the file into the viewer's working-copy folder first, so Expand still opens
  after the caller's temporary grant ends.

## Next up

See PLAN.md "Next". Immediate: viewer chrome restyle check, translations (incl. new pt + ru),
folder restructure, README, release APK, GitHub master branch + issue + discussion.
