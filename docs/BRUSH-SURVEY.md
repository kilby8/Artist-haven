# Brush survey: what other art apps offer, and what Artist Haven now has

Written 2026-11-05 for Grace's request to "10x the brushes, the brush adjustments and the pattern brushes".
It looks at kinds of brush and the settings behind them, not at any app's brush files or artwork.
Every brush in Artist Haven is built from our own engine settings and shapes generated in code.

## Apps surveyed

| App | Brush engine and settings worth knowing | Brush sets |
|---|---|---|
| **Procreate** | Brush Studio groups: stroke path (spacing, jitter, fall-off), stabilisation (StreamLine), taper, shape (scatter, rotation, count, randomise, flip), grain (moving or texturised), rendering (glazed, uniform, intense, heavy, wet blending), wet mix (dilution, charge, pull, grade), colour dynamics (stamp, stroke and pressure colour jitter), dynamics (speed, jitter), Apple Pencil (pressure, tilt, pressure curve), properties. | Sketching, Inking, Drawing, Calligraphy, Painting, Artistic, Airbrushing, Textures, Abstract, Charcoals, Elements, Spray paints, Touch-ups, Retro, Luminance, Industrial, Organic, Water, Vintage, Materials. |
| **Photoshop** | Brush Settings: tip shape (size, angle, roundness, hardness, spacing), shape dynamics (size, angle and roundness jitter, flip), scattering (both axes, count), texture (pattern, scale, depth, invert, mode), dual brush, colour dynamics (fore/background, hue, saturation, brightness, per tip), transfer (opacity and flow jitter), brush pose, noise, wet edges, build-up, smoothing. Mixer brush (wet, load, mix). | General, Dry media, Wet media, Special effects, Kyle Webster sets. |
| **Krita** | Many engines: pixel, colour smudge, bristle, sketch, particle, spray, grid, hatching, shape, curve, deform, clone, filter, dyna, tangent normal. Every setting can be driven by sensors: pressure, X/Y tilt, speed, drawing angle, rotation, distance, time, fade, random. | Basic, Pencils, Ink, Paint, Blenders, Stamps and textures, Pixel art, Erasers. |
| **Clip Studio Paint** | Brush tip material (several tips per brush, picked in order or at random), texture, spray (density, particle size), ribbon mode, colour jitter, watercolour edge, blending (running colour, colour stretch), anti-aliasing, correction. | Pencil, Pen, Marker, Brush (watercolour, thick paint, India ink), Airbrush, Decoration (patterns, foliage, lace, ribbons, clouds, crowds), Blend. |
| **Adobe Fresco** | Live brushes (watercolour and oil that keep flowing, wet layer, dry button), pixel brushes from Photoshop, vector brushes. | Live watercolour, Live oil, Pixel, Vector. |
| **Rebelle** | Physical wet layer with tilt, water and paint amount, drying, blow, canvas grain. | Watercolour, Acrylic, Oil, Gouache, Pastel, Ink pen, Marker, Pencil, Airbrush. |
| **ArtRage** | Oil with loading and thinners, watercolour, palette knife, glitter tube, roller, gloop pen, felt pen, sticker spray, stencils. | One tool per medium with presets. |
| **Corel Painter** | Particle brushes (flow, spring, gravity), dynamic speckle, image hose (stamp lists), pattern pens, thick paint, real watercolour, F-X. | Over 900 variants across ~40 categories. |
| **Others** (Sketchbook, Infinite Painter, MediBang, ibisPaint, SAI, MyPaint) | Rakes, smears, synthetic and wet brushes, pattern and decoration brushes, MyPaint's smudge and dab model. | Mostly the same families as above. |

## The families every app has

1. Pencils and graphite (H to B grades, coloured pencil, shading on the side)
2. Inking pens (technical, brush pen, dip pen, ballpoint, felt tip, comic inker, stipple)
3. Calligraphy (chisel, italic, pointed nib, brush lettering, parallel pen)
4. Paint brushes (round, flat, filbert, fan, rigger, knife, dry brush, impasto, glazing, mixer)
5. Blenders (soft, bristle, textured, wet, finger, stump or tortillon, blur)
6. Watercolour (washes, wet in wet, dry brush, granulating, spatter)
7. Dry media (charcoal vine and compressed, pastel hard and soft, conté, crayon, chalk)
8. Airbrushes (soft, medium, hard, flat, speckle)
9. Spray paint (spray can, fat cap, mist, splatter, toothbrush flick)
10. Textures (sponge, stipple, grunge, canvas, concrete, burlap)
11. **Pattern brushes**: a repeating pattern painted inside the stroke (halftone, stripes, checks, bricks, scales, plaid and so on)
12. **Stamp brushes**: one shape per dab (stars, hearts, confetti, flowers, dots)
13. Nature (grass, foliage, leaves, petals, bark, moss, pebbles)
14. Hair and fur (fine strands, rakes, whiskers)
15. Elements (clouds, smoke, rain, snow, fire, water, bubbles, stars)
16. Light (glow, neon, bokeh, sparkle, glitter)
17. Retro and comic (screen tone, hatching, pixel, risograph)
18. Abstract (colour noise, ribbons, scribbles, geometric scatter)

## What Artist Haven had, and what it has now

Before: 47 brushes in 8 categories, and no way to change a brush in the web app.

Now: about 200 brushes in 20 categories, plus a Brush Studio in the web app with over 60 settings.

New engine settings (they work in the web app and on Android):

| Setting | Like |
|---|---|
| Scatter and dabs per step | Photoshop scattering and count, Procreate scatter and count |
| Squash jitter, mirror at random | Photoshop roundness jitter and flip, Procreate randomised and flipped shape |
| Hue, saturation and brightness jitter, per dab or per stroke | Photoshop colour dynamics, Procreate colour dynamics |
| Build-up | Photoshop build-up, Procreate's intense rendering for stamps |
| Invert texture, pattern textures that stay crisp at any pressure | Photoshop texture with invert |
| 20 stamp tips: square, triangle, star, sparkle, heart, leaf, flower, foliage, grass, hair strands, rake, cloud, snowflake, halftone dots, stipple, ring, bubble, hatching, cross, crescent | Procreate shape sources, Clip Studio tip materials, Painter image hose |
| 19 patterns: halftone, dot screen, polka dots, stripes, checkerboard, crosshatch, brick, honeycomb, scales, waves, chevron, plaid, denim, wood grain, marble, crackle, leopard, little stars, TV static | Photoshop pattern textures, Clip Studio decoration brushes |

Settings the Brush Studio shows: size range, opacity, flow, tip, hardness, roundness, angle and what it follows,
spacing, StreamLine, taper (start, end, tip size), scatter, count, size, opacity, rotation, squash and mirror jitter,
wobble, hue, saturation and brightness jitter, texture, depth, scale, paper tooth, dust, moving or fixed texture,
invert, paint behaviour (glaze, wet, watercolour, mixer), blend mode, build-up, pressure curve, pressure to size,
opacity and flow, tilt to size and opacity, speed to size and opacity, wet paint (load, dilution, pull, dry-out,
bristle colour, bristle shading, relief), watercolour (dark edges, granulation, pigment flow, ragged edge), mixer
(wet, load, mix, sample all layers, clean), and smudging (blender, soften, smudge strength).

## Not done yet

- Dual brush (a second tip cutting into the first) and tip lists that change from dab to dab.
- A pressure curve you drag by hand; today it is one slider from soft to firm.
- Pressure or tilt driving scatter, colour or rotation (Krita sensors).
- Ribbon and image-hose brushes that draw a picture along the path.
- Importing Photoshop `.abr` brushes in the web app (Android already can).
