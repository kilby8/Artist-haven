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

    // ─────────────────────────────────────────────────────────────────────────
    // EXTENDED SHADER LIBRARY (25+ shaders for specialty brushes)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Conte crayon texture: coarse, heavily textured strokes.
     */
    val CONTE_TEXTURE = """
        uniform float2 resolution;
        uniform float pressure;
        uniform float roughness;

        float rand(float2 co) {
            return fract(sin(dot(co, float2(12.9898, 78.233))) * 43758.5453);
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;
            float dist = length(uv - 0.5) * 2.0;
            
            float alpha = 1.0 - smoothstep(0.0, 1.0, dist);
            
            float noise1 = rand(fragCoord * 0.3);
            float noise2 = rand(fragCoord * 0.1);
            float texture = mix(noise1, noise2, 0.5);
            
            alpha *= texture * pressure * roughness;
            
            return half4(0.0, 0.0, 0.0, alpha);
        }
    """.trimIndent()

    /**
     * Tinted charcoal: medium grain with color blending.
     */
    val TINTED_CHARCOAL = """
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
            float grainNoise = rand(fragCoord * 0.4) * grain;
            
            alpha *= (1.0 - grainNoise * 0.5) * pressure;
            
            return half4(0.0, 0.0, 0.0, alpha);
        }
    """.trimIndent()

    /**
     * Powder scatter: loose particles that build with layering.
     */
    val POWDER_SCATTER = """
        uniform float2 resolution;
        uniform float pressure;
        uniform float scatter;

        float rand(float2 co) {
            return fract(sin(dot(co, float2(12.9898, 78.233))) * 43758.5453);
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;
            float dist = length(uv - 0.5) * 2.0;
            
            float alpha = 1.0 - smoothstep(0.0, 1.2, dist);
            
            float noise = rand(fragCoord * scatter);
            alpha *= noise * pressure * 0.6;
            
            return half4(0.0, 0.0, 0.0, alpha);
        }
    """.trimIndent()

    /**
     * Sketch streaks: directional texture for loose sketching.
     */
    val SKETCH_STREAKS = """
        uniform float2 resolution;
        uniform float pressure;
        uniform float streakiness;

        float rand(float2 co) {
            return fract(sin(dot(co, float2(12.9898, 78.233))) * 43758.5453);
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;
            float dist = length(uv - 0.5) * 2.0;
            
            float alpha = 1.0 - smoothstep(0.0, 1.0, dist);
            
            // Directional streaks
            float streak = rand(float2(uv.x * 10.0, uv.y));
            alpha *= mix(1.0, streak, streakiness) * pressure;
            
            return half4(0.0, 0.0, 0.0, alpha);
        }
    """.trimIndent()

    /**
     * Impasto relief: thick paint with visible texture.
     */
    val IMPASTO_RELIEF = """
        uniform float2 resolution;
        uniform float pressure;
        uniform float relief;

        float rand(float2 co) {
            return fract(sin(dot(co, float2(12.9898, 78.233))) * 43758.5453);
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;
            float dist = length(uv - 0.5) * 2.0;
            
            float alpha = 1.0 - smoothstep(-0.2, 1.0, dist);
            
            float texture = rand(fragCoord * 0.2) * relief;
            alpha += texture * 0.3;
            
            alpha *= pressure;
            
            return half4(0.0, 0.0, 0.0, clamp(alpha, 0.0, 1.0));
        }
    """.trimIndent()

    /**
     * Watercolor flow: transparent diffusion with color bleeding.
     */
    val WATERCOLOR_FLOW = """
        uniform float2 resolution;
        uniform float pressure;
        uniform float flow;

        float rand(float2 co) {
            return fract(sin(dot(co, float2(12.9898, 78.233))) * 43758.5453);
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;
            float dist = length(uv - 0.5) * 2.0;
            
            float alpha = 1.0 - smoothstep(0.4, 1.4, dist);
            
            float noise = rand(uv * 10.0);
            alpha = mix(alpha, noise, flow * 0.5) * pressure;
            
            return half4(0.0, 0.0, 0.0, alpha);
        }
    """.trimIndent()

    /**
     * Drybrush breaks: sparse coverage with broken strokes.
     */
    val DRYBRUSH_BREAKS = """
        uniform float2 resolution;
        uniform float pressure;
        uniform float dryness;

        float rand(float2 co) {
            return fract(sin(dot(co, float2(12.9898, 78.233))) * 43758.5453);
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;
            float dist = length(uv - 0.5) * 2.0;
            
            float noise = rand(fragCoord * 0.3);
            float alpha = 0.0;
            
            if (noise > dryness) {
                alpha = 1.0 - smoothstep(0.0, 1.0, dist);
            }
            
            alpha *= pressure;
            
            return half4(0.0, 0.0, 0.0, alpha);
        }
    """.trimIndent()

    /**
     * Stipple dots: pointillist dot pattern.
     */
    val STIPPLE_DOTS = """
        uniform float2 resolution;
        uniform float pressure;
        uniform float stipple;

        float rand(float2 co) {
            return fract(sin(dot(co, float2(12.9898, 78.233))) * 43758.5453);
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;
            
            // Create stipple grid
            float2 grid = fract(uv * stipple);
            float dist = length(grid - 0.5) * 2.0;
            
            float alpha = 1.0 - smoothstep(0.0, 1.0, dist);
            alpha *= pressure;
            
            return half4(0.0, 0.0, 0.0, alpha);
        }
    """.trimIndent()

    /**
     * Sumi gradient: tonal gradation for East Asian ink painting.
     */
    val SUMI_GRADIENT = """
        uniform float2 resolution;
        uniform float pressure;
        uniform float gradient;

        float rand(float2 co) {
            return fract(sin(dot(co, float2(12.9898, 78.233))) * 43758.5453);
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;
            float dist = length(uv - 0.5) * 2.0;
            
            float alpha = 1.0 - smoothstep(0.0, 1.0, dist);
            
            // Radial gradient
            float grad = mix(1.0, dist * 0.5, gradient);
            alpha *= grad * pressure;
            
            return half4(0.0, 0.0, 0.0, alpha);
        }
    """.trimIndent()

    /**
     * Sumi taper: Japanese ink brush with tapered ends.
     */
    val SUMI_TAPER = """
        uniform float2 resolution;
        uniform float pressure;
        uniform float taper;

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;
            float2 centered = uv - 0.5;
            
            // Taper brush strokes
            float dist = length(centered * float2(0.8, 1.2)) * 2.0;
            float alpha = 1.0 - smoothstep(0.0, 1.0, dist);
            
            alpha *= pressure;
            
            return half4(0.0, 0.0, 0.0, alpha);
        }
    """.trimIndent()

    /**
     * Calligraphy angle: flat-tip with angle-dependent width.
     */
    val CALLIGRAPHY_ANGLE = """
        uniform float2 resolution;
        uniform float pressure;
        uniform float angle;

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;
            float2 centered = uv - 0.5;
            
            // Rotate based on angle
            float rotated = centered.x * cos(angle) - centered.y * sin(angle);
            float perpendicular = centered.x * sin(angle) + centered.y * cos(angle);
            
            float dist = length(float2(rotated * 0.3, perpendicular)) * 2.0;
            float alpha = 1.0 - smoothstep(0.0, 1.0, dist);
            
            alpha *= pressure;
            
            return half4(0.0, 0.0, 0.0, alpha);
        }
    """.trimIndent()

    /**
     * Brush pen flex: flexible nib with angle variation.
     */
    val BRUSH_PEN_FLEX = """
        uniform float2 resolution;
        uniform float pressure;
        uniform float flex;

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;
            float2 centered = uv - 0.5;
            
            // Flex effect varies width with angle
            float angle = atan(centered.y, centered.x);
            float width = 0.8 + sin(angle * 2.0) * flex * pressure;
            
            float dist = length(centered) * 2.0 / width;
            float alpha = 1.0 - smoothstep(0.0, 1.0, dist);
            
            return half4(0.0, 0.0, 0.0, alpha);
        }
    """.trimIndent()

    /**
     * Felt edge: soft, fuzzy edges like felt markers.
     */
    val FELT_EDGE = """
        uniform float2 resolution;
        uniform float pressure;
        uniform float fuzziness;

        float rand(float2 co) {
            return fract(sin(dot(co, float2(12.9898, 78.233))) * 43758.5453);
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;
            float dist = length(uv - 0.5) * 2.0;
            
            float fuzz = rand(fragCoord * 2.0) * fuzziness;
            float alpha = 1.0 - smoothstep(0.0 + fuzz, 1.0 + fuzz, dist);
            
            alpha *= pressure;
            
            return half4(0.0, 0.0, 0.0, alpha);
        }
    """.trimIndent()

    /**
     * Concrete pits: rough porous surface with random pitting.
     */
    val CONCRETE_PITS = """
        uniform float2 resolution;
        uniform float pressure;
        uniform float porosity;

        float rand(float2 co) {
            return fract(sin(dot(co, float2(12.9898, 78.233))) * 43758.5453);
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;
            float dist = length(uv - 0.5) * 2.0;
            
            float alpha = 1.0 - smoothstep(0.0, 1.0, dist);
            
            // Random pits
            float pits = 0.0;
            for (int i = 0; i < 8; i++) {
                float2 pitPos = float2(rand(float2(i, 0)), rand(float2(i, 1))) - 0.5;
                float pitDist = length(uv - 0.5 - pitPos * 0.3);
                pits += exp(-pitDist * 20.0) * porosity;
            }
            
            alpha = (alpha - pits * 0.5) * pressure;
            
            return half4(0.0, 0.0, 0.0, clamp(alpha, 0.0, 1.0));
        }
    """.trimIndent()

    /**
     * Sponge holes: natural sponge with organic holes.
     */
    val SPONGE_HOLES = """
        uniform float2 resolution;
        uniform float pressure;
        uniform float sponginess;

        float rand(float2 co) {
            return fract(sin(dot(co, float2(12.9898, 78.233))) * 43758.5453);
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;
            float dist = length(uv - 0.5) * 2.0;
            
            float alpha = 1.0 - smoothstep(0.0, 1.0, dist);
            
            // Sponge holes
            float holes = 0.0;
            for (int i = 0; i < 10; i++) {
                float2 holePos = float2(rand(float2(i * 2.0, 0.0)), rand(float2(i * 2.0, 1.0))) - 0.5;
                float holeDist = length(uv - 0.5 - holePos * 0.4);
                holes += max(0.0, 0.15 - holeDist) * 2.0;
            }
            
            alpha = max(0.0, alpha - holes * sponginess) * pressure;
            
            return half4(0.0, 0.0, 0.0, alpha);
        }
    """.trimIndent()

    /**
     * Crackle fractal: aged, cracked surface.
     */
    val CRACKLE_FRACTAL = """
        uniform float2 resolution;
        uniform float pressure;
        uniform float cracking;

        float rand(float2 co) {
            return fract(sin(dot(co, float2(12.9898, 78.233))) * 43758.5453);
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;
            float dist = length(uv - 0.5) * 2.0;
            
            float alpha = 1.0 - smoothstep(0.0, 1.0, dist);
            
            // Fine cracks
            float cracks = abs(sin(uv.x * 20.0)) * abs(sin(uv.y * 20.0)) * cracking;
            alpha = (alpha - cracks * 0.4) * pressure;
            
            return half4(0.0, 0.0, 0.0, clamp(alpha, 0.0, 1.0));
        }
    """.trimIndent()

    /**
     * Rust speckle: corroded metal with orange/brown speckles.
     */
    val RUST_SPECKLE = """
        uniform float2 resolution;
        uniform float pressure;
        uniform float oxidation;

        float rand(float2 co) {
            return fract(sin(dot(co, float2(12.9898, 78.233))) * 43758.5453);
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;
            float dist = length(uv - 0.5) * 2.0;
            
            float alpha = 1.0 - smoothstep(0.0, 1.0, dist);
            
            float speckles = 0.0;
            for (int i = 0; i < 12; i++) {
                float2 speckPos = float2(rand(float2(i, 0)), rand(float2(i, 1))) - 0.5;
                float speckDist = length(uv - 0.5 - speckPos * 0.5);
                speckles += exp(-speckDist * 15.0) * oxidation;
            }
            
            alpha = (alpha + speckles * 0.5) * pressure;
            
            return half4(0.0, 0.0, 0.0, clamp(alpha, 0.0, 1.0));
        }
    """.trimIndent()

    /**
     * Moss organic: organic moss/lichen growth pattern.
     */
    val MOSS_ORGANIC = """
        uniform float2 resolution;
        uniform float pressure;
        uniform float growth;

        float rand(float2 co) {
            return fract(sin(dot(co, float2(12.9898, 78.233))) * 43758.5453);
        }

        float fbm(float2 p) {
            float v = 0.0;
            float a = 0.5;
            for (int i = 0; i < 4; i++) {
                v += a * sin(dot(p, float2(12.9898, 78.233)));
                p *= 2.0;
                a *= 0.5;
            }
            return v;
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;
            float dist = length(uv - 0.5) * 2.0;
            
            float alpha = 1.0 - smoothstep(0.0, 1.0, dist);
            
            float moss = fbm(uv * 5.0) * growth;
            alpha = (alpha + moss * 0.5) * pressure;
            
            return half4(0.0, 0.0, 0.0, clamp(alpha, 0.0, 1.0));
        }
    """.trimIndent()

    /**
     * Sand blast: aggressive sand-blasted surface.
     */
    val SAND_BLAST = """
        uniform float2 resolution;
        uniform float pressure;
        uniform float aggression;

        float rand(float2 co) {
            return fract(sin(dot(co, float2(12.9898, 78.233))) * 43758.5453);
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;
            float dist = length(uv - 0.5) * 2.0;
            
            float alpha = 1.0 - smoothstep(0.0, 1.0, dist);
            
            float blast = rand(fragCoord * 0.5) * aggression;
            alpha = (alpha - blast * 0.3) * pressure;
            
            return half4(0.0, 0.0, 0.0, clamp(alpha, 0.0, 1.0));
        }
    """.trimIndent()

    /**
     * Splatter organic: organic ink splatters.
     */
    val SPLATTER_ORGANIC = """
        uniform float2 resolution;
        uniform float pressure;
        uniform float chaos;

        float rand(float2 co) {
            return fract(sin(dot(co, float2(12.9898, 78.233))) * 43758.5453);
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;
            float2 centered = uv - 0.5;
            
            float splatters = 0.0;
            for (int i = 0; i < 15; i++) {
                float2 splatPos = float2(rand(float2(i, 0)), rand(float2(i, 1))) - 0.5;
                float splatDist = length(centered - splatPos * 0.6);
                splatters += max(0.0, 0.2 - splatDist) * 5.0;
            }
            
            float alpha = min(1.0, splatters * chaos) * pressure;
            
            return half4(0.0, 0.0, 0.0, alpha);
        }
    """.trimIndent()

    /**
     * Wood grain: directional wood grain with aging cracks.
     */
    val WOOD_GRAIN = """
        uniform float2 resolution;
        uniform float pressure;
        uniform float graining;

        float rand(float2 co) {
            return fract(sin(dot(co, float2(12.9898, 78.233))) * 43758.5453);
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;
            float dist = length(uv - 0.5) * 2.0;
            
            float alpha = 1.0 - smoothstep(0.0, 1.0, dist);
            
            // Directional grain
            float grain = sin(uv.x * 20.0) * graining;
            alpha = (alpha + grain * 0.3) * pressure;
            
            return half4(0.0, 0.0, 0.0, clamp(alpha, 0.0, 1.0));
        }
    """.trimIndent()

    /**
     * Fabric weave: regular weave pattern.
     */
    val FABRIC_WEAVE = """
        uniform float2 resolution;
        uniform float pressure;
        uniform float weave;

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;
            float dist = length(uv - 0.5) * 2.0;
            
            float alpha = 1.0 - smoothstep(0.0, 1.0, dist);
            
            // Regular weave
            float weavePat = mod(uv.x * 10.0, 1.0) + mod(uv.y * 10.0, 1.0);
            float weaveEffect = step(1.0, weavePat) * weave;
            
            alpha = (alpha - weaveEffect * 0.2) * pressure;
            
            return half4(0.0, 0.0, 0.0, clamp(alpha, 0.0, 1.0));
        }
    """.trimIndent()

    /**
     * Perlin noise: smooth Perlin noise for organic effects.
     */
    val PERLIN_NOISE = """
        uniform float2 resolution;
        uniform float pressure;
        uniform float noiseScale;

        float rand(float2 co) {
            return fract(sin(dot(co, float2(12.9898, 78.233))) * 43758.5453);
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;
            float dist = length(uv - 0.5) * 2.0;
            
            float alpha = 1.0 - smoothstep(0.0, 1.0, dist);
            
            float noise = rand(uv * noiseScale) * 0.5 + rand(uv * noiseScale * 0.5) * 0.5;
            alpha = (alpha + (noise - 0.5) * 0.3) * pressure;
            
            return half4(0.0, 0.0, 0.0, clamp(alpha, 0.0, 1.0));
        }
    """.trimIndent()

    /**
     * Binary bokeh: crisp bokeh circles with digital appearance.
     */
    val BINARY_BOKEH = """
        uniform float2 resolution;
        uniform float pressure;
        uniform float bokehs;

        float rand(float2 co) {
            return fract(sin(dot(co, float2(12.9898, 78.233))) * 43758.5453);
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;
            
            float bokeh = 0.0;
            for (int i = 0; i < 20; i++) {
                float2 bokehPos = float2(rand(float2(i, 0)), rand(float2(i, 1)));
                float bokehDist = length(uv - bokehPos) * 3.0;
                bokeh += exp(-bokehDist * 2.0) * 0.5;
            }
            
            float alpha = min(1.0, bokeh * bokehs) * pressure;
            
            return half4(0.0, 0.0, 0.0, alpha);
        }
    """.trimIndent()

    /**
     * Geometric shapes: repeating geometric patterns.
     */
    val GEOMETRIC_SHAPES = """
        uniform float2 resolution;
        uniform float pressure;
        uniform float scale;

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;
            float2 centered = uv - 0.5;
            
            float angle = atan(centered.y, centered.x);
            float radius = length(centered);
            
            // Hexagon pattern
            float hex = cos(angle * 3.0) * 0.5 + 0.5;
            
            float alpha = smoothstep(0.0, scale, hex) * pressure;
            
            return half4(0.0, 0.0, 0.0, alpha);
        }
    """.trimIndent()

    /**
     * Glitch RGB: RGB channel separation glitch effect.
     */
    val GLITCH_RGB = """
        uniform float2 resolution;
        uniform float pressure;
        uniform float glitch;

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;
            float dist = length(uv - 0.5) * 2.0;
            
            // RGB separation
            float r = 1.0 - smoothstep(0.0, 1.0, dist - glitch * 0.1);
            float g = 1.0 - smoothstep(0.0, 1.0, dist);
            float b = 1.0 - smoothstep(0.0, 1.0, dist + glitch * 0.1);
            
            return half4(r, g, b, min(1.0, r + g + b) * pressure);
        }
    """.trimIndent()

    /**
     * Neon glow: intense glowing neon effect.
     */
    val NEON_GLOW = """
        uniform float2 resolution;
        uniform float pressure;
        uniform float glow;

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;
            float dist = length(uv - 0.5) * 2.0;
            
            float core = 1.0 - smoothstep(0.0, 0.3, dist);
            float halo = exp(-dist * glow) * 0.8;
            
            float alpha = (core + halo) * pressure;
            
            return half4(0.0, 0.0, 0.0, min(1.0, alpha));
        }
    """.trimIndent()

    /**
     * Particle emitter: flowing particle stream.
     */
    val PARTICLE_EMITTER = """
        uniform float2 resolution;
        uniform float pressure;
        uniform float particles;

        float rand(float2 co) {
            return fract(sin(dot(co, float2(12.9898, 78.233))) * 43758.5453);
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;
            
            float particle = 0.0;
            for (int i = 0; i < 25; i++) {
                float2 particlePos = float2(rand(float2(i, 0)), rand(float2(i, 1)));
                float particleDist = length(uv - particlePos);
                particle += exp(-particleDist * 15.0) * particles;
            }
            
            float alpha = min(1.0, particle) * pressure;
            
            return half4(0.0, 0.0, 0.0, alpha);
        }
    """.trimIndent()

    /**
     * Halftone dots: CMY halftone pattern.
     */
    val HALFTONE_DOTS = """
        uniform float2 resolution;
        uniform float pressure;
        uniform float frequency;

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;
            float dist = length(uv - 0.5) * 2.0;
            
            float alpha = 1.0 - smoothstep(0.0, 1.0, dist);
            
            // Halftone pattern
            float dotSize = sin(uv.x * frequency) * sin(uv.y * frequency) * 0.5 + 0.5;
            alpha = alpha * dotSize * pressure;
            
            return half4(0.0, 0.0, 0.0, alpha);
        }
    """.trimIndent()

    /**
     * Lightning bolt: jagged electric lightning.
     */
    val LIGHTNING_BOLT = """
        uniform float2 resolution;
        uniform float pressure;
        uniform float jaggedness;

        float rand(float2 co) {
            return fract(sin(dot(co, float2(12.9898, 78.233))) * 43758.5453);
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;
            float2 centered = uv - 0.5;
            
            // Jagged pattern
            float jag = abs(sin(centered.y * 20.0 + rand(float2(centered.x, 0)) * jaggedness)) * 0.2;
            
            float line = abs(centered.x) + jag;
            float alpha = (1.0 - smoothstep(0.0, 0.05, line)) * pressure;
            
            return half4(0.0, 0.0, 0.0, alpha);
        }
    """.trimIndent()

    /**
     * Chromatic aberration: RGB color separation.
     */
    val CHROMA_ABERRATION = """
        uniform float2 resolution;
        uniform float pressure;
        uniform float aberration;

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;
            float dist = length(uv - 0.5) * 2.0;
            
            float r = 1.0 - smoothstep(0.0, 1.0, dist - aberration * 0.05);
            float g = 1.0 - smoothstep(0.0, 1.0, dist);
            float b = 1.0 - smoothstep(0.0, 1.0, dist + aberration * 0.05);
            
            float alpha = max(max(r, g), b) * pressure;
            
            return half4(r, g, b, alpha);
        }
    """.trimIndent()

    /**
     * Starburst rays: radiating rays from center.
     */
    val STARBURST_RAYS = """
        uniform float2 resolution;
        uniform float pressure;
        uniform float rays;

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;
            float2 centered = uv - 0.5;
            
            float angle = atan(centered.y, centered.x);
            float dist = length(centered);
            
            // Radiating rays
            float ray = abs(sin(angle * rays)) * 0.5 + 0.5;
            float alpha = (1.0 - smoothstep(0.0, 0.5, dist)) * ray * pressure;
            
            return half4(0.0, 0.0, 0.0, alpha);
        }
    """.trimIndent()

    /**
     * Smoke wisp: soft, curling smoke trails.
     */
    val SMOKE_WISP = """
        uniform float2 resolution;
        uniform float pressure;
        uniform float smokiness;

        float rand(float2 co) {
            return fract(sin(dot(co, float2(12.9898, 78.233))) * 43758.5453);
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;
            float dist = length(uv - 0.5) * 2.0;
            
            float alpha = exp(-dist * smokiness);
            
            float noise = rand(uv * 5.0) * 0.5 + 0.5;
            alpha *= noise * pressure;
            
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

    fun createShaderForBrush(shaderId: String?): RuntimeShader? =
        when (shaderId) {
            "pencil_grain" -> RuntimeShader(BrushShaders.PENCIL_AGSL)
            "conte_texture" -> RuntimeShader(BrushShaders.CONTE_TEXTURE)
            "tinted_charcoal" -> RuntimeShader(BrushShaders.TINTED_CHARCOAL)
            "powder_scatter" -> RuntimeShader(BrushShaders.POWDER_SCATTER)
            "sketch_streaks" -> RuntimeShader(BrushShaders.SKETCH_STREAKS)
            "impasto_relief" -> RuntimeShader(BrushShaders.IMPASTO_RELIEF)
            "watercolor_flow" -> RuntimeShader(BrushShaders.WATERCOLOR_FLOW)
            "drybrush_breaks" -> RuntimeShader(BrushShaders.DRYBRUSH_BREAKS)
            "stipple_dots" -> RuntimeShader(BrushShaders.STIPPLE_DOTS)
            "sumi_gradient" -> RuntimeShader(BrushShaders.SUMI_GRADIENT)
            "sumi_taper" -> RuntimeShader(BrushShaders.SUMI_TAPER)
            "calligraphy_angle" -> RuntimeShader(BrushShaders.CALLIGRAPHY_ANGLE)
            "brush_pen_flex" -> RuntimeShader(BrushShaders.BRUSH_PEN_FLEX)
            "felt_edge" -> RuntimeShader(BrushShaders.FELT_EDGE)
            "concrete_pits" -> RuntimeShader(BrushShaders.CONCRETE_PITS)
            "sponge_holes" -> RuntimeShader(BrushShaders.SPONGE_HOLES)
            "crackle_fractal" -> RuntimeShader(BrushShaders.CRACKLE_FRACTAL)
            "rust_speckle" -> RuntimeShader(BrushShaders.RUST_SPECKLE)
            "moss_organic" -> RuntimeShader(BrushShaders.MOSS_ORGANIC)
            "sand_blast" -> RuntimeShader(BrushShaders.SAND_BLAST)
            "splatter_organic" -> RuntimeShader(BrushShaders.SPLATTER_ORGANIC)
            "wood_grain" -> RuntimeShader(BrushShaders.WOOD_GRAIN)
            "fabric_weave" -> RuntimeShader(BrushShaders.FABRIC_WEAVE)
            "perlin_noise" -> RuntimeShader(BrushShaders.PERLIN_NOISE)
            "binary_bokeh" -> RuntimeShader(BrushShaders.BINARY_BOKEH)
            "geometric_shapes" -> RuntimeShader(BrushShaders.GEOMETRIC_SHAPES)
            "glitch_rgb" -> RuntimeShader(BrushShaders.GLITCH_RGB)
            "neon_glow" -> RuntimeShader(BrushShaders.NEON_GLOW)
            "particle_emitter" -> RuntimeShader(BrushShaders.PARTICLE_EMITTER)
            "halftone_dots" -> RuntimeShader(BrushShaders.HALFTONE_DOTS)
            "lightning_bolt" -> RuntimeShader(BrushShaders.LIGHTNING_BOLT)
            "chroma_aberration" -> RuntimeShader(BrushShaders.CHROMA_ABERRATION)
            "starburst_rays" -> RuntimeShader(BrushShaders.STARBURST_RAYS)
            "smoke_wisp" -> RuntimeShader(BrushShaders.SMOKE_WISP)
            else -> null
        }

    fun updateShaderUniforms(
        shader: RuntimeShader,
        width: Float,
        height: Float,
        pressure: Float,
        textureParam: Float,
    ) {
        shader.setFloatUniform("resolution", width, height)
        shader.setFloatUniform("pressure", pressure)
        // Each shader declares only one texture-control uniform with a brush-specific name.
        // We attempt all three names; two will legitimately throw because the uniform does
        // not exist in that shader's AGSL source — this is the expected (non-error) path.
        try {
            shader.setFloatUniform("grain", textureParam)     // pencil shader
        } catch (_: Exception) { /* uniform absent in other shaders */ }
        try {
            shader.setFloatUniform("wetness", textureParam)   // watercolor shader
        } catch (_: Exception) { /* uniform absent in other shaders */ }
        try {
            shader.setFloatUniform("roughness", textureParam) // charcoal shader
        } catch (_: Exception) { /* uniform absent in other shaders */ }
        try {
            shader.setFloatUniform("porosity", textureParam)  // concrete shader
        } catch (_: Exception) { /* uniform absent in other shaders */ }
        try {
            shader.setFloatUniform("sponginess", textureParam) // sponge shader
        } catch (_: Exception) { /* uniform absent in other shaders */ }
        try {
            shader.setFloatUniform("cracking", textureParam)  // crackle shader
        } catch (_: Exception) { /* uniform absent in other shaders */ }
        try {
            shader.setFloatUniform("oxidation", textureParam) // rust shader
        } catch (_: Exception) { /* uniform absent in other shaders */ }
        try {
            shader.setFloatUniform("growth", textureParam)    // moss shader
        } catch (_: Exception) { /* uniform absent in other shaders */ }
        try {
            shader.setFloatUniform("aggression", textureParam) // sand blast shader
        } catch (_: Exception) { /* uniform absent in other shaders */ }
        try {
            shader.setFloatUniform("chaos", textureParam)     // splatter shader
        } catch (_: Exception) { /* uniform absent in other shaders */ }
        try {
            shader.setFloatUniform("graining", textureParam)  // wood grain shader
        } catch (_: Exception) { /* uniform absent in other shaders */ }
        try {
            shader.setFloatUniform("weave", textureParam)     // fabric weave shader
        } catch (_: Exception) { /* uniform absent in other shaders */ }
        try {
            shader.setFloatUniform("noiseScale", textureParam) // perlin noise shader
        } catch (_: Exception) { /* uniform absent in other shaders */ }
        try {
            shader.setFloatUniform("bokehs", textureParam)    // bokeh shader
        } catch (_: Exception) { /* uniform absent in other shaders */ }
        try {
            shader.setFloatUniform("scale", textureParam)     // geometric shader
        } catch (_: Exception) { /* uniform absent in other shaders */ }
        try {
            shader.setFloatUniform("glitch", textureParam)    // glitch shader
        } catch (_: Exception) { /* uniform absent in other shaders */ }
        try {
            shader.setFloatUniform("glow", textureParam)      // neon glow shader
        } catch (_: Exception) { /* uniform absent in other shaders */ }
        try {
            shader.setFloatUniform("particles", textureParam) // particle emitter shader
        } catch (_: Exception) { /* uniform absent in other shaders */ }
        try {
            shader.setFloatUniform("frequency", textureParam) // halftone shader
        } catch (_: Exception) { /* uniform absent in other shaders */ }
        try {
            shader.setFloatUniform("jaggedness", textureParam) // lightning shader
        } catch (_: Exception) { /* uniform absent in other shaders */ }
        try {
            shader.setFloatUniform("aberration", textureParam) // chromatic aberration shader
        } catch (_: Exception) { /* uniform absent in other shaders */ }
        try {
            shader.setFloatUniform("rays", textureParam)      // starburst shader
        } catch (_: Exception) { /* uniform absent in other shaders */ }
        try {
            shader.setFloatUniform("smokiness", textureParam) // smoke wisp shader
        } catch (_: Exception) { /* uniform absent in other shaders */ }
        try {
            shader.setFloatUniform("relief", textureParam)    // impasto shader
        } catch (_: Exception) { /* uniform absent in other shaders */ }
        try {
            shader.setFloatUniform("streakiness", textureParam) // sketch streaks shader
        } catch (_: Exception) { /* uniform absent in other shaders */ }
        try {
            shader.setFloatUniform("dryness", textureParam)   // drybrush shader
        } catch (_: Exception) { /* uniform absent in other shaders */ }
        try {
            shader.setFloatUniform("stipple", textureParam)   // stipple shader
        } catch (_: Exception) { /* uniform absent in other shaders */ }
        try {
            shader.setFloatUniform("gradient", textureParam)  // sumi gradient shader
        } catch (_: Exception) { /* uniform absent in other shaders */ }
        try {
            shader.setFloatUniform("taper", textureParam)     // sumi taper shader
        } catch (_: Exception) { /* uniform absent in other shaders */ }
        try {
            shader.setFloatUniform("angle", textureParam)     // calligraphy angle shader
        } catch (_: Exception) { /* uniform absent in other shaders */ }
        try {
            shader.setFloatUniform("flex", textureParam)      // brush pen flex shader
        } catch (_: Exception) { /* uniform absent in other shaders */ }
        try {
            shader.setFloatUniform("fuzziness", textureParam) // felt edge shader
        } catch (_: Exception) { /* uniform absent in other shaders */ }
    }
}
