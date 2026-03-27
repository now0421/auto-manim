# GeoGebra Style Reference

Use this compact reference when writing storyboard-level style instructions for GeoGebra output. This stage does not write code, but all style language should stay compatible with later GeoGebra construction and presentation steps.

## 1. Safe Style Language

Describe style in simple GeoGebra-friendly terms:

* color
* line thickness
* line style
* point size
* point style
* fill opacity
* label visibility
* object visibility

Prefer short phrases such as:

* blue primary segment with medium thickness
* red highlight point
* dashed gray helper line
* lightly filled polygon
* hide auxiliary bisectors after construction
* show labels only for key points

Avoid CSS-style syntax, gradients, shadows, blur, texture language, or browser-specific styling terms.

## 2. Style Planning Rules

* Use semantic color mapping consistently across the figure.
* Keep the same object category in the same color whenever possible.
* Important objects should be brighter, thicker, or more visible than helper objects.
* Helper objects can use dashed lines, neutral colors, or reduced emphasis.
* Do not overload one figure with too many saturated highlight colors.
* Labels and measurements should support the construction rather than crowd it.

## 3. Visibility Guidance

When planning a construction:

* keep labels on key points, key lines, and final results
* hide temporary helper objects unless they are pedagogically important
* reduce clutter before introducing additional annotations
* prefer one clear highlighted target over several competing accents

## 4. Dynamic Figure Guidance

If the figure is interactive or slider-driven:

* style the moving object so it is easy to track
* keep dependent objects visually attached to the same semantic palette
* use dynamic text sparingly and only for values that genuinely help interpretation
* avoid too many simultaneous moving labels or measurements

## 5. Storyboard Output Guidance

When writing storyboard JSON or high-level design notes:

* `color_palette` should list simple named colors only
* `color_scheme` should describe semantic mapping, not implementation details
* object `style` should stay concise and construction-friendly
* `notes_for_codegen` may remind later stages to preserve dependency clarity, helper visibility rules, and emphasis hierarchy
