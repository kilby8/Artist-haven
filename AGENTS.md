# AGENTS.md

## Quick orientation
- Two Gradle modules, Kotlin DSL only: `:engine` (pure Kotlin/JVM, no Android) and `:app` (Android, Compose).
- `docs/DESIGN.md` is the design doc: goal, data model, brush engine, threading, file format.
- No Hilt, Room or KSP. Keep it that way unless there is a strong reason.

## Engine (`engine/src/main/kotlin/com/artisthaven/engine`)
- `raster/`: 64x64 tiled premultiplied ARGB surfaces (empty tiles are null), pixel maths.
- `document/`: `Document`, `Layer`, `BlendMode` (13 W3C modes), `Compositor` (outputs straight ARGB), `History` (tile before/after snapshots with a byte budget).
- `brush/`: `BrushPreset` + `BrushLibrary` (21 brushes), procedural tips and grain in `Textures`.
- `stroke/`: `StrokeSession` does smoothing (StreamLine), spacing, taper, dynamics, glaze, wet, wash and Mixer rendering, symmetry. `MixerState` is the paint the Mixer Brush carries between strokes (owned by `Editor`).
- `tools/`: flood fill, QuickShape recognition. `io/`: project folder format (`ProjectCodec`), layered PSD export (`PsdWriter`) and import (`PsdReader`), Photoshop brush import (`AbrReader`). Code ported from PhotoCraft is listed in `THIRD_PARTY_NOTICES.md`; keep its MIT notice.
- `Editor` is the single entry point; every mutating action returns a `Change` and pushes a `Command` so undo works.
- The engine is not thread safe. The app only touches it from the `EngineHost` thread.

## App (`app/src/main/kotlin/com/artisthaven/app`)
- `MainActivity` -> `App()` switches between `ui/GalleryScreen` and `ui/CanvasScreen`.
- `canvas/EngineHost` owns the `Editor` on a HandlerThread, composites dirty rects into a display buffer, and autosaves 1.5 s after edits (only changed layers are rewritten).
- `canvas/CanvasView` is a plain `View` for input and display (gestures, palm rejection, hover outline, colour loupe).
- `canvas/CanvasViewModel` holds tool state; `data/` has `ProjectStore` (projects in `filesDir/projects`, deletes go to `filesDir/trash` for 30 days), `Settings` and `Exporter`.

## Testing
- `./gradlew :engine:test` runs fast on any JVM and writes images to `engine/build/test-output`.
- `./gradlew :app:testDebugUnitTest` runs Robolectric tests, including `AppFlowTest`, which drives the real app and saves screenshots to `app/build/test-output`.
- CI (`.github/workflows/android-ci.yml`) runs engine tests, app tests, lint and `assembleDebug`, and uploads the APK and test output as artifacts.
- If you change drawing behaviour, look at the brush sheet and screenshots, not only the test results.
