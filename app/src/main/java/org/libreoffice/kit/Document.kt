/*
 * Binding for a loaded LibreOfficeKit document. Names are a JNI contract — see LibreOfficeKit.kt.
 * Only the calls ClearPDF needs are declared; JNI resolves natives lazily, so omitting the rest
 * (tile painting, input events, UNO commands) is harmless. Original ClearPDF code (MIT).
 */
package org.libreoffice.kit

import java.nio.ByteBuffer

class Document(
    /** Read by native code by name ("handle", Ljava/nio/ByteBuffer;). */
    @JvmField val handle: ByteBuffer
) {
    init {
        bindMessageCallback()
    }

    private external fun bindMessageCallback()

    external fun destroy()

    external fun getParts(): Int

    external fun getDocumentTypeNative(): Int

    /** Saves to [url] (a file:// URL) in [format] (e.g. "pdf"), with optional filter options. */
    external fun saveAs(url: String, format: String, options: String?)

    /** Invoked from native code by name ("messageRetrieved", (ILjava/lang/String;)V). */
    @Suppress("unused", "UNUSED_PARAMETER")
    private fun messageRetrieved(type: Int, payload: String?) = Unit
}
