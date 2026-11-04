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

## Loop log

Scores are from a separate rater agent that compared the study sheets and app screenshots with the control (control → new).

| Loop | Heavy paint | Paint feel | Blending | Variety | Watercolour | Speed | No regressions | What changed |
|---|---|---|---|---|---|---|---|---|
| 1 | 1 → 5 | 5 → 6 | 2 → 5 | 5 → 7 | 4 → 6 | 4 → 9 | 10 → 10 | Relief, wet-in-wet, lane smudge, 24 brushes, speed work |
| 2 | 6 | 6 | 5 | 7 | 6 | 9 | 10 | Pressure width, torn relief edges, varied rims, chalk and pastel fixes |
| 3 | 7 | 7 | 5 | 8 | 6 | 9 | 10 | Thinner light-pressure paint, softer relief, Hard Round, Fan Brush, Sponge |
| 4 | 7 | 7 | 7 | 8 | 6 | 9 | 10 | Smudge keeps coverage, real Soften blur, stronger bloom |
| 5 | 7 | 7 | 7 | 8 | 7 | 9 | 10 | Rims only on parts of the edge, broad pigment variation |

Open gaps (from the rater, loop 5):
- Wet-in-wet crossing still reads as a hard-edged overlap. Bloom should spread further and feather.
- Flat Wash shows fine stamp banding. Dry Watercolour keeps a full outline.
- Heavy Paint and Palette Knife still show fine ribbing and square stamp corners at the start of strokes.
- Smudging thick paint flattens its relief. A per-layer height map would be needed to keep it.
- Wet Blend and Finger look alike. Sponge repeats a visible pattern. Fan Brush tips are blurred.
- Wet-in-Wet costs about 8.4 ms per point. It is not yet measured on a real tablet.

Acceptance criteria: all 11 met. Evidence:
- `BrushLookTest` covers relief, the start square, blend streaks, the Blending category, rim variation and wet-in-wet.
- JS timings: Wet Wash 30.5 → about 7 ms per point; Watercolour Round pen-up 1247 → about 12 ms.
- The web end-to-end pen test passes.
- CI is green.

## Loop 6: watercolour that flows

James: "water color needs to spread better". Research: `/mnt/project-files/research/watercolor-spreading.md` (a simplified Curtis 1997 model).

- Washes now run a water simulation on a quarter-resolution grid (`WaterSim`). Water keeps moving after pen-up, runs only where the paper is wet or damp, carries pigment, and dries with a darker edge where it evaporates fastest.
- The web app and Android tick the water every frame until it is dry. Undo while wet takes back the stroke and its water.
- Tests (`WaterFlowTest`): wet-in-wet area 2501 at pen-up → 4097 after 2 s; Watercolour Round on dry paper 1188 → 1189; band edge 208 vs middle 135; red runs into a wet blue wash (rim 535) but not a dry one (1662).
- JS: Watercolour Round 3.2 ms per point, Wet-in-Wet 8.3, Wet Wash 12.8. Water ticks p50 4.3 ms, p95 62 ms on very large washes.

Open gaps: no Dry button in the UI yet; p95 tick on huge washes; Android water loop is checked only by CI; not yet tried on a real Surface.
