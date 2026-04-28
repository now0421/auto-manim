# Manim Style Reference

Use this reference only for storyboard-level style planning. This stage does
not write code; all style language must stay compatible with later Manim
generation.

## Rules

* Manim uses a black background by default.
* Use only the project-approved Manim color constants listed below.
* Do not use arbitrary hex colors, Manim color constants outside this reference,
  CSS-style syntax, gradients, shadows, blur, or unsupported rendering terms.
* Use concise, backend-friendly style properties.
* Prefer structured properties over vague prose.
* Treat style as part of teaching, not decoration alone.
* Assign colors to concepts, not random mobjects.
* Keep one clear visual focus per scene.
* Preserve intentional empty space; do not saturate the whole frame.

## 1. Allowed Color Constants

Use only names from this whitelist in `color_scheme`, `color_palette`, object
`style`, or scene notes.

Background and readability treatment:

* `BLACK`

Foreground colors for the black Manim canvas:

* `WHITE`
* `BLUE`
* `GREEN`
* `YELLOW`
* `RED`
* `PURPLE`
* `PINK`
* `ORANGE`
* `TEAL`
* `GOLD`
* `BLUE_A`
* `BLUE_B`
* `GREEN_A`
* `GREEN_B`
* `YELLOW_A`
* `YELLOW_B`
* `YELLOW_C`
* `RED_A`
* `RED_B`
* `PURPLE_A`
* `PURPLE_B`
* `TEAL_A`
* `TEAL_B`
* `GOLD_A`
* `GOLD_B`
* `GOLD_C`
* `MAROON_A`
* `MAROON_B`
* `LIGHT_PINK`
* `PURE_RED`
* `PURE_GREEN`
* `PURE_BLUE`
* `PURE_YELLOW`
* `PURE_CYAN`
* `PURE_MAGENTA`

Do not use dark or low-contrast foreground colors such as `DARK_BLUE`,
`DARK_GRAY`, `GRAY_E`, `BLUE_E`, `GREEN_E`, `PURPLE_E`, `MAROON_E`,
`LOGO_BLACK`, or custom hex colors.

## 2. Safe Style Properties

Use concise key-value properties that can later map to Manim styling code.

```text
color            // one allowed foreground color
fill_color       // one allowed foreground color
fill_opacity     // 0..1
stroke_color     // one allowed foreground color
stroke_width     // numeric stroke width
stroke_opacity   // 0..1
opacity          // overall opacity
scale            // relative size multiplier
font_size        // text size when needed
```

## 3. Style Planning Rules

These rules are for storyboard-level style planning and should remain compatible
with later Manim code generation.

### Semantic Palette

* `color_scheme` should map colors to roles or concepts, for example
  "moving point = GREEN_A, fixed anchors = RED_A/BLUE_A, result highlight =
  YELLOW_B".
* Keep the same concept in the same color across scenes.
* Prefer a small consistent palette over many unrelated colors.

### Opacity Hierarchy

Use opacity to direct attention:

* Primary focus: `opacity = 1.0`
* Context that should remain visible but secondary: around `0.35` to `0.5`
* Structural elements such as axes, helper grids, or reference baselines:
  around `0.12` to `0.2`

Do not keep every object at full opacity.

### Empty Space And Visual Weight

* Leave meaningful breathing room around the main focus.
* Keep at least a modest empty region for overlays, captions, or later reveals.
* Do not cluster all heavy content on one side unless comparison is the goal.
* Large opaque cards should sit in a clearly reserved area, not on top of the
  active geometry.

### Typography And Readability

* Prefer monospace fonts for storyboard text planning, for example `Menlo`,
  `JetBrains Mono`, `DejaVu Sans Mono`, or `Courier New`.
* Keep `font_size >= 18` for readable supporting text.
* Use larger sizes for scene titles, section headers, and key conclusions.
* If text must overlap busy geometry, plan a background box or backstroke-style
  readability treatment.

### Layout And Motion Vocabulary

* Prefer spatial consistency: the same concept should stay in the same screen
  region across scenes unless the move itself teaches something.
* Prefer progressive disclosure: show the simple state first, then add one new
  idea.
* Prefer transform- or restyle-based continuity over replacing everything.
* Use motion only when it clarifies change, causality, or comparison.

## 4. Storyboard Output Guidance

When writing storyboard JSON:

* `color_palette` must contain only whitelisted Manim color constants from this
  reference.
* `color_scheme` should describe semantic mapping, not implementation details or
  raw code.
* Object `style` should stay concise and implementation-friendly.
* Prefer structured properties such as `color`, `fill_opacity`, and
  `stroke_width` over free-form style prose.
* Use `opacity`, `fill_opacity`, and `stroke_opacity` deliberately to encode
  hierarchy.
* Use `font_size` to separate title, body, label, and caption roles.
* `notes_for_codegen` may remind later stages to preserve palette consistency,
  emphasis hierarchy, overlay safety, and text readability.
