# Brushes round 2: Grace's feedback

Grace tried the Surface Pro web app (2026-11-04) and said:

1. Heavy paintbrush
2. Blend needs variation
3. More brushes
4. Watercolour needs work

James wasn't sure what she meant by "heavy paintbrush", so all three readings are covered: paint brushes
that feel slow (lag), paint that looks too thick, and the lack of a thick, heavy-body brush.

## Control

The brushes on main at 3102a11 (21 brushes). Study sheets from `BrushLookTest` and JS timings from
the web engine (Node, 2736 x 1824 canvas, 200-point stroke) are kept as the control.

| Brush | JS stroke ms | JS pen-up ms |
|---|---|---|
| Oil Paint | 528 | 4 |
| Round Brush | 129 | 97 |
| Watercolour Round | 863 | 814 |
| Wet Wash | 4396 | 19 |

What is wrong with the control, from its sheets:
- Oil Paint reads as a comb dragged through paint: even, mechanical streaks, no body, and a pale
  square at the start of every stroke (the first dab is drawn before the stroke has a direction).
- No brush has real relief. "Impasto" only shades bristle lanes, so nothing looks thick.
- Smudge has one look: every blender smears one flat average colour.
- Watercolour Round is a vector ribbon: the same hard dark outline everywhere, the same density
  from start to end, a perfect pill shape. Wet Wash noise is fine digital speckle, not pigment.
  Strokes never bleed into each other while wet.
- Watercolour is slow in the browser: Wet Wash costs 22 ms per input point, and Watercolour Round
  replays the whole stroke at pen-up (0.8 s).

## Design

Engine (all in `:engine` commonMain, so Android gets it too):

- **Speed.** Wash shading caches each pixel's wetness once per pass instead of computing it 17 times.
  Pen strokes on wet, wash and mixer brushes no longer replay the whole stroke at pen-up for the end
  taper: the pen's own pressure already tapers the end, so the replay only runs for mouse and finger.
- **Start artefact.** Flat (bristle) tips skip the very first dab, which is drawn before the stroke has
  a direction.
- **Relief (heavy paint).** New `relief` brush setting. A relief brush paints into a per-stroke base
  buffer and a height map: bristle grooves, paint pushed up into ridges along both edges of the stroke,
  and a blob where the stroke starts. A shading pass lights the height map from the top left
  (highlight on ridges facing the light, shadow on the far side) and writes the lit paint to the layer.
- **Bristle blending.** The smudge tool with a bristle tip carries a separate colour per bristle lane,
  so blending drags streaks of each colour instead of one flat average.
- **Watercolour.**
  - Pigment changes along the stroke: it pools where the brush slows and at the start, and thins as the
    brush runs out of water.
  - The dark rim is narrower and varies along the edge.
  - The outline wanders on a larger scale, so strokes stop looking like pills.
  - Granulation is clumpy, not speckled.
  - Wet-in-wet: the editor keeps a drying map of recent washes (about 15 seconds). A new wash that
    touches a still-wet one bleeds into it with soft edges and forms no rim there.
- **New brushes** (21 to about 40), including a new Blending category so the smudge tool has real choices.

Web app: the smudge tool defaults to a blending brush, and the brush library opens on Blending for
smudge.

How this beats the control: every point in the list above is visible on the study sheets or in the timings.

## Scorecard (1 to 10)

1. Heavy paint: is there a brush that looks like thick paint with body and relief?
2. Paint feel: oil, gouache and round brush look like paint, with no artefacts, and go thin under
   light pressure.
3. Blending: are there several blenders that each look different, with streaky and textured options?
4. Variety: are there enough brushes, each clearly different, covering what a painter reaches for?
5. Watercolour: rims vary, pigment varies, the shape is organic, and wet-in-wet works.
6. Speed in the browser: the per-point and pen-up costs stay under a frame.
7. No regressions: pencils, inks, charcoal and the rest look the same or better.

## Acceptance criteria

1. Heavy Paint shows lit ridges along both stroke edges and bristle grooves on the study sheet.
2. Oil Paint and Gouache strokes have no pale square at the start.
3. Wet Wash costs under 8 ms per input point in JS (control: 22 ms).
4. Watercolour Round pen-up with a pen costs under 50 ms in JS (control: 814 ms).
5. Bristle Blend over two colours leaves visible streaks of both colours (a test measures colour
   variance across the stroke).
6. The Blending category has at least 5 brushes, and the web smudge tool defaults to one of them.
7. At least 15 new brushes, each with a study sheet that looks distinct.
8. Watercolour: a second wash laid over a wet one within 5 s has no dark rim inside the first wash
   (a test compares the rim with the dry case).
9. Watercolour Round's rim darkness varies along the edge (a test checks its spread).
10. All existing engine tests still pass, and the Android CI build is green.
11. The web app runs the new brushes: the brush library shows them and an end-to-end pen test passes.
