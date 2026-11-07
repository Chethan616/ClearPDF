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

/**
 * Every glass node asks for the same few shaders, and a [RuntimeShader] compiles its AGSL on the
 * calling (UI) thread, so they are built once for the whole process instead of once per node: a
 * fan of chips, or a page of cards, used to compile two shaders each, the prismatic lens being the
 * dearest. Sharing is safe because an effect or paint snapshots the uniforms the moment it is made,
 * and every node sets its uniforms and makes its effect in one go on the UI thread.
 */
private val sharedRuntimeShaders = HashMap<String, RuntimeShader>()

internal class RuntimeShaderCacheImpl : RuntimeShaderCache {

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun obtainRuntimeShader(key: String, string: String): RuntimeShader {
        return sharedRuntimeShaders.getOrPut(key) { RuntimeShader(string) }
    }
}

/**
 * Compiles the glass shaders ahead of the first glass that needs them. Call [next] from an idle
 * handler until it returns false: one shader per call, so no single call holds the UI thread long.
 */
object GlassShaderWarmup {
    private var step = 0

    @Suppress("NewApi")
    fun next(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val cache = RuntimeShaderCacheImpl()
        when (step++) {
            0 -> cache.obtainRuntimeShader("Refraction", RoundedRectRefractionShaderString)
            1 -> cache.obtainRuntimeShader("RefractionWithDispersion", RoundedRectRefractionWithDispersionShaderString)
            2 -> cache.obtainRuntimeShader("Default", DefaultHighlightShaderString)
            3 -> cache.obtainRuntimeShader("Ambient", AmbientHighlightShaderString)
            else -> return false
        }
        return step < 4
    }
}
