# AGENTS.md

## Quick orientation
- Primary app entrypoint is `app/src/main/kotlin/com/artisthaven/app/MainActivity.kt`; `app/src/main/AndroidManifest.xml` points at `.MainActivity` and `.ArtistHavenApplication`.
- Treat `app/src/main/kotlin/com/artisthaven/app/**` as the current product path: Compose UI, Hilt DI, Room persistence, and the active drawing screen.
- `app/src/main/java/com/artisthaven/drawing/**` plus `app/src/main/res/layout/activity_main.xml` is a separate legacy/experimental SurfaceView renderer (`CustomDrawingCanvas`). It is not referenced by the manifest, so do not assume edits there affect the shipped flow.
- The repo contains duplicate build definitions (`build.gradle` + `build.gradle.kts`, `settings.gradle` + `settings.gradle.kts`, `app/build.gradle` + `app/build.gradle.kts`). If you change build logic, keep both in sync unless you intentionally consolidate them.

## Architecture and data flow
- UI flow is `MainActivity` -> `DrawingScreen` -> `AndroidView(DrawingCanvasView)` -> `CanvasViewModel`.
- `DrawingCanvasView` owns input capture: it applies touch-slop filtering, reads historical `MotionEvent` samples, renders a live preview bitmap, and emits a finished `DrawingStroke` through `onStrokeCommitted`.
- `CanvasViewModel.commitStroke()` wraps each stroke in a `StrokeCommand` and executes it through the singleton `CommandHistory` provided in `di/AppModule.kt`.
- Undo/redo is command-based, not snapshot-based at the screen level. If you add a new mutating canvas action, model it as a `DrawingCommand` so `canUndo` / `canRedo` stay correct.
- Layer composition is bitmap-based and in-memory: `CanvasViewModel.layerBitmaps` is keyed by layer id, and both `DrawingScreen` and `mergeLayers()` sort visible layers by `Layer.order` before compositing.
- Persistence is metadata-first. Room stores `ProjectEntity` and `LayerEntity`, but stroke pixels live in memory during editing; the only verified bitmap file write today is export in `ProjectRepositoryImpl.exportAsPng()` to `filesDir/exports/<projectId>/...`.
- Hilt wiring lives in `app/src/main/kotlin/com/artisthaven/app/di/AppModule.kt`: `ProjectRepository` binds to `ProjectRepositoryImpl`, `AppDatabase` is a singleton, and `CommandHistory(maxSize = 50)` is shared app-wide.
- Room setup is in `data/local/AppDatabase.kt`; note `fallbackToDestructiveMigration()` and `exportSchema = false` (there is no `app/schemas/` directory).

## Project-specific conventions
- Package boundaries are meaningful here: `presentation` = Compose/View UI, `domain` = models/commands/repository API, `data` = Room + repository implementation, `di` = Hilt modules.
- Brush defaults live in `domain/model/Brush.kt` inside `BrushType`; update defaults there instead of scattering size/opacity constants through UI code.
- Android 13+ shader behavior is explicitly gated in `presentation/canvas/DrawingCanvasView.kt` through `BrushShaderFactory`; keep the non-AGSL fallback path working for lower API levels.
- `Project` and `Layer` model comments mention stored bitmaps, but the active flow currently recreates layer bitmaps in `CanvasViewModel` on size availability. Be careful not to assume full raster persistence already exists.
- If you touch layer operations, preserve `Layer.order` semantics and the “at least one layer remains” rule in `CanvasViewModel.deleteLayer()`.
- The legacy engine under `java/com/artisthaven/drawing` uses a very different architecture (`SurfaceControlCompat`, AGSL shader pool, `TiledRenderer`, render thread). Changes there should be isolated and deliberate.

## Developer workflow
- On Windows, use the wrapper from repo root: `./gradlew.bat <task>`.
- Tasks implied by the module setup are `assembleDebug`, `testDebugUnitTest`, `lintDebug`, and `installDebug` for `:app`.
- Local verification is currently blocked by a corrupted user Gradle cache at `C:/Users/carpe/.gradle/caches/journal-1/file-access.bin`; `./gradlew.bat tasks --all --console=plain` fails before task listing completes.
- Current automated tests are unit tests under `app/src/test/kotlin`, especially `domain/command/CommandHistoryTest.kt`; there is no `app/src/androidTest/` tree right now.
- If a change affects drawing behavior, inspect both `presentation/canvas/*` and `java/com/artisthaven/drawing/*` before concluding there is only one rendering path in the repo.

