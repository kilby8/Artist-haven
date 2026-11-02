# Artist Haven 2.0 design

## Goal and control

The goal is a drawing app James's wife prefers to Procreate and Adobe Fresco. The control for each
loop is the previous version (v1.4 for loop 1), and the reference bar is Procreate 5 and Fresco as
documented in their handbooks. Neither of them runs on Android, so on her device Artist Haven only
has to be the best drawing app she can actually use, but we score it honestly against both anyway.

Scoring criteria (1 to 10 each): brush quality and variety, drawing feel and speed, reliability
(saving, autosave, undo), layers, colour tools, workflow tools (eyedropper, fill, QuickShape,
symmetry, gestures), gallery and file management, export and sharing, UI clarity for a working
artist, and availability on her device.

## What changes from v1.4

v1.4 drew through Android `Canvas` with a preview path that differed from the committed path,
rebuilt the paper texture pixel by pixel every frame, kept layer pixels only in memory and showed
every brush as the same stamp. 2.0 replaces the drawing core with a pure Kotlin raster engine, in
the style of MyPaint and Krita, where the live stroke and the saved stroke are the same pixels.

## Modules

- `:engine` is pure Kotlin/JVM with no Android imports. It holds the document, tiles, brushes,
  stroke maths, compositing, fill, undo and file format. It is unit-tested on the JVM and renders
  test images to PNG so brush output can be inspected.
- `:app` is the Android app (Jetpack Compose). It owns input, the canvas view, threading,
  storage locations, export to the gallery and sharing, and all screens.

No Hilt, Room or KSP. Projects are plain files, so there is no database to migrate or wipe.

## Engine data model

- `Document`: width, height, paper colour, ordered `Layer` list, active layer, `History`.
- `Layer`: id, name, visible, opacity, `BlendMode`, alpha lock, clipping mask, and a `TiledSurface`.
- `TiledSurface`: 64x64 tiles of premultiplied ARGB `IntArray`; empty tiles are `null`, so blank
  layers cost no memory.
- `History`: undo and redo stacks of commands with a byte budget. Pixel edits store only the tiles
  they touched (before and after). Layer add, delete, move, merge and property changes are commands
  too, so every action, including Clear, can be undone.

## Brush engine

A brush is a `BrushPreset` with Procreate-style groups: stroke path (spacing, jitter, scatter),
streamline, taper, shape (procedural tip: round with hardness, pencil, charcoal, bristle, flat,
splatter), grain (procedural paper, canvas or noise texture; moving or texturised; scale; depth),
rendering (glaze or wet mix, flow, blend mode), wet mix (dilution, charge, pull, as in MyPaint's
smudge), dynamics (speed size/opacity, jitter), and pressure and tilt curves.

Stroke pipeline: input points (x, y, pressure, tilt, orientation, time) are smoothed by StreamLine,
interpolated with Catmull-Rom, and turned into dabs at the brush's spacing. Glaze brushes
accumulate dab coverage into a per-stroke buffer that is capped by stroke opacity and composited
over the layer's original tiles, which gives Procreate's even, non-streaky opacity. Wet brushes
paint dabs straight into the layer and pick up the colour beneath (smudge and wet mix). The eraser
uses the same coverage buffer to remove alpha.

## Tools

Brush, smudge, eraser, eyedropper (touch and hold), colour fill (drag-free tap fill with
tolerance on the layer or all layers), QuickShape (hold at the end of a stroke to snap to a line,
ellipse or rectangle), and symmetry (vertical, horizontal or quadrant).

## App

- Screens: Gallery (thumbnail grid, new canvas presets, rename, duplicate, delete with confirm) and
  Canvas.
- Canvas layout follows Procreate: top-left Gallery, Actions, undo and redo; top-right brush,
  smudge, eraser, layers and colour; left edge size and opacity sliders with the eyedropper.
- Gestures: two-finger tap undo, three-finger tap redo, pinch to zoom and rotate, touch and hold to
  pick colour. When a stylus has been used, fingers only navigate (palm rejection).
- Threading: a single engine thread owns the document. The view sends input there and pulls dirty
  rectangles of the composite into a document-sized bitmap, drawn with the zoom matrix.
- Saving: every edit marks the project dirty and autosaves two seconds later and when the app goes
  to the background. A project is a folder: `manifest.json`, one deflate-compressed tile file per
  layer, and `thumbnail.png`.
- Export: PNG, JPEG and layered PSD into `Pictures/Artist Haven` through MediaStore, plus share.

## Build and CI

Kotlin DSL only, Kotlin 2.2 with the Compose compiler plugin, compileSdk and targetSdk 35. CI builds
the debug APK, runs engine and app unit tests on every push and pull request, and uploads the APK
and test reports as artifacts. No fake APKs and no automatic releases.
