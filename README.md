# Artist Haven

A drawing and painting app for Android phones and tablets, built to feel like Procreate and Adobe Fresco.

- 21 brushes across sketching, inking, painting, watercolour, artistic, airbrush and texture sets, with pressure, tilt and speed response and wet paint that mixes on the canvas
- Layers with 13 blend modes, opacity, alpha lock and clipping masks
- StreamLine smoothing, QuickShape (hold at the end of a line), symmetry, flood fill
- Gestures: two-finger tap to undo, three-finger tap to redo, pinch to zoom and rotate, touch and hold to pick a colour
- Palm rejection with a stylus, and the pen's eraser end erases
- Mixer Brush: paint that picks up the colours it drags through and runs dry, plus a Blender for soft blends on their own layer
- Import Photoshop brushes (.abr) into My brushes, and open layered PSD files from the gallery
- Autosave, a gallery with thumbnails, and export to PNG, JPEG or layered PSD

Some file and paint code is ported from PhotoCraft (MIT); see `THIRD_PARTY_NOTICES.md`.

## Get the app

Every push builds a debug APK in GitHub Actions: open the latest **Android CI** run and download the `artist-haven-debug-apk` artifact.

## Build

```
./gradlew :engine:test :app:testDebugUnitTest :app:assembleDebug
```

Needs JDK 17 and the Android SDK (platform 35). See `docs/DESIGN.md` for how it works.
