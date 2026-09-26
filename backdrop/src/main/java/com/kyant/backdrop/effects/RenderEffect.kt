/*
 * Adapted from AndroidLiquidGlass by kyant0.
 * Source: https://github.com/Kyant0/AndroidLiquidGlass
 * Licensed under the Apache License, Version 2.0.
 */

package com.kyant.backdrop.effects

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import com.kyant.backdrop.BackdropEffectScope
import org.intellij.lang.annotations.Language

fun BackdropEffectScope.effect(effect: RenderEffect) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

    val currentEffect = renderEffect
    renderEffect =
        if (currentEffect != null) {
            RenderEffect.createChainEffect(effect, currentEffect)
        } else {
            effect
        }
}

fun BackdropEffectScope.effect(effect: androidx.compose.ui.graphics.RenderEffect) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

    effect(effect.asAndroidRenderEffect())
}

/**
 * Applies an AGSL [shaderString] as a RenderEffect, chained after the effects added so far.
 * [uniformShaderName] is the `uniform shader` that receives the current (already-effected) content;
 * [block] sets the other uniforms. No-op below Android 13 (TIRAMISU). Shaders are cached by [key].
 */
fun BackdropEffectScope.runtimeShaderEffect(
    key: String,
    @Language("AGSL") shaderString: String,
    uniformShaderName: String,
    block: RuntimeShader.() -> Unit
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val shader = obtainRuntimeShader(key, shaderString).apply(block)
    effect(RenderEffect.createRuntimeShaderEffect(shader, uniformShaderName))
}
