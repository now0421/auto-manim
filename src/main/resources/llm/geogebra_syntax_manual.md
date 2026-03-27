# GeoGebra Usage Guide

Please strictly use common, stable, and highly readable **GeoGebra Classic** patterns when generating construction commands, object definitions, or step-by-step geometry instructions. The output should prioritize correctness, dependency stability, and visual clarity first, and only then pursue fancy effects.

## I. Recommended Working Style

When building GeoGebra content, always think in this order:

1. Define the base objects first.
2. Build dependent objects from those base objects.
3. Apply labels, colors, and visibility settings.
4. Only then add interactive controls or animation-related settings.

Do not define a derived object from hardcoded coordinates if it should really depend on earlier geometry.

---

## II. Core Object Types

## 1. Point Objects

### Free Point

Used for:

* anchor points
* manually placed geometric vertices
* draggable references

```geogebra
A = (0, 0)
B = (4, 0)
```

### Point on Object

Used for:

* points constrained to a line
* points constrained to a circle
* moving points in dynamic constructions

```geogebra
P = Point(c)
Q = Point(lineAB)
```

Principles:

* Prefer constrained points when the point must stay on an existing object.
* Do not fake "point on circle" behavior by manually updating coordinates.

### Midpoint / Center Point

Used for:

* segment midpoints
* centers of circles or conics

```geogebra
M = Midpoint(A, B)
O = Center(c)
```

---

## 2. Basic Linear Objects

### Segment

Used for:

* finite edges
* polygon sides
* measured distances

```geogebra
s = Segment(A, B)
```

### Line

Used for:

* infinite lines
* supporting lines
* analytic geometry references

```geogebra
lineAB = Line(A, B)
```

### Ray

Used for:

* angle arms
* directed geometric constructions

```geogebra
r = Ray(A, B)
```

### Vector

Used for:

* displacement
* direction display
* vector operations

```geogebra
u = Vector(A, B)
```

Principles:

* Use `Segment` when the object is finite.
* Use `Line` when the whole infinite extension matters.
* Use `Ray` when a shared vertex and direction matter.

---

## 3. Circles, Arcs, and Conics

### Circle

Used for:

* radius-based constructions
* circumcircles
* loci and rotation references

```geogebra
c = Circle(A, B)
d = Circle(O, 3)
```

### Arc / Sector

Used for:

* angle visualization
* highlighted circular regions

```geogebra
arc1 = CircularArc(O, A, B)
sec1 = CircularSector(O, A, B)
```

Safety rules:

* Prefer official arc or sector commands over approximating arcs manually.
* If the construction depends on angle orientation, make sure the start point, end point, and center are semantically correct.
* Do not use a decorative arc when the real geometric angle should stay attached to moving points.

### Other Conics

Use as needed:

* `Ellipse(F1, F2, a)`
* `Hyperbola(F1, F2, a)`
* `Parabola(P, l)`

Use them only when the topic truly needs conics, and keep the construction readable.

---

## 4. Polygonal and Region Objects

### Polygon

Used for:

* triangles
* quadrilaterals
* filled regions
* area demonstrations

```geogebra
poly = Polygon(A, B, C, D)
tri = Polygon(A, B, C)
```

### RegularPolygon

Used for:

* regular triangles
* squares
* pentagons
* standard construction demonstrations

```geogebra
sq = RegularPolygon(A, B, 4)
```

### Semicircle

Used for:

* Thales-type geometry
* clean half-circle constructions

```geogebra
sc = Semicircle(A, B)
```

Principles:

* Prefer dedicated high-level commands such as `RegularPolygon` or `Semicircle` when available.
* Do not rebuild a standard object from many fragile helper steps unless the lesson specifically teaches those steps.

---

## 5. Angle Objects

### Angle

Used for:

* angle measurement
* angle markers
* geometric constraints

```geogebra
alpha = Angle(B, A, C)
beta = Angle(line1, line2)
```

Angle safety rules:

* Prefer `Angle(...)` over manually computing slopes or hand-building angle labels.
* When the angle is at a vertex, use the correct middle argument as the vertex.
* If the construction moves, keep the angle defined from the actual source objects so it updates automatically.
* Do not place a text label that says "30 deg" unless that value is intentionally fixed and mathematically justified.

---

## 6. Text, Labels, and Explanatory Objects

### Text

Used for:

* titles
* subtitles
* step labels
* explanatory notes

```geogebra
t1 = Text("Construct the perpendicular bisector")
```

### Dynamic Text

Used for:

* showing a computed length
* displaying a changing angle
* interactive parameter readouts

```geogebra
t2 = Text("Length AB = " + Distance(A, B))
t3 = Text("alpha = " + Round(alpha, 1) + " deg")
```

Principles:

* Prefer dynamic text when the value should update with the construction.
* Keep displayed text short and instructional.
* Do not overload one scene with long paragraphs.

---

## III. Common Construction Commands

## 1. Intersection and Incidence

### Intersect

Used for:

* line-line intersections
* line-circle intersections
* conic intersections

```geogebra
X = Intersect(lineAB, c)
Y = Intersect(c, d)
```

### Point Membership / Attachment

Use dependency-aware constructions whenever possible:

```geogebra
P = Point(lineAB)
Q = Point(poly)
```

Principles:

* Prefer `Intersect(...)` and `Point(...)` over reverse-engineering coordinates.
* Let GeoGebra maintain the dependency graph for you.

---

## 2. Perpendicular and Parallel Constructions

### PerpendicularLine

```geogebra
perp = PerpendicularLine(A, lineAB)
```

### ParallelLine

```geogebra
para = ParallelLine(C, lineAB)
```

### PerpendicularBisector

```geogebra
pb = PerpendicularBisector(A, B)
```

### AngularBisector

```geogebra
bis = AngularBisector(B, A, C)
```

Principles:

* Prefer these dedicated commands over manually estimating slopes or directions.
* If the lesson is about a standard geometric construction, these commands are the safest default.

---

## 3. Distance, Length, and Measurement

### Distance / Length

```geogebra
d1 = Distance(A, B)
len1 = Length(s)
```

### Area / Perimeter

```geogebra
area1 = Area(poly)
per1 = Perimeter(poly)
```

### Slope

```geogebra
m = Slope(lineAB)
```

Measurement rules:

* Use measurement commands for displayed values, validation, or dynamic text.
* Do not replace exact geometric dependencies with numeric approximations unless the lesson is explicitly numerical.

---

## 4. Transformations

### Translate

```geogebra
obj2 = Translate(obj1, u)
```

### Rotate

```geogebra
obj2 = Rotate(obj1, 60deg, A)
```

### Reflect

```geogebra
obj2 = Reflect(obj1, lineAB)
```

### Dilate

```geogebra
obj2 = Dilate(obj1, 1.5, A)
```

Transformation principles:

* Prefer geometric transformation commands over manually re-entering transformed coordinates.
* If a reflected or rotated object should stay linked to the source, define it through the transformation command directly.

---

## 5. Analytic and Function Objects

### Function

Used for:

* graphing a formula
* slope or derivative demonstrations
* coordinate-system lessons

```geogebra
f(x) = x^2 - 2x + 1
g(x) = sin(x)
```

### Point on Graph

```geogebra
P = Point(f)
```

### Tangent

```geogebra
t = Tangent(P, f)
```

### Derivative / Integral

```geogebra
fp(x) = Derivative(f)
areaF = Integral(f, 0, 2)
```

Principles:

* Use named functions for clarity.
* Keep algebraic definitions simple and readable.
* Prefer direct commands such as `Derivative(...)` and `Tangent(...)` instead of manual symbolic rewrites.

---

## IV. Variables, Sliders, and Dynamic Control

## 1. Numeric Variables

Used for:

* parameters
* radii
* animation drivers
* angle controls

```geogebra
a = 3
r = 2.5
```

## 2. Slider-Driven Constructions

Used for:

* interactive parameter changes
* dynamic demonstrations
* animation controls

Common pattern:

```geogebra
t = 0
P = (t, t^2)
```

Principles:

* If a point or object should move continuously, define it from a variable or slider.
* Keep the number of active sliders small.
* One clear parameter is usually better than many unrelated controls.

---

## V. Style and Visibility Commands

GeoGebra style instructions should remain simple, semantic, and stable.

## 1. Safe Style Properties

Common settings:

* color
* line thickness
* line style
* point size
* point style
* filling
* opacity
* label visibility
* object visibility

Use concise instructions such as:

* blue segment with medium thickness
* red highlight point
* dashed gray helper line
* lightly filled polygon
* hide auxiliary construction lines

## 2. Style Principles

* Main objects should be visually stronger than helper objects.
* Helper lines should often be lighter, dashed, or lower-emphasis.
* Keep the same object category in the same color whenever possible.
* Do not assign many unrelated bright colors in one figure.
* Labels should support the geometry, not overwhelm it.

## 3. Visibility Principles

* Hide temporary helper objects unless they are instructionally important.
* Keep labels on important points, lines, and measured values.
* If a construction becomes cluttered, reduce auxiliary visibility before changing the core geometry.

---

## VI. Dynamic Dependency Rules

GeoGebra is strongest when objects stay mathematically attached to each other.

## 1. Prefer Dependency Over Hardcoding

Good:

```geogebra
M = Midpoint(A, B)
pb = PerpendicularBisector(A, B)
```

Bad idea:

```geogebra
M = ((x(A) + x(B)) / 2, (y(A) + y(B)) / 2)
```

unless the lesson explicitly studies coordinate formulas.

## 2. Preserve Semantic Attachment

If a label, intersection, midpoint, tangent point, or reflected point depends on another object, define it from that source object directly.

Examples:

* use `Intersect(line1, line2)` for an intersection point
* use `Point(c)` for a point on a circle
* use `Reflect(A, lineAB)` for a reflected point
* use `Midpoint(A, B)` for a midpoint

## 3. Keep Moving Objects Truly Dynamic

If the user drags a base point or changes a slider:

* dependent points should update automatically
* helper lines should stay attached
* measurements should recompute automatically
* text readouts should use dynamic expressions when needed

Do not freeze a derived object into static coordinates if it should continue to track the source geometry.

---

## VII. Coordinate and Layout Guidance

## 1. Prefer Moderate Coordinates

Use simple coordinates such as:

```geogebra
A = (0, 0)
B = (4, 0)
C = (1.5, 3)
```

Avoid extreme scales unless the lesson truly needs them.

## 2. Keep the Figure Readable

* Leave enough space between labels and objects.
* Avoid stacking too many measurements in one corner.
* Spread major anchor points so circles, bisectors, and labels remain readable.

## 3. Distinguish Main Geometry from Helpers

If many helper lines are required:

* make the final target object visually dominant
* reduce the visual weight of temporary scaffolding
* hide helpers once their purpose is complete, if the workflow supports it

---

## VIII. Recommended Command Patterns

## 1. Triangle Geometry

```geogebra
A = (0, 0)
B = (4, 0)
C = (1.5, 3)
tri = Polygon(A, B, C)
M = Midpoint(B, C)
med = Line(A, M)
```

## 2. Circle and Chord Geometry

```geogebra
O = (0, 0)
A = (3, 0)
c = Circle(O, A)
B = Point(c)
chord = Segment(A, B)
pb = PerpendicularBisector(A, B)
```

## 3. Reflection Construction

```geogebra
A = (1, 2)
l = Line((0, 0), (4, 1))
A1 = Reflect(A, l)
seg = Segment(A, A1)
midAA1 = Midpoint(A, A1)
```

## 4. Function and Tangent

```geogebra
f(x) = x^2
P = Point(f)
t = Tangent(P, f)
```

These patterns are preferred because they are short, standard, and dependency-safe.

---

## IX. Recommended Principles When Generating GeoGebra Content

1. Prefer common and stable GeoGebra commands over obscure or version-sensitive tricks.
2. Build from base objects to derived objects in a clear dependency chain.
3. Prefer dedicated geometric commands such as `Midpoint`, `Intersect`, `PerpendicularBisector`, `Reflect`, and `Tangent`.
4. Use dynamic definitions when the construction should respond to dragging or slider changes.
5. Keep coordinates moderate and layout readable.
6. Make important objects visually stronger than helper constructions.
7. Use dynamic text for changing values, but keep the wording short.
8. Avoid replacing exact geometric relationships with manually computed approximate coordinates unless the lesson explicitly requires coordinate derivation.
9. Hide unnecessary auxiliary objects when they no longer support understanding.
10. Let color, label visibility, and object dependency communicate the teaching structure.

---
