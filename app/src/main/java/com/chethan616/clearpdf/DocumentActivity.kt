package com.chethan616.clearpdf

/**
 * A document opened from another app, in its own window: its own task and Recents entry (named
 * after the file), separate from the app's main window and from the app that opened it. Started by
 * [MainActivity] for every VIEW / SEND; leaving the document closes the window and returns to
 * whatever opened it (#29).
 */
class DocumentActivity : MainActivity() {
    override val isDocumentWindow: Boolean = true
}
