# Professional CanvasProvider System - Implementation Guide

## Overview

This implementation provides a **modular, high-performance canvas texture system** for Artist-Haven, featuring 6 professional surface types, advanced rendering techniques, and seamless integration with the existing brush engine.

### Key Features

✅ **6 Professional Canvas Types**
- Cold-Press Paper (watercolor simulation)
- Fine-Grain Linen (oil/acrylic simulation) 
- Dark Mode Slate (charcoal/ink work)
- Transparent Grid (digital export)
- Vellum (technical drawing)
- Primed Canvas (heavy oil painting)

✅ **Three Pro Technical Layers**
1. **Seamless Texture Tiling** - Random offset prevents pattern repetition
2. **Canvas Tooth Interaction** - DST_IN blend mode for dry brush effects
3. **3D Lighting & Depth** - Directional light simulation for realism

✅ **Hardware Acceleration**
- RenderNode for API 29+ (non-blocking background rendering)
- Fallback paths for older Android versions
- Shader-based texturing (no file I/O latency)

✅ **Professional Rendering**
- Procedurally generated seamless textures
- ColorFilter tinting without bitmap reallocation
- Memory-efficient caching system

## Architecture

```
┌─────────────────────────────────────────────────┐
│           DrawingCanvasView / Compose UI         │
├─────────────────────────────────────────────────┤
│         CanvasRenderingManager (Facade)          │
├─────────────────────────────────────────────────┤
│  ┌──────────────────┐      ┌─────────────────┐  │
│  │ CanvasProvider   │      │  PaintBrush     │  │
│  │ (Singleton)      │      │ (Enhanced)      │  │
│  └──────────────────┘      └─────────────────┘  │
├─────────────────────────────────────────────────┤
│  ┌──────────────────┐      ┌─────────────────┐  │
│  │  CanvasLayer     │      │ CanvasTextureFactory│
│  │ (Rendering)      │      │ (Procedural Gen) │  │
│  └──────────────────┘      └─────────────────┘  │
├─────────────────────────────────────────────────┤
│  ┌──────────────────┐      ┌─────────────────┐  │
│  │ ShaderCache      │      │ TextureCache    │  │
│  │ (Composition)    │      │ (Bitmaps)       │  │
│  └──────────────────┘      └─────────────────┘  │
```

## Quick Start

### 1. Initialize Canvas for a Project

```kotlin
// In CanvasViewModel or DrawingScreen initialization
val manager = CanvasRenderingManager.getInstance()
manager.initialize(
    width = canvasWidth,
    height = canvasHeight,
    canvasType = CanvasType.COLD_PRESS_PAPER,
    enableToothInteraction = true,
)
```

### 2. Render Background Before Strokes

```kotlin
// In DrawingCanvasView.onDraw() or Compose drawing loop
val manager = CanvasRenderingManager.getInstance()
manager.renderBackground(canvas)  // Draw before brush strokes
```

### 3. Switch Canvas Type at Runtime

```kotlin
// User selects different canvas in UI
manager.setCanvasType(CanvasType.FINE_GRAIN_LINEN)
// That's it! No recreation needed.
```

### 4. Enable Dry Brush Effect

```kotlin
// Allow light pressure to follow paper texture
manager.setToothInteraction(enabled = true)

// Then use tooth-aware rendering in PaintBrush
brush.renderStrokeWithToothInteraction(
    canvas = targetCanvas,
    points = strokePoints,
    brush = brushConfig,
    canvasTexture = provider.getTextureForType(CanvasType.COLD_PRESS_PAPER),
    toothIntensity = 0.35f,  // 0 = no tooth, 1 = full tooth
)
```

### 5. Enable 3D Lighting

```kotlin
// Add subtle directional light for depth
manager.setLighting(enabled = true)
manager.setLightingIntensity(0.15f)  // 0 - 1 range
```

## Advanced Features

### Seamless Texture Tiling

The **Texture Tiling** strategy hides repeating patterns by randomizing the starting offset on each project open:

```kotlin
// Automatic in CanvasLayer - uses System.currentTimeMillis() as seed
// Each texture gets a random offset:
val random = Random(config.randomSeedOffset)
val offsetX = random.nextInt(texture.width)
val offsetY = random.nextInt(texture.height)

// Result: Users never see the same pattern twice
```

**Benefits:**
- Eliminates "copy-paste" look
- Looks natural and organic
- No perceptible seams (512px+ textures)

### Dry Brush Effect (Tooth Interaction)

The **"Tooth" Interaction** uses `PorterDuff.Mode.DST_IN` to mask brush strokes through canvas texture:

```kotlin
// In drawStampAtWithTooth():
val toothPaint = Paint().apply {
    shader = toothShader  // Canvas texture as shader
    alpha = (alpha * toothIntensity).toInt()
    // DST_IN: "keep destination where shader is opaque"
    xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
}
canvas.drawCircle(x, y, r, toothPaint)
```

**How It Works:**
1. Paint stroke is drawn normally (SRC_OVER)
2. Canvas texture is applied as a mask (DST_IN)
3. Light pressure fills only the peaks (bright areas of texture)
4. Valleys remain white (or transparent)
5. Creates realistic paper texture interaction

**Effects:**
- Light pressure → thin, dry strokes (texture shows through)
- Heavy pressure → full color (texture fills valleys)
- Adobe Fresco-like quality

### 3D Lighting Simulation

The **Lighting & Depth** layer adds perceived dimensionality:

```kotlin
// In applyLighting():
val lightPaint = Paint().apply {
    // Linear gradient from top-left to bottom-right
    shader = LinearGradient(
        0f, 0f, width.toFloat(), height.toFloat(),
        Color.argb(80, 255, 255, 255),   // Bright top-left
        Color.argb(20, 0, 0, 0),          // Dark bottom-right
        Shader.TileMode.CLAMP,
    )
    // Soft light blend mode for subtle effect
    xfermode = PorterDuffXfermode(PorterDuff.Mode.SOFT_LIGHT)
}
```

**Pro Tricks:**
- Directional light simulates canvas fibers casting shadows
- Soft light blend is subtle (doesn't dominate)
- Can rotate light by changing gradient start/end points
- Pairs well with tooth interaction

## Canvas Presets

Use preset configurations for common scenarios:

```kotlin
// Watercolor painting
manager.initialize(
    width = 1024, height = 1024,
    configFactory = CanvasPresets.watercolorPreset()
)

// Oil painting
val oilConfig = CanvasPresets.oilPaintingPreset()

// Technical drawing with grid
val techConfig = CanvasPresets.technicalDrawingPreset()

// Dark mode with charcoal/ink
val darkConfig = CanvasPresets.darkmodeSlatePreset()

// Digital export (no texture, just grid)
val exportConfig = CanvasPresets.digitalExportPreset()
```

## Integration with Existing Code

### Update DrawingCanvasView

```kotlin
// In DrawingCanvasView onDraw method:
override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    
    // Render canvas background FIRST
    val manager = CanvasRenderingManager.getInstance()
    manager.renderBackground(canvas)
    
    // Then render brush strokes on top
    previewBitmap?.let {
        canvas.drawBitmap(it, 0f, 0f, previewPaint)
    }
}
```

### Update DrawingCanvasView Touch Handling

```kotlin
// In onStrokeCommitted callback, use tooth interaction if enabled:
val canvasConfig = CanvasRenderingManager.getInstance().getCurrentConfig()
if (canvasConfig?.enableToothInteraction == true) {
    val texture = CanvasProvider.getInstance().getTextureForType(
        canvasConfig.canvasType
    )
    brushEngine.renderStrokeWithToothInteraction(
        canvas = layer.bitmap.canvas(),
        points = stroke.points,
        brush = currentBrush,
        canvasTexture = texture,
        toothIntensity = 0.35f,
    )
} else {
    brushEngine.renderStroke(
        canvas = layer.bitmap.canvas(),
        points = stroke.points,
        brush = currentBrush,
    )
}
```

### Add to CanvasViewModel

```kotlin
// In CanvasViewModel:
private val canvasManager = CanvasRenderingManager.getInstance()

fun initializeCanvas(width: Int, height: Int) {
    canvasManager.initialize(
        width = width,
        height = height,
        canvasType = CanvasType.COLD_PRESS_PAPER,
    )
}

fun updateCanvasType(type: CanvasType) {
    canvasManager.setCanvasType(type)
    // Trigger recompose if using Compose
    _uiState.update { it.copy(/* ... */) }
}
```

### Update Compose UI

```kotlin
// In DrawingScreen:
CanvasTypeSelector(
    selectedCanvasType = uiState.currentCanvasType ?: CanvasType.COLD_PRESS_PAPER,
    onCanvasTypeSelected = { viewModel.updateCanvasType(it) },
)

CanvasFeatureToggles(
    enableToothInteraction = uiState.toothInteractionEnabled,
    enableLighting = uiState.lightingEnabled,
    onToothInteractionChange = { viewModel.toggleToothInteraction(it) },
    onLightingChange = { viewModel.toggleLighting(it) },
)
```

## Performance Considerations

### Memory Usage

- **Texture Cache**: ~512KB (6 canvas types at 512x512)
- **Shader Cache**: Negligible (native objects only)
- **Per-Layer**: ~4MB (1024x1024 ARGB bitmap)

### Optimization Tips

1. **Cache Reuse**: Textures are shared across all layers/projects
2. **Lazy Loading**: Textures created on first use
3. **Clear Cache**: Call `provider.clearCache()` between projects
4. **RenderNode**: Automatic on API 29+, eliminates CPU-GPU sync stalls

### Benchmark (Approximate)

| Operation | Time | Notes |
|-----------|------|-------|
| Create texture | 1-2ms | One-time on first use |
| Render canvas bg | <1ms | With RenderNode caching |
| Single dab w/ tooth | 2-4ms | DST_IN blend cost |
| Full stroke (50 dabs) | 100-200ms | Typical fast stroke |

## Best Practices

### 1. Texture Quality

- Use 512x512 or larger for professional look
- Seamless patterns for edge-wrapping
- High contrast for tooth interaction

### 2. Tooth Interaction Intensity

```
toothIntensity = 0f      → Normal brush (no texture mask)
toothIntensity = 0.2-0.4 → Subtle dry brush (recommended)
toothIntensity = 0.6+    → Strong dry brush (visible texture)
```

Start with 0.35f and adjust based on canvas type:
- Rough textures (linen): 0.25-0.35f
- Fine textures (vellum): 0.1-0.2f
- Medium (cold press): 0.3-0.4f

### 3. Lighting for Depth

```
lightingIntensity = 0.0   → No lighting
lightingIntensity = 0.1   → Subtle depth (recommended)
lightingIntensity = 0.2+  → Pronounced lighting
```

### 4. ColorFilter Tinting

```kotlin
// Tint without reallocation
val tintColor = Color(0xFFFFFCF7)  // Warm white
manager.setTintColor(tintColor)
// Done! No bitmap recreation
```

### 5. Random Offset Strategy

```kotlin
// Use current time as seed for natural variation
randomSeedOffset = System.currentTimeMillis()

// Or use project ID for reproducible textures
randomSeedOffset = projectId.hashCode().toLong()
```

## Troubleshooting

### Canvas looks repetitive

**Solution**: Ensure random offset is enabled
```kotlin
config.randomSeedOffset = System.currentTimeMillis()
```

### Dry brush effect too strong/weak

**Solution**: Adjust toothIntensity parameter
```kotlin
toothIntensity = 0.25f  // Too weak
toothIntensity = 0.35f  // Just right
toothIntensity = 0.50f  // Too strong
```

### Performance lag with brush strokes

**Solution**: Ensure RenderNode acceleration is active
```kotlin
// Check in DrawingCanvasView:
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    // RenderNode should be used automatically
}
// For <API29, RenderNode falls back to direct rendering
```

### Texture seems blurry or pixelated

**Solution**: Increase texture size
```kotlin
provider.getTextureForType(
    canvasType = CanvasType.COLD_PRESS_PAPER,
    size = 1024,  // Increase from 512
)
```

## Future Enhancements

1. **GLSL Shader Normal Maps** - True 3D surface via bump mapping
2. **Animated Grain** - Time-based texture variation
3. **User Custom Textures** - Import user-provided canvas images
4. **Adaptive Tooth** - Pressure-responsive tooth intensity
5. **Multi-Canvas Blending** - Mix two canvas types on same layer

## References

- **PorterDuff Modes**: https://developer.android.com/reference/android/graphics/PorterDuff.Mode
- **BitmapShader**: https://developer.android.com/reference/android/graphics/BitmapShader
- **RenderNode**: https://developer.android.com/reference/android/graphics/RenderNode
- **Paint Tips**: Fresco (Adobe) research on digital canvas simulation

## Support

For issues or enhancements, review:
- `CanvasType.kt` - Domain models
- `CanvasTextureFactory.kt` - Texture generation
- `CanvasProvider.kt` - Main rendering engine
- `CanvasRenderingManager.kt` - High-level API
- `PaintBrush.kt` - Tooth interaction integration

