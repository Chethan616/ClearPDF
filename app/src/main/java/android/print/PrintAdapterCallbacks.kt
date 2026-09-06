package android.print

/**
 * Openable subclasses of the print framework's two result callbacks.
 *
 * `PrintDocumentAdapter.LayoutResultCallback` and `WriteResultCallback` are public abstract classes
 * with *package-private constructors* — the framework hands you instances, and the public API gives
 * you no way to construct one. That is fine when the system print UI is driving the adapter, but it
 * blocks the one thing this app needs: driving a `PrintDocumentAdapter` directly to turn an
 * offscreen WebView into a PDF file, with no print dialog and no user interaction.
 *
 * Declaring these two here — in `android.print`, inside the app's own dex — puts them in the same
 * *package* as the constructors they call, which is what the access check compares. Everything else
 * about them is ordinary public API.
 *
 * This is the one genuinely load-bearing assumption in the .docx pipeline, so its single caller
 * ([com.chethan616.clearpdf.utils.DocxWebRenderer]) treats any `Throwable` from touching these — a
 * verification or access error included — as "this route is unavailable on this device" and falls
 * back to the hand-written converter. A device where the trick fails renders .docx exactly as it
 * did before; it never fails visibly.
 */
abstract class OpenLayoutResultCallback : PrintDocumentAdapter.LayoutResultCallback()

/** @see OpenLayoutResultCallback */
abstract class OpenWriteResultCallback : PrintDocumentAdapter.WriteResultCallback()
