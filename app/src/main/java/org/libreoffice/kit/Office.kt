/*
 * Binding for a LibreOfficeKit office instance. Names are a JNI contract — see LibreOfficeKit.kt.
 * Original ClearPDF code (MIT).
 */
package org.libreoffice.kit

import java.nio.ByteBuffer

class Office(
    /** Read by native code by name ("handle", Ljava/nio/ByteBuffer;). */
    @JvmField val handle: ByteBuffer
) {
    /** Receives LibreOfficeKit callbacks (for example document-password requests). */
    @Volatile
    var callback: ((type: Int, payload: String?) -> Unit)? = null

    init {
        bindMessageCallback()
    }

    private external fun bindMessageCallback()

    external fun getError(): String?

    private external fun documentLoadNative(url: String): ByteBuffer?

    fun documentLoad(url: String): Document? = documentLoadNative(url)?.let(::Document)

    /** Answers a [LOK_CALLBACK_DOCUMENT_PASSWORD] request; a null password aborts the load. */
    external fun setDocumentPassword(url: String, password: String?)

    external fun setOptionalFeatures(features: Long)

    external fun destroy()

    external fun destroyAndExit()

    /** Invoked from native code by name ("messageRetrievedLOKit", (ILjava/lang/String;)V). */
    @Suppress("unused")
    private fun messageRetrievedLOKit(type: Int, payload: String?) {
        callback?.invoke(type, payload)
    }

    companion object {
        /** LibreOfficeKitOptionalFeatures: LOK_FEATURE_DOCUMENT_PASSWORD. */
        const val FEATURE_DOCUMENT_PASSWORD = 1L shl 0

        /** LibreOfficeKitCallbackType: LOK_CALLBACK_DOCUMENT_PASSWORD. */
        const val LOK_CALLBACK_DOCUMENT_PASSWORD = 20
    }
}
