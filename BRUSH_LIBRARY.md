# Artist-Haven Brush Library Documentation

## Overview

The comprehensive brush library provides **50 unique, artist-quality brushes** organized into 5 functional categories. Each brush is fully defined with technical parameters, AGSL shaders, and pressure sensitivity modes for professional drawing capabilities.

---

## 📚 Brush Categories & Organization

### 1. **Sketching Brushes** (10 brushes)
*Low opacity, high grain, responsive to pressure and tilt*

| # | Brush Name | Characteristics | Pressure Response |
|---|-----------|-----------------|-------------------|
| 1 | Soft HB Pencil | Natural graphite grain, tapered ends | Size + Opacity |
| 2 | Rough Conté Crayon | Coarse texture, significant drag | Flow |
| 3 | Mechanical Pencil | Precise uniform lines, minimal texture | Opacity |
| 4 | Charcoal Stick | Heavy grain, soft edges, smudge-friendly | Size + Opacity |
| 5 | Blending Stump | Very soft, low opacity, smooth blending | Flow |
| 6 | Tinted Charcoal | Medium grain, warm undertones | Size + Opacity |
| 7 | Graphite Powder | Heavy grain, builds with layering | Flow |
| 8 | Cross-Hatching Pen | Fine lines, minimal texture | None |
| 9 | Kneaded Eraser | Very soft edges, gentle removal | Opacity |
| 10 | Rough Sketch | Directional streaks, loose and organic | Size |

---

### 2. **Painting Brushes** (10 brushes)
*Color mixing, wetness simulation, flow-responsive*

| # | Brush Name | Characteristics | Pressure Response |
|---|-----------|-----------------|-------------------|
| 1 | Thick Impasto | Heavy paint, visible brush strokes | Size |
| 2 | Diluted Watercolor | Transparent, soft edges, color bleeding | Flow |
| 3 | Flat Brush | Rectangular shape, wide coverage | Size |
| 4 | Round Mop | Large soft brush, excellent blending | Flow |
| 5 | Wet-on-Wet | Maximum diffusion, feathered edges | Flow |
| 6 | Glazing Brush | Very transparent, smooth flow | None |
| 7 | Drybrush | Sparse coverage, broken strokes | Flow |
| 8 | Oil Blending | Soft bristles, natural color mixing | Size |
| 9 | Stipple Brush | Pointillist dots, organic mixing | Opacity |
| 10 | Sumi Watercolor | East Asian ink style, tonal gradation | Size + Opacity |

---

### 3. **Inking Brushes** (10 brushes)
*High contrast, sharp lines, smooth flow, ink control*

| # | Brush Name | Characteristics | Pressure Response |
|---|-----------|-----------------|-------------------|
| 1 | Technical Fineliner | Ultra-precise, uniform lines | None |
| 2 | Sumi-e Ink | Tapered ends, expressive variation | Size |
| 3 | Comic Ink | Bold, consistent weight | Size |
| 4 | Calligraphy Brush | Flat chisel-tip, angle-dependent width | Size |
| 5 | Vector Ink | Post-smoothed lines, digital vector style | None |
| 6 | Ballpoint Pen | Classic everyday appearance | Opacity |
| 7 | Liquid Ink | Smooth flow, consistent saturation | Size |
| 8 | Brush Pen | Flexible nib, natural variation | Size + Opacity |
| 9 | Marker Ink | Vibrant, opaque, alcohol-based | Opacity |
| 10 | Felt Liner | Fuzzy edges, matte finish | Opacity |

---

### 4. **Textural/Grunge Brushes** (10 brushes)
*Dual-brush stamps, noise patterns, organic textures*

| # | Brush Name | Characteristics | Pressure Response |
|---|-----------|-----------------|-------------------|
| 1 | Distressed Concrete | Rough porous surface, random pitting | Flow |
| 2 | Sponge Dab | Natural holes, irregular edges | Opacity |
| 3 | Crackle Texture | Aged appearance, fine cracks | Flow |
| 4 | Rust & Corrosion | Speckled oxidation, warm tones | Opacity |
| 5 | Moss Growth | Organic clusters, natural variation | Flow |
| 6 | Sand Blast | Aggressive texture, impact marks | Flow |
| 7 | Ink Splatter | Organic splatters, chaotic pattern | None |
| 8 | Weathered Wood | Directional grain, aging cracks | Flow |
| 9 | Fabric Weave | Regular weave pattern, thread texture | Flow |
| 10 | Digital Noise | Perlin noise, organic digital effects | Opacity |

---

### 5. **Special Effects Brushes** (10 brushes)
*Particle systems, glow, geometry, digital-first effects*

| # | Brush Name | Characteristics | Pressure Response |
|---|-----------|-----------------|-------------------|
| 1 | Binary Bokeh | Crisp bokeh circles, digital glitch | Opacity |
| 2 | Geometric Scatter | Repeating shapes, precise angles | Opacity |
| 3 | Glitch Ribbon | RGB separation, pixel blocks | Size |
| 4 | Neon Vapor | Intense glow, vaporwave aesthetic | Opacity |
| 5 | Particle Stream | Flowing particles, fading trail | Flow |
| 6 | Halftone Dots | CMY pattern, pressure-responsive density | Flow |
| 7 | Lightning Spark | Jagged bolts, electric glow | Size |
| 8 | Chromatic Aberration | RGB color separation, lens effect | Size |
| 9 | Starburst Rays | Radiating geometry, lens flare look | Opacity |
| 10 | Smoke Wisp | Soft trails, organic diffusion | Flow |

---

## 🔧 Technical Parameters Reference

### Core Parameters for Each Brush

```kotlin
data class BrushDefinition(
    val id: String,                           // Unique identifier (e.g., "sketch_01")
    val name: String,                         // Display name
    val category: BrushCategory,              // Sketching, Painting, Inking, Textural, Special Effects
    val displayName: String,                  // User-facing name
    val description: String,                  // Long description
    val coreCharacteristics: String,          // Technical behavior description
    val blendMode: BlendMode,                 // Compose blend mode (SrcOver, Multiply, Screen, etc.)
    val defaultSize: Float,                   // Default brush size (pixels)
    val defaultOpacity: Float,                // Default opacity (0.0-1.0)
    val defaultHardness: Float,               // Edge hardness (0.0 soft - 1.0 hard)
    val defaultFlow: Float = 1f,              // Paint flow rate (0.0-1.0)
    val spacing: Float = 0.1f,                // Spacing between impressions (0.0-1.0)
    val scatter: Float = 0f,                  // Random placement scatter (0.0-20.0+)
    val jitter: Float = 0f,                   // Angle/size variation (0.0-10.0+)
    val pressureSensitivity: PressureSensitivityMode,
    val usesShader: Boolean = false,          // Whether to apply AGSL shader
    val shaderId: String? = null,             // Reference to AGSL shader
)
```

---

## 💡 Pressure Sensitivity Modes

```kotlin
enum class PressureSensitivityMode {
    PRESSURE_TO_SIZE,              // Pressure increases brush width/height
    PRESSURE_TO_OPACITY,           // Pressure modulates alpha channel
    PRESSURE_TO_FLOW,              // Pressure modulates coverage per distance
    PRESSURE_TO_SIZE_AND_OPACITY,  // Pressure affects both size and opacity
    PRESSURE_TO_HARDNESS,          // Pressure affects edge softness
    NONE,                          // No pressure sensitivity
}
```

---

## 🎨 Blend Modes & Their Effects

| Blend Mode | Use Case | Visual Effect |
|-----------|----------|--------------|
| `SrcOver` | Default painting | Normal compositing |
| `Multiply` | Sketching, painting | Darkens underlying |
| `Screen` | Glow, effects | Lightens, additive |
| `Clear` | Erasers | Complete transparency |
| `Lighten` | Light effects | Only keeps light values |

---

## 🖌️ Using the Brush Library in Your Code

### 1. Access All Brushes
```kotlin
val allBrushes = BrushLibrary.allBrushes  // List of 50 BrushDefinition objects
```

### 2. Get Brushes by Category
```kotlin
val sketchingBrushes = BrushLibrary.getBrushesByCategory(BrushCategory.SKETCHING)
val paintingBrushes = BrushLibrary.getBrushesByCategory(BrushCategory.PAINTING)
// etc.
```

### 3. Get a Specific Brush
```kotlin
val pencilBrush = BrushLibrary.getBrushById("sketch_01")  // Soft HB Pencil
```

### 4. Get All Available Shaders
```kotlin
val shaderIds = BrushLibrary.getAllShaderIds()  // Set<String> of shader identifiers
```

### 5. Create a Drawing Brush from BrushDefinition
```kotlin
val brushDef = BrushLibrary.getBrushById("sketch_01")!!
val drawingBrush = Brush(
    type = BrushType.PENCIL,  // Map to enum or create custom mapping
    size = brushDef.defaultSize,
    opacity = brushDef.defaultOpacity,
    hardness = brushDef.defaultHardness,
    spacing = brushDef.spacing,
    textureStrength = if (brushDef.usesShader) brushDef.scatter else 0f,
)
```

---

## 🎯 Integration Points

### 1. **Brush Selection UI**
Display brushes organized by category with thumbnails:
```kotlin
@Composable
fun BrushPalette() {
    val categories = BrushCategory.values()

    categories.forEach { category ->
        BrushCategorySection(
            category = category,
            brushes = BrushLibrary.getBrushesByCategory(category)
        )
    }
}
```

### 2. **Shader Rendering**
Integrate shaders into your CanvasView:
```kotlin
val shaderFactory = BrushShaderFactory()
val shader = shaderFactory.createShaderForBrush(brushDef.shaderId)

shader?.let {
    shaderFactory.updateShaderUniforms(
        shader = it,
        width = canvasWidth.toFloat(),
        height = canvasHeight.toFloat(),
        pressure = strokePoint.pressure,
        textureParam = brushDef.scatter
    )
}
```

### 3. **Pressure Response Mapping**
Implement pressure sensitivity based on mode:
```kotlin
fun applyPressureSensitivity(
    brush: BrushDefinition,
    pressure: Float,
): BrushModifier {
    return when (brush.pressureSensitivity) {
        PressureSensitivityMode.PRESSURE_TO_SIZE -> 
            BrushModifier(size = brush.defaultSize * pressure)

        PressureSensitivityMode.PRESSURE_TO_OPACITY ->
            BrushModifier(opacity = brush.defaultOpacity * pressure)

        PressureSensitivityMode.PRESSURE_TO_FLOW ->
            BrushModifier(flow = brush.defaultFlow * pressure)

        // ... handle other modes
    }
}
```

---

## 📊 Parameter Ranges & Recommendations

### Size
- **Sketching:** 2-25px
- **Painting:** 15-35px
- **Inking:** 2.5-14px
- **Textural:** 24-35px
- **Effects:** 14-28px

### Opacity
- **Low (0.2-0.4):** Glazing, watercolor, smoke effects
- **Medium (0.4-0.7):** Blending, textural, general effects
- **High (0.7-1.0):** Inking, mechanical, bold strokes

### Spacing
- **Tight (0.04-0.08):** Smooth, precise lines (inking, technical)
- **Medium (0.1-0.2):** General purpose (sketching, painting)
- **Loose (0.3-0.6):** Stippling, particle effects

### Scatter
- **None (0):** Precise tools (technical pen, vector ink)
- **Low (0.5-3):** Natural tools (pencil, pen)
- **Medium (5-12):** Textural tools (concrete, rust)
- **High (15-25):** Effects and splatter

### Hardness
- **Soft (0.0-0.3):** Blending, watercolor, diffuse effects
- **Medium (0.4-0.7):** General purpose, inking
- **Hard (0.8-1.0):** Precise lines, technical tools

---

## 🔍 Shader IDs & Their Parameters

| Shader ID | Description | Primary Parameter |
|-----------|-------------|-------------------|
| `pencil_grain` | Graphite texture | `grain` (0.0-1.0) |
| `conte_texture` | Crayon roughness | `roughness` (0.0-1.0) |
| `watercolor_flow` | Color diffusion | `flow` (0.0-1.0) |
| `impasto_relief` | Paint thickness | `relief` (0.0-1.0) |
| `sumi_gradient` | Tonal gradation | `gradient` (0.0-1.0) |
| `concrete_pits` | Surface porosity | `porosity` (0.0-1.0) |
| `glitch_rgb` | RGB separation | `glitch` (0.0-1.0) |
| `neon_glow` | Glow intensity | `glow` (0.0-1.0) |
| `particle_emitter` | Particle density | `particles` (0.0-1.0) |
| `halftone_dots` | Dot frequency | `frequency` (0.0-100.0) |
| *... and 15 more* | *See BrushShader.kt* | *Various* |

---

## 📋 Quick Reference: Brush IDs

### Sketching
- `sketch_01` - Soft HB Pencil
- `sketch_02` - Rough Conté Crayon
- `sketch_03` - Mechanical Pencil
- `sketch_04` - Charcoal Stick
- `sketch_05` - Blending Stump
- `sketch_06` - Tinted Charcoal
- `sketch_07` - Graphite Powder
- `sketch_08` - Cross-Hatching Pen
- `sketch_09` - Kneaded Eraser
- `sketch_10` - Rough Sketch

### Painting
- `paint_01` - Thick Impasto
- `paint_02` - Diluted Watercolor
- `paint_03` - Flat Brush
- `paint_04` - Round Mop
- `paint_05` - Wet-on-Wet
- `paint_06` - Glazing Brush
- `paint_07` - Drybrush
- `paint_08` - Oil Blending
- `paint_09` - Stipple Brush
- `paint_10` - Sumi Watercolor

### Inking
- `ink_01` - Technical Fineliner
- `ink_02` - Sumi-e Ink
- `ink_03` - Comic Ink
- `ink_04` - Calligraphy Brush
- `ink_05` - Vector Ink
- `ink_06` - Ballpoint Pen
- `ink_07` - Liquid Ink
- `ink_08` - Brush Pen
- `ink_09` - Marker Ink
- `ink_10` - Felt Liner

### Textural
- `tex_01` - Distressed Concrete
- `tex_02` - Sponge Dab
- `tex_03` - Crackle Texture
- `tex_04` - Rust & Corrosion
- `tex_05` - Moss Growth
- `tex_06` - Sand Blast
- `tex_07` - Ink Splatter
- `tex_08` - Weathered Wood
- `tex_09` - Fabric Weave
- `tex_10` - Digital Noise

### Special Effects
- `fx_01` - Binary Bokeh
- `fx_02` - Geometric Scatter
- `fx_03` - Glitch Ribbon
- `fx_04` - Neon Vapor
- `fx_05` - Particle Stream
- `fx_06` - Halftone Dots
- `fx_07` - Lightning Spark
- `fx_08` - Chromatic Aberration
- `fx_09` - Starburst Rays
- `fx_10` - Smoke Wisp

---

## 🚀 Performance Considerations

### Shader Rendering (Android 13+)
- Shaders run on GPU via Skia engine
- Most performant for textured effects
- 25 shaders available for specialty brushes
- Use shader-based brushes for large strokes

### Non-Shader Brushes
- Faster for simple, uniform strokes
- Ideal for quick sketching and inking
- No GPU overhead

### Optimization Tips
1. Cache shader instances per brush
2. Reuse `BrushShaderFactory` instances
3. Update uniforms only when parameters change
4. Use appropriate size/opacity for performance vs. quality

---

## 📝 License & Attribution

These brush definitions are custom-designed for the Artist-Haven drawing application, implementing professional digital art brush concepts from traditional media simulation to digital-first effects.

---

**Last Updated:** 2024
**Total Brushes:** 50
**Shader Definitions:** 25+
**Supported API:** Android 13+ (API 33) for shader effects
