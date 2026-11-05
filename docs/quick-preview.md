# Quick Preview

"Open with Artefact" from WhatsApp, Gmail, Telegram or a file manager shows the PDF in a compact
floating card over that app, in the spirit of Apple's Quick Look. Close it and the other app is
exactly where it was; Open (or pull the card up) hands the file to the full viewer.

## How it works

| Piece | Where |
|---|---|
| Activity, intent handling, file copy, Expand | `android/app/src/main/java/com/chethan616/clearpdf/preview/QuickPreviewActivity.kt` |
| The card, gestures, animations | `android/app/src/main/java/com/chethan616/clearpdf/preview/QuickPreview.kt` |
| Window theme | `Theme.Artefact.Preview` in `res/values/themes.xml` (and `values-night`) |
| Intent filter | `.preview.QuickPreviewActivity` in `AndroidManifest.xml` |

**A translucent activity, not an overlay.** The preview is an ordinary activity whose window is
translucent (`windowIsTranslucent`, transparent background, no system dim, no window animation).
Android keeps drawing the activity below a translucent one, so the caller stays visible. The
activity runs with `launchMode="standard"` and `taskAffinity=""`: launched normally it joins the
caller's task (WhatsApp's), and if the caller asks for a new task it gets a fresh one instead of
Artefact's main task. Either way Back or Close just finishes it and the caller is on top again.

**Compact on purpose.** The card takes 84 % of the height, inset 10 dp from the edges and the
navigation bar, with 34 dp corners. Above it the caller shows through a 32 % (light) or 50 %
(dark) dim, plus a soft blur on Android 12+ phones that support cross-window blur
(`FLAG_BLUR_BEHIND` with `blurBehindRadius`, animated with the card; the system turns it off in
battery saver, which only removes the blur). Tapping the dim closes the preview.

**Reuses the viewer's engine and renderer.** The card opens the file with the same Rust engine
(`ArtefactApplication.engine`) and draws pages with the viewer's `PageCanvas` and `PageImages`:
vertical scrolling, pinch, double tap and double-tap-drag zoom all work. Nothing is duplicated.

**URI permissions.** The caller grants a temporary read permission
(`FLAG_GRANT_READ_URI_PERMISSION`) that lasts for this visit only. The preview copies the file
straight away into the viewer's working-copy folder (`files/Documents/<hash>/`), the same place
the full viewer would put it, so:

- Expand passes the original `content://` URI on with the grant flag; if Android won't re-grant
  it, the viewer opens the copy the preview made (`EXTRA_LOCAL_COPY`).
- Recent can reopen the file later, from the copy, after the caller's grant has ended.

**Lifecycle.** The engine document is closed (`doc_close`, discarding nothing since previews never
change the file) when the activity finishes; page images are dropped on memory pressure.

**Transitions.** Opening: the card rises from the bottom on a spring as the dim and blur fade in.
Closing: it folds down without bounce, then the activity finishes with no system animation.
Expand: the card grows to fill the screen (margins and corners to zero), then the full viewer
fades in over it and the preview finishes.

**Gestures.** Drag the header down past 110 dp to close, up past 88 dp to expand.

**Settings.** Settings ▸ Reading ▸ Quick Preview turns it off: files then go straight to the full
viewer.

## What Android allows (and what it doesn't)

- An app can't draw over another app without the overlay permission (`SYSTEM_ALERT_WINDOW`), and
  Artefact doesn't ask for it. The translucent activity is the supported way to sit over the
  caller: Android itself keeps the caller visible underneath.
- The window has to cover the screen to catch touches outside the card (to close it). A window
  that only covers the card (`FLAG_NOT_TOUCH_MODAL`) would let touches reach the caller, but the
  caller is paused behind a resumed activity, so they would do nothing useful. The visible card is
  still compact; only the invisible window is full size.
- Glass can't refract another app's pixels (no app can read them). The card is solid; the
  liquid-glass header and bottom bar refract the PDF pages inside the card, and the caller gets
  the system's blur behind the window where supported.
- Artefact can't change how WhatsApp shows a file inside WhatsApp. Quick Preview starts only
  when WhatsApp (or any app) hands the file to another app and the user picks Artefact.
- No Picture-in-Picture: PiP is for video-style content and can't scroll a document.

## Testing

From a chat app:

1. Send yourself a PDF in WhatsApp (or Telegram, Gmail, Google Messages).
2. Tap the PDF. WhatsApp shows "Open with"; choose **Artefact** (tap "Always" to skip the chooser
   next time). In some WhatsApp versions use the ⋮ menu ▸ Open with.
3. The card rises over the chat. Scroll, zoom, use Share or More (Save to Downloads, Open with).
4. Tap Close, press Back or tap the dim: you are back in the chat at the same message.
5. Open it again and tap **Open** (or drag the header up): the full viewer opens the document;
   Back from it returns to the chat.

From a file manager: open any PDF and choose Artefact.

With adb (any PDF the media store knows):

```sh
adb shell content query --uri content://media/external/file --projection _id:_display_name --where "_display_name='Report.pdf'"
adb shell am start -a android.intent.action.VIEW -t application/pdf \
  -d content://media/external/file/<id> --grant-read-uri-permission \
  -n com.chethan616.clearpdf.debug/com.chethan616.clearpdf.preview.QuickPreviewActivity
```

Also check: a password-protected PDF (the card asks for the password), a damaged file (a sad
mascot and the reason), rotating the phone (the card keeps its state), dark mode, and Settings ▸
Quick Preview off (straight to the viewer).

## Later

The intent filter grows with each format the engine learns: DOCX, XLSX, PPTX, images and text
share the same card.
