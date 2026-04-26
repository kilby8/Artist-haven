package com.artisthaven.drawing.brush

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Factory that creates GPU-accelerated [RuntimeShader] instances for each brush type.
 *
 * Clean Architecture — Infrastructure layer.
 *
 * AGSL (Android Graphics Shading Language) shaders run entirely on the GPU,
 * making brush textures such as graphite grain or watercolor bleed zero-cost
 * on the CPU render thread.  [RuntimeShader] is available from API 33 (Android 13).
 *
 * Design decisions:
 *  - Each [BrushType] maps to a self-contained AGSL program string.
 *  - [BrushProvider] is stateless; callers own the returned [RuntimeShader] and
 *    are responsible for updating its uniforms each frame.
 *  - Uniform names are documented per shader so the caller knows what to set.
 *
 * Common uniforms across all shaders:
 *  | Name       | Type    | Description                                  |
 *  |------------|---------|----------------------------------------------|
 *  | center     | float2  | Stroke sample position in canvas px coords   |
 *  | radius     | float   | Brush tip radius in pixels                   |
 *  | pressure   | float   | Normalised stylus pressure 0.0 … 1.0         |
 *  | color      | float4  | RGBA brush color (premultiplied alpha)       |
 *  | time       | float   | Elapsed time in seconds (for animated fx)    |
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
object BrushProvider {

    /**
     * Supported brush types, each backed by a distinct AGSL shader program.
     */
    enum class BrushType {
        /** Hard-edged pencil / graphite with grain texture. */
        GRAPHITE,
        /** Soft semi-transparent watercolor wash with diffuse edges. */
        WATERCOLOR,
        /** Standard smooth anti-aliased round brush. */
        ROUND
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AGSL shader source strings
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Graphite brush AGSL program.
     *
     * Renders a circular dab with:
     *  - Pressure-dependent opacity (harder press → darker stroke).
     *  - Pseudo-random high-frequency grain simulating graphite tooth.
     *  - A slight directional smear toward the stroke direction via the [angle] uniform.
     *
     * Additional uniform:
     *  | angle | float | Stroke direction in radians (used for directional grain) |
     */
    private val GRAPHITE_AGSL = """
        uniform float2 center;
        uniform float  radius;
        uniform float  pressure;
        uniform float4 color;
        uniform float  angle;

        // Low-cost pseudo-random hash (Vlachos 2016).
        float hash(float2 p) {
            return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
        }

        half4 main(float2 coords) {
            float dist = distance(coords, center);

            // Elliptical smear: compress along the stroke normal to simulate tilt.
            float2 dir  = float2(cos(angle), sin(angle));
            float2 perp = float2(-dir.y, dir.x);
            float2 rel  = coords - center;
            float  ellD = length(float2(dot(rel, dir) / (radius * 1.4),
                                        dot(rel, perp) / radius));

            // Smooth mask with pressure-controlled coverage.
            float mask = smoothstep(1.0, 0.35, ellD);

            // Two-octave grain: coarse tooth + fine specular glint.
            float grain = hash(floor(coords * 4.0)) * 0.55
                        + hash(floor(coords * 12.0)) * 0.35
                        + 0.1;
            grain = clamp(grain, 0.0, 1.0);

            float alpha = mask * grain * mix(0.45, 0.95, pressure);
            return half4(color.rgb * alpha, alpha);
        }
    """.trimIndent()

    /**
     * Watercolor brush AGSL program.
     *
     * Renders a soft circular wash with:
     *  - Animated edge diffusion using sine-based distortion ([time] uniform).
     *  - Pressure controls opacity and bleed radius.
     *  - Two-ring pigment concentration gradient (dark centre, lighter bleed edge).
     *
     * Additional uniform:
     *  | time | float | Elapsed seconds — drives edge animation |
     */
    private val WATERCOLOR_AGSL = """
        uniform float2 center;
        uniform float  radius;
        uniform float  pressure;
        uniform float4 color;
        uniform float  time;

        float hash(float2 p) {
            return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
        }

        half4 main(float2 coords) {
            float2 rel  = coords - center;
            float  dist = length(rel);

            // Distort the boundary with low-frequency turbulence.
            float angle = atan(rel.y, rel.x);
            float warp  = sin(angle * 3.0 + time * 0.8) * 0.12
                        + sin(angle * 7.0 - time * 1.3) * 0.06;
            float effectiveR = radius * (1.0 + warp * pressure);

            float t = dist / effectiveR;          // 0 = center, 1 = edge

            // Pigment concentration: dense toward centre, bleeds outward.
            float pigment = smoothstep(1.0, 0.0, t) * 0.6
                          + smoothstep(0.5, 0.0, t) * 0.4;

            // Edge fringe — slightly darker ring at the drying boundary.
            float fringe = smoothstep(0.65, 0.75, t) * smoothstep(1.0, 0.75, t) * 0.3;

            float noiseVal = hash(floor(coords * 3.0)) * 0.25 + 0.75;
            float alpha = (pigment + fringe) * noiseVal * pressure * color.a;
            alpha = clamp(alpha, 0.0, 1.0);

            return half4(color.rgb * alpha, alpha);
        }
    """.trimIndent()

    /**
     * Standard round brush AGSL program.
     *
     * Smooth anti-aliased circle with pressure-driven size and opacity.
     * No additional uniforms beyond the common set.
     */
    private val ROUND_AGSL = """
        uniform float2 center;
        uniform float  radius;
        uniform float  pressure;
        uniform float4 color;

        half4 main(float2 coords) {
            float dist  = distance(coords, center);
            float mask  = smoothstep(radius, radius * 0.6, dist);
            float alpha = mask * pressure * color.a;
            return half4(color.rgb * alpha, alpha);
        }
    """.trimIndent()

    // ──────────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Creates a new [RuntimeShader] for the requested [brushType].
     *
     * The returned shader has **no** uniforms set.  The caller must set at minimum:
     * `center`, `radius`, `pressure`, and `color` before the shader is used.
     *
     * @param brushType Which GPU brush program to instantiate.
     * @return A ready-to-configure [RuntimeShader].
     */
    fun create(brushType: BrushType): RuntimeShader {
        val agsl = when (brushType) {
            BrushType.GRAPHITE   -> GRAPHITE_AGSL
            BrushType.WATERCOLOR -> WATERCOLOR_AGSL
            BrushType.ROUND      -> ROUND_AGSL
        }
        return RuntimeShader(agsl)
    }

    /**
     * Convenience wrapper: creates and configures common uniforms in one call.
     *
     * @param brushType  Brush program to use.
     * @param centerX    Stroke sample X in canvas pixels.
     * @param centerY    Stroke sample Y in canvas pixels.
     * @param radius     Brush tip radius in pixels.
     * @param pressure   Normalised pressure (0.0 … 1.0).
     * @param r          Red channel (0.0 … 1.0).
     * @param g          Green channel.
     * @param b          Blue channel.
     * @param a          Alpha channel.
     * @param time       Elapsed time in seconds (used by animated brushes).
     * @param angle      Stroke angle in radians (used by graphite tilt).
     * @return Fully configured [RuntimeShader] ready for use with a [android.graphics.Paint].
     */
    fun createConfigured(
        brushType: BrushType,
        centerX: Float,
        centerY: Float,
        radius: Float,
        pressure: Float,
        r: Float = 0f,
        g: Float = 0f,
        b: Float = 0f,
        a: Float = 1f,
        time: Float = 0f,
        angle: Float = 0f
    ): RuntimeShader {
        val shader = create(brushType)
        shader.setFloatUniform("center", centerX, centerY)
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("pressure", pressure.coerceIn(0f, 1f))
        shader.setFloatUniform("color", r, g, b, a)

        // Brush-specific optional uniforms — safe to set on all shaders; unused
        // uniforms in a shader program are silently ignored by the AGSL runtime.
        shader.setFloatUniform("time", time)
        shader.setFloatUniform("angle", angle)
        return shader
    }
}
