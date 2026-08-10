package com.kyant.pdfcore.security

import android.content.Context
import android.net.Uri
import com.kyant.pdfcore.internal.PdfBox
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import java.io.File

/**
 * Password operations for PDFs. The service keeps the original URI untouched and performs
 * all parsing/writing on the caller's worker thread.
 */
object PdfSecurityService {

    class PasswordRequiredException : IllegalStateException("PDF password required")

    fun isPasswordProtected(context: Context, uri: Uri): Boolean {
        PdfBox.ensureInitialized(context)
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                PDDocument.load(stream).use { document -> document.isEncrypted }
            } ?: false
        } catch (error: Exception) {
            if (isPasswordError(error)) true else throw error
        }
    }

    /** Creates an unlocked temporary PDF for the viewer without modifying the source. */
    fun decryptToCache(context: Context, uri: Uri, password: String): File {
        PdfBox.ensureInitialized(context)
        val outputDirectory = File(context.cacheDir, "decrypted_pdfs").apply { mkdirs() }
        val output = File(outputDirectory, "unlocked_${System.currentTimeMillis()}.pdf")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                PDDocument.load(input, password).use { document ->
                    document.setAllSecurityToBeRemoved(true)
                    output.outputStream().use { document.save(it) }
                }
            } ?: throw IllegalStateException("Unable to read PDF")
        } catch (error: Exception) {
            output.delete()
            if (isPasswordError(error)) throw PasswordRequiredException()
            throw error
        }
        return output
    }

    /** Writes an unlocked copy to a user-selected destination URI. */
    fun decryptToUri(context: Context, sourceUri: Uri, destinationUri: Uri, password: String) {
        PdfBox.ensureInitialized(context)
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                PDDocument.load(input, password).use { document ->
                    document.setAllSecurityToBeRemoved(true)
                    context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                        document.save(output)
                    } ?: throw IllegalStateException("Unable to write PDF")
                }
            } ?: throw IllegalStateException("Unable to read PDF")
        } catch (error: Exception) {
            if (isPasswordError(error)) throw PasswordRequiredException()
            throw error
        }
    }

    /** Writes a password-protected copy to a user-selected destination URI. */
    fun encryptToUri(context: Context, sourceUri: Uri, destinationUri: Uri, password: String) {
        require(password.isNotBlank()) { "PDF password required" }
        PdfBox.ensureInitialized(context)
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                PDDocument.load(input).use { document ->
                    val permissions = AccessPermission().apply {
                        setCanPrint(true)
                        setCanModify(false)
                        setCanExtractContent(true)
                    }
                    val policy = StandardProtectionPolicy(password, password, permissions).apply {
                        encryptionKeyLength = 128
                    }
                    document.protect(policy)
                    context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                        document.save(output)
                    } ?: throw IllegalStateException("Unable to write PDF")
                }
            } ?: throw IllegalStateException("Unable to read PDF")
        } catch (error: Exception) {
            if (isPasswordError(error)) throw PasswordRequiredException()
            throw error
        }
    }

    private fun isPasswordError(error: Throwable): Boolean {
        return generateSequence(error) { it.cause }
            .any { it::class.java.simpleName.contains("InvalidPassword", ignoreCase = true) }
    }
}
