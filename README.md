# Artist Haven

A drawing and painting app for Android phones and tablets, built to feel like Procreate and Adobe Fresco.

- 18 brushes across sketching, inking, painting, watercolour, artistic, airbrush and texture sets, with pressure, tilt and speed response and wet paint that mixes on the canvas
- Layers with 13 blend modes, opacity, alpha lock and clipping masks
- StreamLine smoothing, QuickShape (hold at the end of a line), symmetry, flood fill
- Gestures: two-finger tap to undo, three-finger tap to redo, pinch to zoom and rotate, touch and hold to pick a colour
- Palm rejection with a stylus, and the pen's eraser end erases
- Autosave, a gallery with thumbnails, and export to PNG, JPEG or layered PSD

## Get the app

Every push builds a debug APK in GitHub Actions: open the latest **Android CI** run and download the `artist-haven-debug-apk` artifact.

## Build

```
./gradlew :engine:test :app:testDebugUnitTest :app:assembleDebug
```

Needs JDK 17 and the Android SDK (platform 35). See `docs/DESIGN.md` for how it works.
