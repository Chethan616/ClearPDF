/*
 * Adapted from AndroidLiquidGlass by kyant0.
 * Source: https://github.com/Kyant0/AndroidLiquidGlass
 * Licensed under the Apache License, Version 2.0.
 */

package com.kyant.backdrop

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import org.intellij.lang.annotations.Language

sealed interface RuntimeShaderCache {

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun obtainRuntimeShader(
        key: String,
        @Language("AGSL") string: String
    ): RuntimeShader
}

internal class RuntimeShaderCacheImpl : RuntimeShaderCache {

    private val runtimeShaders = mutableMapOf<String, RuntimeShader>()

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun obtainRuntimeShader(key: String, string: String): RuntimeShader {
        return runtimeShaders.getOrPut(key) { RuntimeShader(string) }
    }

    fun clear() {
        runtimeShaders.clear()
    }
}
