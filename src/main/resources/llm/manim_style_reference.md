# Manim Style Reference

Use this reference only for storyboard-level style planning. This stage does not write code; all style language must stay compatible with later Manim generation.

## Rules

* Use only whitelisted Manim color constants.
* Use concise, backend-friendly style properties.
* Prefer structured properties over vague prose.
* Avoid CSS-style syntax, hex colors, gradients, shadows, blur, and unsupported rendering terms.

## 1. Allowed Color Constants

Use only names from this whitelist in `color_scheme`, `color_palette`, object `style`, or scene notes.

Base colors:

* `BLACK`
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
* `MAROON`
* `GRAY`
* `GREY`
* `DARK_BLUE`
* `DARK_BROWN`
* `DARK_GRAY`
* `DARK_GREY`
* `DARKER_GRAY`
* `DARKER_GREY`
* `LIGHT_BROWN`
* `LIGHT_GRAY`
* `LIGHT_GREY`
* `LIGHTER_GRAY`
* `LIGHTER_GREY`
* `LIGHT_PINK`
* `GRAY_BROWN`
* `GREY_BROWN`

Variant families:

* `BLUE_A`, `BLUE_B`, `BLUE_C`, `BLUE_D`, `BLUE_E`
* `GREEN_A`, `GREEN_B`, `GREEN_C`, `GREEN_D`, `GREEN_E`
* `YELLOW_A`, `YELLOW_B`, `YELLOW_C`, `YELLOW_D`, `YELLOW_E`
* `RED_A`, `RED_B`, `RED_C`, `RED_D`, `RED_E`
* `PURPLE_A`, `PURPLE_B`, `PURPLE_C`, `PURPLE_D`, `PURPLE_E`
* `TEAL_A`, `TEAL_B`, `TEAL_C`, `TEAL_D`, `TEAL_E`
* `GOLD_A`, `GOLD_B`, `GOLD_C`, `GOLD_D`, `GOLD_E`
* `MAROON_A`, `MAROON_B`, `MAROON_C`, `MAROON_D`, `MAROON_E`
* `GRAY_A`, `GRAY_B`, `GRAY_C`, `GRAY_D`, `GRAY_E`
* `GREY_A`, `GREY_B`, `GREY_C`, `GREY_D`, `GREY_E`

Pure colors:

* `PURE_RED`
* `PURE_GREEN`
* `PURE_BLUE`
* `PURE_YELLOW`
* `PURE_CYAN`
* `PURE_MAGENTA`

Logo colors:

* `LOGO_BLACK`
* `LOGO_WHITE`
* `LOGO_BLUE`
* `LOGO_GREEN`
* `LOGO_RED`

## 2. Safe Style Properties

Use concise key-value properties that can later map to Manim styling code.

```text
color            // overall color
fill_color       // fill color
fill_opacity     // 0..1
stroke_color     // stroke color
stroke_width     // numeric stroke width
stroke_opacity   // 0..1
opacity          // overall opacity
scale            // relative size multiplier
font_size        // text size when needed
```

## 3. Storyboard Output Guidance

When writing storyboard JSON:

* `color_palette` should contain only whitelisted Manim color constants.
* `color_scheme` should describe semantic mapping, not implementation details.
* Object `style` should stay concise and implementation-friendly.
* Prefer structured properties such as `color`, `fill_opacity`, and `stroke_width` over free-form style prose.
* `notes_for_codegen` may remind later stages to preserve palette consistency and emphasis hierarchy.
