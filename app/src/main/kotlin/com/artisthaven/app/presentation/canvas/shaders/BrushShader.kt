package com.artisthaven.app.presentation.canvas.shaders

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * AGSL (Android Graphics Shading Language) shader definitions for brush textures.
 * Available on Android 13+ (API 33).
 *
 * These shaders create organic, texture-rich brush effects similar to professional
 * drawing applications. They run on the GPU via the Skia graphics engine.
 */
object BrushShaders {

    /**
     * Pencil texture shader: creates a grainy, rough texture
     * simulating graphite particles on paper.
     */
    val PENCIL_AGSL = """
        uniform float2 resolution;
        uniform float pressure;
        uniform float grain;

        float rand(float2 co) {
            return fract(sin(dot(co, float2(12.9898, 78.233))) * 43758.5453);
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;
            float dist = length(uv - 0.5) * 2.0;

            float alpha = 1.0 - smoothstep(0.0, 1.0, dist);

            float noise = rand(fragCoord * 0.5);
            float grainEffect = mix(1.0, noise, grain);

            alpha *= pressure * grainEffect;

            return half4(0.0, 0.0, 0.0, alpha);
        }
    """.trimIndent()

    /**
     * Watercolor shader: creates soft, diffuse edges with color spreading.
     */
    val WATERCOLOR_AGSL = """
        uniform float2 resolution;
        uniform float pressure;
        uniform float wetness;

        float rand(float2 co) {
            return fract(sin(dot(co, float2(12.9898, 78.233))) * 43758.5453);
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;
            float dist = length(uv - 0.5) * 2.0;

            float edge = 1.0 - smoothstep(0.6, 1.0 + wetness * 0.4, dist);
            float core = 1.0 - smoothstep(0.0, 0.7, dist);

            float noise = rand(uv * 8.0) * 0.3;
            float alpha = (edge + noise * wetness) * pressure;
            alpha = clamp(alpha, 0.0, 1.0) * core;

            return half4(0.0, 0.0, 0.0, alpha);
        }
    """.trimIndent()

    /**
     * Charcoal shader: creates smudged, directional texture.
     */
    val CHARCOAL_AGSL = """
        uniform float2 resolution;
        uniform float pressure;
        uniform float roughness;

        float rand(float2 co) {
            return fract(sin(dot(co, float2(12.9898, 78.233))) * 43758.5453);
        }

        float fbm(float2 p) {
            float v = 0.0;
            float a = 0.5;
            for (int i = 0; i < 4; i++) {
                v += a * rand(p);
                p *= 2.0;
                a *= 0.5;
            }
            return v;
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;
            float2 centered = uv - 0.5;

            float dist = length(float2(centered.x * 0.7, centered.y)) * 2.0;

            float alpha = 1.0 - smoothstep(0.0, 1.0, dist);

            float texture = fbm(fragCoord * roughness * 0.1);
            alpha *= texture * pressure;

            return half4(0.0, 0.0, 0.0, alpha);
        }
    """.trimIndent()
}

/**
 * Factory for creating RuntimeShader instances for brush textures.
 * Only available on Android 13+ (API 33).
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class BrushShaderFactory {

    fun createPencilShader(): RuntimeShader =
        RuntimeShader(BrushShaders.PENCIL_AGSL)

    fun createWatercolorShader(): RuntimeShader =
        RuntimeShader(BrushShaders.WATERCOLOR_AGSL)

    fun createCharcoalShader(): RuntimeShader =
        RuntimeShader(BrushShaders.CHARCOAL_AGSL)

    fun updateShaderUniforms(
        shader: RuntimeShader,
        width: Float,
        height: Float,
        pressure: Float,
        textureParam: Float,
    ) {
        shader.setFloatUniform("resolution", width, height)
        shader.setFloatUniform("pressure", pressure)
        try {
            shader.setFloatUniform("grain", textureParam)
        } catch (_: Exception) {}
        try {
            shader.setFloatUniform("wetness", textureParam)
        } catch (_: Exception) {}
        try {
            shader.setFloatUniform("roughness", textureParam)
        } catch (_: Exception) {}
    }
}
