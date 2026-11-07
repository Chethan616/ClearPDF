# Draft for upstream: WordCraft and DeckCraft on Android

Status: **draft, not filed**. Artefact carries these patches in `engine/vendor/` (see
`engine/vendor/README.md`); this page is what would go to storytold/wordcraft and
storytold/deckcraft as issues or pull requests, when the owner decides to send them.

Base revisions: wordcraft `729cd0a5fc953bbedbfdec4f6af18ad6d1780b3b`, deckcraft
`159ed07eb3f0522a5dc97d7cf1858242a6638659`.

## 1. Fonts: Android's font folders (wordcraft-fonts, deckcraft-fonts)

**Problem.** `system_font_dirs()` sends every Unix that isn't macOS to the Linux folders
(`/usr/share/fonts`, `~/.fonts`, XDG, Flatpak's `/run/host/...`). None of them exists on Android,
where the system's fonts are in `/system/fonts` (and on some phones also `/product/fonts`). An
Android build of the engine therefore finds no system fonts: every document falls back to the
bundled faces, and scripts they don't cover (CJK, Devanagari, Thai, emoji…) draw as boxes, though
the phone has Noto faces for all of them. The font database is built once from this function
(`FontDb::global`), so an embedder can't point it elsewhere.

**Fix.** An `android` branch before the Linux one. No new dependency; other platforms are
unchanged. The Flatpak test moves out of Android builds, and an Android test checks the folders.

### wordcraft (`crates/fonts`)

```diff
diff --git a/crates/fonts/src/fontdb/names.rs b/crates/fonts/src/fontdb/names.rs
index 0b01041..f085eda 100644
--- a/crates/fonts/src/fontdb/names.rs
+++ b/crates/fonts/src/fontdb/names.rs
@@ -228,6 +228,11 @@ pub fn system_font_dirs() -> Vec<std::path::PathBuf> {
                 }
             }
         }
+    } else if cfg!(target_os = "android") {
+        // Android's font folders (the system's, and the product partition's that
+        // some phones add fonts to). The Linux folders below don't exist there, and an app has no
+        // home folder of fonts.
+        dirs.extend(["/system/fonts", "/product/fonts", "/system/product/fonts"].map(Into::into));
     } else if cfg!(windows) {
         let root = std::env::var_os("WINDIR").map(std::path::PathBuf::from).unwrap_or_else(|| "C:\\Windows".into());
         dirs.push(root.join("Fonts"));
diff --git a/crates/fonts/src/fontdb/tests.rs b/crates/fonts/src/fontdb/tests.rs
index 4734f67..79ce5b8 100644
--- a/crates/fonts/src/fontdb/tests.rs
+++ b/crates/fonts/src/fontdb/tests.rs
@@ -41,7 +41,7 @@ fn kai_contours_include_complete_character_body() {
 use super::*;
 
 #[test]
-#[cfg(all(unix, not(target_os = "macos"), not(target_arch = "wasm32")))]
+#[cfg(all(unix, not(target_os = "macos"), not(target_os = "android"), not(target_arch = "wasm32")))]
 fn system_font_dirs_include_the_flatpak_host_folders() {
     // Inside a Flatpak, /usr/share/fonts is the runtime's: the host's fonts are under /run/host (#1937).
     let dirs = system_font_dirs();
@@ -50,6 +50,15 @@ fn system_font_dirs_include_the_flatpak_host_folders() {
     }
 }
 
+/// On a phone the fonts are in the system's folders, not the Linux ones.
+#[test]
+#[cfg(target_os = "android")]
+fn system_font_dirs_on_android_are_the_system_folders() {
+    let dirs = system_font_dirs();
+    assert!(dirs.contains(&std::path::PathBuf::from("/system/fonts")), "{dirs:?}");
+    assert!(!dirs.contains(&std::path::PathBuf::from("/usr/share/fonts")), "{dirs:?}");
+}
+
 #[test]
 fn a_capped_outline_is_refused_while_it_is_drawn() {
     // #3716 review: a colour glyph's clip outline stops growing at the payload left.
```

### deckcraft (`crates/fonts`)

```diff
diff --git a/crates/fonts/src/fontdb.rs b/crates/fonts/src/fontdb.rs
index 19f1904..c8534be 100644
--- a/crates/fonts/src/fontdb.rs
+++ b/crates/fonts/src/fontdb.rs
@@ -629,6 +629,10 @@ pub fn system_font_dirs() -> Vec<std::path::PathBuf> {
             // DeckCraft never ships or needs them.
             dirs.push(l.join("Microsoft\\FontCache\\4\\CloudFonts"));
         }
+    } else if cfg!(target_os = "android") {
+        // Android's font folders (the system's, and the product partition's that some phones add
+        // fonts to). The Linux folders, XDG and fontconfig below don't exist there.
+        dirs.extend(["/system/fonts", "/product/fonts", "/system/product/fonts"].map(Into::into));
     } else {
         dirs.extend(["/usr/share/fonts", "/usr/local/share/fonts"].map(Into::into));
         if let Some(h) = &home {
```

## 2. Engine without the font store (wordcraft-engine)

**Problem.** On every native target `wordcraft-engine` depends on `craft-font-store` and
`craft-font-catalog`, which download Language packs. That brings an HTTP client (ureq, rustls and
ring, a C/assembly build) into every embedder, including ones that ship their own fonts and must not
go online (an offline phone app with no network permission). The web build already lives without the
store (`cfg(target_arch = "wasm32")`).

**Fix.** A `font-store` feature, on by default so the desktop app, the CLI and MCP behave as
today. With it off the engine compiles the same no-store paths as the web build: `packs::Cancel`
is a plain flag and `use_font_store`/`use_font_store_in` are not there (only the app and the CLI
call them). Checked with `cargo tree --target aarch64-linux-android -e normal`: with
`default-features = false` no `ureq`, `rustls` or `ring` remains.

```diff
diff --git a/crates/engine/Cargo.toml b/crates/engine/Cargo.toml
index 76b11c6..cda82c9 100644
--- a/crates/engine/Cargo.toml
+++ b/crates/engine/Cargo.toml
@@ -9,6 +9,10 @@ description = "WordCraft engine: session, selection, undo, the command registry
 workspace = true
 
 [features]
+default = ["font-store"]
+# Language packs downloaded by the suite's font store. Off: no packs, and no network code (an
+# embedder that ships its own fonts, such as a phone app).
+font-store = ["dep:craft-font-store", "dep:craft-font-catalog"]
 # Front-end producer tests need to simulate a globally exhausted IME revision allocator. It only
 # ever makes receipt classification fail closed. Never enable this for a production build; the
 # UI's dev-dependencies enable it for its tests.
@@ -41,9 +45,10 @@ js-sys = "0.3"
 
 [target.'cfg(not(target_arch = "wasm32"))'.dependencies]
 getrandom = { workspace = true }
-# Language packs (CP4.3): downloaded, verified and unpacked by the suite's font store.
-craft-font-store = { workspace = true }
-craft-font-catalog = { workspace = true }
+# Language packs (CP4.3): downloaded, verified and unpacked by the suite's font store
+# (the `font-store` feature).
+craft-font-store = { workspace = true, optional = true }
+craft-font-catalog = { workspace = true, optional = true }
 # The local time zone's offset for Insert ▸ Date & Time and date fields (#1707); the web asks the browser's Date.
 chrono = { workspace = true, features = ["clock"] }
 
diff --git a/crates/engine/src/packs.rs b/crates/engine/src/packs.rs
index 5619114..448302e 100644
--- a/crates/engine/src/packs.rs
+++ b/crates/engine/src/packs.rs
@@ -21,7 +21,8 @@ use serde::Serialize;
 
 use crate::{CmdError, Session};
 
-#[cfg(not(target_arch = "wasm32"))]
+// The font store only with the `font-store` feature (no network code without it).
+#[cfg(all(not(target_arch = "wasm32"), feature = "font-store"))]
 mod store;
 
 /// Longest locale tag taken, in bytes (BCP 47 tags in practice stay under 20).
@@ -71,25 +72,28 @@ pub struct Installed {
 /// Stops a download. On the desktop it is the font store's own flag, which a download watches
 /// even while the server sends nothing.
 #[derive(Clone, Debug, Default)]
-pub struct Cancel(#[cfg(not(target_arch = "wasm32"))] pub(crate) craft_font_store::Cancel, #[cfg(target_arch = "wasm32")] Arc<AtomicBool>);
+pub struct Cancel(
+    #[cfg(all(not(target_arch = "wasm32"), feature = "font-store"))] pub(crate) craft_font_store::Cancel,
+    #[cfg(not(all(not(target_arch = "wasm32"), feature = "font-store")))] Arc<AtomicBool>,
+);
 
 impl Cancel {
     pub fn new() -> Self {
         Self::default()
     }
-    #[cfg(not(target_arch = "wasm32"))]
+    #[cfg(all(not(target_arch = "wasm32"), feature = "font-store"))]
     pub fn cancel(&self) {
         self.0.cancel();
     }
-    #[cfg(not(target_arch = "wasm32"))]
+    #[cfg(all(not(target_arch = "wasm32"), feature = "font-store"))]
     pub fn is_cancelled(&self) -> bool {
         self.0.is_cancelled()
     }
-    #[cfg(target_arch = "wasm32")]
+    #[cfg(not(all(not(target_arch = "wasm32"), feature = "font-store")))]
     pub fn cancel(&self) {
         self.0.store(true, Ordering::Relaxed);
     }
-    #[cfg(target_arch = "wasm32")]
+    #[cfg(not(all(not(target_arch = "wasm32"), feature = "font-store")))]
     pub fn is_cancelled(&self) -> bool {
         self.0.load(Ordering::Relaxed)
     }
@@ -155,7 +159,7 @@ pub fn set_backend(backend: Option<Arc<dyn Backend>>) {
 /// never can). The pack index is the pinned one, or `CRAFT_LANGUAGE_PACK_INDEX` (a URL or a path,
 /// for testing packs before they are published), which the font store reads itself. Returns
 /// whether there is a store folder.
-#[cfg(not(target_arch = "wasm32"))]
+#[cfg(all(not(target_arch = "wasm32"), feature = "font-store"))]
 pub fn use_font_store(user: bool) -> bool {
     match store::FontStoreBackend::new(user) {
         Some(b) => {
@@ -167,7 +171,7 @@ pub fn use_font_store(user: bool) -> bool {
 }
 
 /// [`use_font_store`] with the store in `dir` instead of the person's (`ui_shot`, tests).
-#[cfg(not(target_arch = "wasm32"))]
+#[cfg(all(not(target_arch = "wasm32"), feature = "font-store"))]
 pub fn use_font_store_in(dir: PathBuf, user: bool) {
     set_backend(Some(Arc::new(store::FontStoreBackend::in_dir(dir, user))));
 }
```
