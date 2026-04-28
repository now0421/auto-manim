# GeoGebra Style Reference

Use this reference only for storyboard-level style planning. This stage does
not write code; all style language must stay compatible with later GeoGebra
command generation.

## Rules

* GeoGebra uses a white canvas by default.
* Use only the project-approved colors listed below.
* Do not use arbitrary hex colors, named colors outside this reference, CSS
  syntax, gradients, shadows, blur, glow, or browser-specific styling terms.
* Use concise, backend-friendly style properties.
* Prefer restyling an existing object over creating a visual duplicate on the
  same geometry.
* Prefer native labels for named geometric objects instead of separate duplicate
  text objects.
* Assign colors to concepts, not random objects.

## 1. Allowed Colors

Use only the following values in `color_scheme`, `color_palette`, object
`style`, or scene notes.

Background only:

* `WHITE`
* `#FFFFFF`

Foreground colors for the white GeoGebra canvas:

* `#111827` deep ink
* `#1F2937` charcoal
* `#0B3D91` navy
* `#1D4ED8` strong blue
* `#075985` deep sky blue
* `#0F766E` teal
* `#166534` forest green
* `#365314` olive green
* `#7F1D1D` dark red
* `#B91C1C` red
* `#9F1239` raspberry
* `#831843` plum
* `#581C87` violet
* `#6D28D9` purple
* `#7C2D12` burnt orange
* `#92400E` brown orange

Do not use yellow, gold, lime, cyan, aqua, light blue, light green, light
orange, light yellow, light purple, pink, silver, light gray, white foregrounds,
or other pale colors on the white canvas.

## 2. Safe Style Properties

Use concise key-value properties that can later map to GeoGebra styling
commands.

```text
color               // one allowed foreground color
line_thickness      // stronger or lighter stroke emphasis
line_style          // solid | dashed_long | dashed_short | dotted | dash_dot
point_size          // numeric point size
point_style         // dot | cross | empty_dot | plus | diamond | triangle
fill_opacity        // 0..1
label_visible       // true | false
caption             // short displayed caption
fixed               // true | false
selection_allowed   // true | false
tooltip_mode        // automatic | on | off | caption
layer               // 0..9
visible             // true | false
show_axes           // true | false
show_grid           // true | false
```

## 3. Storyboard Output Guidance

When writing storyboard JSON:

* `color_palette` must contain only values from this reference.
* `color_scheme` should describe semantic mapping, not implementation details.
* Object `style` should stay concise, structured, and construction-friendly.
* Prefer structured properties such as `color`, `line_style`, `fill_opacity`,
  and `label_visible` over free-form style prose.
* Use the same color for the same mathematical concept across scenes.
* `notes_for_codegen` may remind later stages to preserve helper visibility,
  grid/axes policy, label policy, palette consistency, and emphasis hierarchy.
