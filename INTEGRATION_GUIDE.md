# Brush Library Integration Guide

## Quick Start

### 1. Access a Brush
```kotlin
// Get a specific brush by ID
val pencilBrush = BrushLibrary.getBrushById("sketch_01")

// Get all brushes in a category
val paintingBrushes = BrushLibrary.getBrushesByCategory(BrushCategory.PAINTING)

// Get all 50 brushes
val allBrushes = BrushLibrary.allBrushes
```

### 2. Create Drawing Brush from Definition
```kotlin
val brushDef = BrushLibrary.getBrushById("paint_02")!!

val drawingBrush = Brush(
    type = BrushType.WATERCOLOR,  // map appropriately
    size = brushDef.defaultSize,
    opacity = brushDef.defaultOpacity,
    color = Color.Black,
    hardness = brushDef.defaultHardness,
    spacing = brushDef.spacing,
    textureStrength = if (brushDef.usesShader) 0.5f else 0f
)
```

### 3. Apply Pressure Sensitivity
```kotlin
fun applyPressure(brush: BrushDefinition, pressure: Float): Brush {
    return when (brush.pressureSensitivity) {
        PressureSensitivityMode.PRESSURE_TO_SIZE -> {
            val sizedBrush = Brush(
                size = brush.defaultSize * pressure,
                opacity = brush.defaultOpacity,
                // ...
            )
            sizedBrush
        }
        PressureSensitivityMode.PRESSURE_TO_OPACITY -> {
            Brush(
                size = brush.defaultSize,
                opacity = brush.defaultOpacity * pressure,
                // ...
            )
        }
        // ... handle other modes
    }
}
```

### 4. Render with Shader (Android 13+)
```kotlin
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun renderBrushStroke(
    brushDef: BrushDefinition,
    canvas: Canvas,
    paint: Paint,
    pressure: Float
) {
    if (brushDef.usesShader && brushDef.shaderId != null) {
        val shaderFactory = BrushShaderFactory()
        val shader = shaderFactory.createShaderForBrush(brushDef.shaderId)

        shader?.let {
            shaderFactory.updateShaderUniforms(
                shader = it,
                width = canvas.width.toFloat(),
                height = canvas.height.toFloat(),
                pressure = pressure,
                textureParam = brushDef.scatter  // or other params
            )
            paint.shader = it
        }
    }
}
```

---

## File Structure

```
artist-haven/
├── app/src/main/kotlin/com/artisthaven/app/
│   ├── domain/model/
│   │   ├── Brush.kt                 (Original - unchanged)
│   │   ├── BrushLibrary.kt          (NEW - 50 brush definitions)
│   │   ├── DrawingStroke.kt         (Original - unchanged)
│   │   └── ...
│   └── presentation/canvas/shaders/
│       └── BrushShader.kt           (EXTENDED - 25+ shaders)
├── BRUSH_LIBRARY.md                 (NEW - Documentation)
├── BRUSH_LIBRARY.json               (NEW - Data export)
└── ...
```

---

## Architecture Overview

### BrushDefinition Data Class
The core definition model containing:
- **Aesthetic Properties:** name, description, characteristics
- **Technical Parameters:** size, opacity, hardness, flow, spacing, scatter, jitter
- **Rendering:** blend mode, shader reference
- **Input Response:** pressure sensitivity mode

### BrushLibrary Singleton
Provides:
- `allBrushes` - List of 50 BrushDefinition objects
- `getBrushesByCategory(category)` - Filter brushes
- `getBrushById(id)` - Lookup specific brush
- `getAllShaderIds()` - Get available shader references

### BrushShaders Object
AGSL shader definitions:
- 3 core shaders (pencil, watercolor, charcoal)
- 25+ specialty shaders for advanced effects
- GPU-accelerated rendering (Android 13+)

### BrushShaderFactory
Factory for shader instantiation:
- `createShaderForBrush(shaderId)` - Create shader by ID
- `updateShaderUniforms(...)` - Update GPU uniforms

---

## Performance Optimization Tips

### 1. Cache Shader Instances
```kotlin
class BrushRenderer {
    private val shaderCache = mutableMapOf<String, RuntimeShader>()

    fun getShader(brushId: String): RuntimeShader? {
        return shaderCache.getOrPut(brushId) {
            val brushDef = BrushLibrary.getBrushById(brushId)
            BrushShaderFactory().createShaderForBrush(brushDef?.shaderId)
        }
    }
}
```

### 2. Batch Uniform Updates
```kotlin
// Update all active shaders in one pass
activeShaders.forEach { shader ->
    shaderFactory.updateShaderUniforms(
        shader,
        width, height,
        currentPressure,
        textureParam
    )
}
```

### 3. Lazy Load Categories
```kotlin
// Only load brushes for the current category
val visibleBrushes = BrushLibrary.getBrushesByCategory(
    selectedCategory
)
```

---

## Implementing in UI

### Brush Palette Composable
```kotlin
@Composable
fun BrushPalette(
    selectedCategory: BrushCategory,
    onBrushSelected: (BrushDefinition) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3)
    ) {
        items(
            BrushLibrary.getBrushesByCategory(selectedCategory)
        ) { brush ->
            BrushPaletteItem(
                brush = brush,
                onClick = { onBrushSelected(brush) }
            )
        }
    }
}

@Composable
fun BrushPaletteItem(
    brush: BrushDefinition,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        BrushPreviewCanvas(brush)
        Text(brush.name)
        Text(
            text = brush.coreCharacteristics,
            fontSize = 8.sp,
            color = Color.Gray
        )
    }
}
```

### Category Tabs
```kotlin
@Composable
fun BrushCategoryTabs() {
    TabRow(selectedTabIndex = selectedTabIndex) {
        BrushCategory.values().forEachIndexed { index, category ->
            Tab(
                selected = index == selectedTabIndex,
                onClick = { selectedTabIndex = index },
                text = { Text(category.name) },
                badge = {
                    Badge(
                        label = { 
                            Text(
                                BrushLibrary
                                    .getBrushesByCategory(category)
                                    .size
                                    .toString()
                            )
                        }
                    )
                }
            )
        }
    }
}
```

---

## Testing the Library

### Unit Test Example
```kotlin
@Test
fun testBrushLibrarySize() {
    assertEquals(50, BrushLibrary.allBrushes.size)
}

@Test
fun testCategoryDistribution() {
    val sketching = BrushLibrary.getBrushesByCategory(BrushCategory.SKETCHING)
    assertEquals(10, sketching.size)
}

@Test
fun testBrushIdUniqueness() {
    val ids = BrushLibrary.allBrushes.map { it.id }
    assertEquals(ids.size, ids.distinct().size)
}

@Test
fun testShaderReferenceValidity() {
    val allShaderIds = BrushLibrary.getAllShaderIds()
    BrushLibrary.allBrushes
        .filter { it.usesShader }
        .forEach { brush ->
            assert(
                allShaderIds.contains(brush.shaderId) || 
                brush.shaderId == null
            )
        }
}
```

---

## Migration from Existing Brushes

If you have existing brush implementations, map them to the new library:

```kotlin
// Old implementation
val oldPencil = BrushType.PENCIL

// New implementation
val newPencil = BrushLibrary.getBrushById("sketch_01")

// Adapter function
fun convertToNewBrush(oldType: BrushType): BrushDefinition? {
    return when (oldType) {
        BrushType.PENCIL -> BrushLibrary.getBrushById("sketch_01")
        BrushType.PEN -> BrushLibrary.getBrushById("ink_01")
        BrushType.MARKER -> BrushLibrary.getBrushById("ink_09")
        BrushType.WATERCOLOR -> BrushLibrary.getBrushById("paint_02")
        BrushType.CHARCOAL -> BrushLibrary.getBrushById("sketch_04")
        BrushType.ERASER -> BrushLibrary.getBrushById("sketch_09")
    }
}
```

---

## Customization

### Adding Custom Brushes
```kotlin
// Extend the library with custom definitions
val customBrush = BrushDefinition(
    id = "custom_01",
    name = "My Custom Brush",
    category = BrushCategory.SKETCHING,
    displayName = "My Custom Brush",
    description = "A custom brush I created",
    coreCharacteristics = "Unique texture",
    blendMode = BlendMode.Multiply,
    defaultSize = 12f,
    defaultOpacity = 0.7f,
    defaultHardness = 0.5f,
    defaultFlow = 0.8f,
    spacing = 0.15f,
    scatter = 3f,
    jitter = 1f,
    pressureSensitivity = PressureSensitivityMode.PRESSURE_TO_SIZE,
    usesShader = true,
    shaderId = "custom_texture"
)

// Add to a runtime collection
val runtimeBrushes = BrushLibrary.allBrushes.toMutableList()
runtimeBrushes.add(customBrush)
```

### Creating Custom Shaders
```kotlin
// Add to BrushShaders object
val CUSTOM_TEXTURE = """
    uniform float2 resolution;
    uniform float pressure;

    half4 main(float2 fragCoord) {
        // Your AGSL shader code here
        float2 uv = fragCoord / resolution;
        float dist = length(uv - 0.5) * 2.0;
        float alpha = 1.0 - smoothstep(0.0, 1.0, dist);
        return half4(0.0, 0.0, 0.0, alpha * pressure);
    }
""".trimIndent()

// Register in BrushShaderFactory
fun createCustomShader(): RuntimeShader =
    RuntimeShader(BrushShaders.CUSTOM_TEXTURE)
```

---

## References

- **BrushLibrary.kt** - Source definitions
- **BrushShader.kt** - AGSL implementations
- **BRUSH_LIBRARY.md** - Complete documentation
- **BRUSH_LIBRARY.json** - Data export
- **Google Ink API** - https://developer.android.com/training/ink/
- **Jetpack Graphics** - https://developer.android.com/jetpack/androidx/releases/graphics
- **AGSL Documentation** - Android Graphics Shading Language reference

---

## Next Steps

1. ✅ Review brush definitions in `BrushLibrary.kt`
2. ✅ Examine shaders in `BrushShader.kt`
3. ⬜ Integrate brushes into your UI layer
4. ⬜ Test with sample strokes
5. ⬜ Tune pressure sensitivity per device
6. ⬜ Benchmark shader performance
7. ⬜ Gather user feedback on brush feel

---

**Ready to paint!** 🎨
