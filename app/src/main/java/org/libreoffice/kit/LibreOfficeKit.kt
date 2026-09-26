/*
 * Kotlin binding for the LibreOfficeKit JNI layer (liblo-native-code.so).
 *
 * The package, class, method and field names here are an interface contract with native code:
 * the library exports `Java_org_libreoffice_kit_<Class>_<method>` symbols and looks up fields /
 * callbacks by name, mirroring LibreOffice's own Android binding (MPL-2.0, android/source in
 * LibreOffice core). Do not rename anything in this package; proguard-rules.pro keeps it intact.
 *
 * This file is original ClearPDF code (MIT); only the names are dictated by the native side.
 */
package org.libreoffice.kit

import android.content.res.AssetManager
import java.nio.ByteBuffer

object LibreOfficeKit {

    /** Native-side initialisation of LibreOfficeKit rooted at [dataDir] (its `$APP_DATA_DIR`). */
    @JvmStatic
    external fun initializeNative(dataDir: String, cacheDir: String, apkFile: String, assets: AssetManager): Boolean

    /** Pointer to the process-wide `LibreOfficeKit` instance, wrapped as a direct buffer. */
    @JvmStatic
    external fun getLibreOfficeKitHandle(): ByteBuffer?

    @JvmStatic
    external fun putenv(entry: String)

    @JvmStatic
    external fun redirectStdio(enabled: Boolean)
}
