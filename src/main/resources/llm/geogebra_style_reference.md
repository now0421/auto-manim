# GeoGebra Style Reference

Use this reference only for storyboard-level style planning. This stage does not write code; all style language must stay compatible with later GeoGebra command generation.

## Rules

* Use only official GeoGebra color inputs.
* Use concise, backend-friendly style properties.
* Prefer restyling an existing object over creating a visual duplicate on the same geometry.
* Prefer native labels for named geometric objects instead of separate duplicate text objects.
* Avoid CSS-style syntax, gradients, shadows, blur, glow, and browser-specific styling terms.

## 1. Allowed Color Inputs

Use only the following safe inputs in `color_scheme`, `color_palette`, object `style`, or scene notes.

Named colors:

* `BLACK`
* `DARKGRAY`
* `GRAY`
* `DARKBLUE`
* `BLUE`
* `DARKGREEN`
* `GREEN`
* `MAROON`
* `CRIMSON`
* `RED`
* `MAGENTA`
* `INDIGO`
* `PURPLE`
* `BROWN`
* `ORANGE`
* `GOLD`
* `LIME`
* `CYAN`
* `TURQUOISE`
* `LIGHTBLUE`
* `AQUA`
* `SILVER`
* `LIGHTGRAY`
* `PINK`
* `VIOLET`
* `YELLOW`
* `LIGHTYELLOW`
* `LIGHTORANGE`
* `LIGHTVIOLET`
* `LIGHTPURPLE`
* `LIGHTGREEN`
* `WHITE`

Hex colors:

* `#RRGGBB`
* `#AARRGGBB`

## 2. Safe Style Properties

Use concise key-value properties that can later map to GeoGebra styling commands.

```text
color               // named color token or official hex color
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

* `color_palette` should contain only safe named colors or official hex strings.
* `color_scheme` should describe semantic mapping, not implementation details.
* Object `style` should stay concise, structured, and construction-friendly.
* Prefer structured properties such as `color`, `line_style`, `fill_opacity`, and `label_visible` over free-form style prose.
* `notes_for_codegen` may remind later stages to preserve helper visibility, grid/axes policy, label policy, and emphasis hierarchy.
