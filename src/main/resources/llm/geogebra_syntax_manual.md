# GeoGebra Syntax for LLM Output

## Rules

- Use `Point(path)` or `PointIn(region)` for draggable points constrained by existing geometry.
- Use `Slider(...)` when motion or rotation must stay within an explicit numeric range.
- Use `RigidPolygon(...)` for fixed-size shapes that must remain draggable and rotatable.
- Use `Polygon(...)` to create triangles, rectangles, and regular polygons.

## Point

```text
Point( <Object> )                 // point constrained to an existing path or object
Point( <Object>, <Parameter> )    // parameter chooses the initial position on that object
Point( <Point>, <Vector> )        // vector is a displacement from the given point
Point( <List> )                   // list of two coordinates
PointIn( <Region> )               // point constrained to the interior of the region
```

```geogebra
fixedPoint = Point({0, 0})
SetFixed(fixedPoint, true)

freePoint = Point({3, 1})

pointOnLine = Point(L)
pointOnCircle = Point(circle1)
pointOnSegmentAtParameter = Point(S, 0.25)
pointInTriangle = PointIn(triangle)
```

## Vector

```text
Vector( <Point> )
Vector( <Start Point>, <End Point> )
```

```geogebra
vectorAB = Vector(A, B)
vector1 = Vector((3, 2))
```

## Line

```text
Line( <Point>, <Point> )
Line( <Point>, <Parallel Line> )   // line through the point parallel to the given line
Line( <Point>, <Direction Vector> ) // vector gives direction only
```

```geogebra
L = Line(A, B)
```

## Segment

```text
Segment( <Point>, <Point> )
Segment( <Point>, <Length> )
```

```geogebra
S = Segment(A, B)
```

## Ray

```text
Ray( <Start Point>, <Point> )
Ray( <Start Point>, <Direction Vector> )
```

```geogebra
rayAB = Ray(A, B)
```

## Circle

```text
Circle( <Point>, <Radius Number> )
Circle( <Point>, <Segment> )
Circle( <Point>, <Point> )           // center and one point on the circle
Circle( <Point>, <Point>, <Point> )  // circumcircle through three non-collinear points
```

```geogebra
circle1 = Circle(A, B)
```

## Polygon

```text
Polygon( <Point>, ..., <Point> )
Polygon( <Point>, <Point>, <Number of Vertices> )   // regular polygon; last parameter is total vertices
Polygon( <List of Points> )
RigidPolygon( <Polygon> )                           // rigid draggable copy; first vertex translates, second rotates
RigidPolygon( <Polygon>, <Offset x>, <Offset y> )  // rigid copy shifted by the given offset
RigidPolygon( <Free Point>, ..., <Free Point> )    // build a rigid polygon from free vertices
```

```geogebra
triangle = Polygon(A, B, C)
rectangle = Polygon(A, B, C, D)
polygon1 = Polygon(A, B, C, D, E)
regularPolygon = Polygon(A, B, n)

rigidRectangle = RigidPolygon(A, B, C, D)
rigidPolygon = RigidPolygon(polygon1)
```

## Slider

```text
Slider( <Min>, <Max>, <Increment>, <Speed>, <Width>, <Is Angle>, <Horizontal>, <Animating>, <Boolean Random> ) // range, step, speed, width, angle-mode, orientation, auto-animation, random
```

```geogebra
position = Slider(min, max, increment, speed, width, isAngle, horizontal, animating, random)
rotationAngle = Slider(-45, 45, 1, 1, 140, true, true, false, false)

pointOnXAxis = Point({position, 0})
rotatedRectangle = Rotate(rectangle, rotationAngle, O)
```

## Transformation

```text
Translate( <Object>, <Vector> )      // move the whole object by the vector
Translate( <Vector>, <Start Point> ) // relocate the vector so it starts at the point
Rotate( <Object>, <Angle> )          // rotate around the origin
Rotate( <Object>, <Angle>, <Point> ) // rotate around the given point
Reflect( <Object>, <Point> )
Reflect( <Object>, <Line> )
Reflect( <Object>, <Circle> )        // inversion with respect to the circle
Dilate( <Object>, <Dilation Factor> ) // dilate from the origin
Dilate( <Object>, <Dilation Factor>, <Dilation Center Point> ) // dilate from the given center
```

```geogebra
translatedShape = Translate(polygon1, vectorAB)
rotatedShape = Rotate(polygon1, rotationAngle, O)
reflectedShape = Reflect(polygon1, L)
dilatedShape = Dilate(polygon1, scaleFactor, O)
```

## Dependency

```text
Midpoint( <Segment> )
Midpoint( <Conic> )                     // returns the center of the conic
Midpoint( <Point>, <Point> )
Intersect( <Object>, <Object> )         // may return one or more intersection points
Center( <Conic> )
Distance( <Point>, <Object> )           // shortest distance from the point to the object
Distance( <Line>, <Line> )
Length( <Object> )
Length( <Function>, <Start x-Value>, <End x-Value> ) // graph length on the x-interval
Area( <Point>, ..., <Point> )          // points are interpreted as a polygon boundary
Area( <Conic> )
Area( <Polygon> )
```

```geogebra
midpointAB = Midpoint(A, B)
intersection1 = Intersect(L, circle1)
center1 = Center(circle1)

distanceAB = Distance(A, B)
lengthS = Length(S)
areaTriangle = Area(triangle)
```

## Style

- Style commands do not create objects and should be applied after construction.
- Prefer English color names or hex colors in scripting commands.

```text
SetBackgroundColor( <Object>, <Red>, <Green>, <Blue> )
SetBackgroundColor( <Object>, <"Color"> )
SetBackgroundColor( <Red>, <Green>, <Blue> )         // active Graphics View; numeric channels use 0..1
SetBackgroundColor( <"Color"> )                      // active Graphics View
SetColor( <Object>, <"Color"> )
SetColor( <Object>, <Red>, <Green>, <Blue> )         // numeric RGB uses the 0..1 scale
SetColor( <Object>, <"#RRGGBB"> )
SetColor( <Object>, <"#AARRGGBB"> )                  // AA + RGB
SetDynamicColor( <Object>, <Red>, <Green>, <Blue> )
SetDynamicColor( <Object>, <Red>, <Green>, <Blue>, <Opacity> ) // all numeric inputs use 0..1
SetLineThickness( <Object>, <Number> )
SetLineOpacity( <Object>, <Number> )
SetLineStyle( <Line>, <Number> )                     // 0 full, 1 dashed long, 2 dashed short, 3 dotted, 4 dash-dot
SetPointStyle( <Point>, <Number> )                  // 0 dot, 1 cross, 2 empty dot, 3 plus, 4 full diamond, 5 empty diamond, 6-9 triangles, 10 full dot without outline
SetPointSize( <Point>, <Number> )
SetPointSize( <Object>, <Number> )
SetFilling( <Object>, <Number> )
SetDecoration( <Object>, <Number> )                  // style code depends on object type
SetDecoration( <Segment>, <Number>, <Number> )       // start decoration, end decoration
ShowLabel( <Object>, <Boolean> )
SetLabelMode( <Object>, <Number> )                   // 0 name, 1 name + value, 2 value, 3 caption, 9 caption + value
SetCaption( <Object>, <Text> )
SetFixed( <Object>, <true | false> )
SetFixed( <Object>, <true | false>, <true | false> ) // third parameter controls Selection Allowed
SetTooltipMode( <Object>, <Number> )                // 0 automatic, 1 on, 2 off, 3 caption, 4 next cell
SetTrace( <Object>, <true | false> )
SetLayer( <Object>, <Layer> )                       // layer 0..9
ShowLayer( <Number> )
HideLayer( <Number> )
SetConditionToShowObject( <Object>, <Condition> )    // boolean condition
SetVisibleInView( <Object>, <View Number 1|2|-1>, <Boolean> ) // 1 Graphics, 2 Graphics 2, -1 3D
ShowAxes( )                                          // active view
ShowAxes( <Boolean> )
ShowAxes( <View>, <Boolean> )                        // view 1, 2, or 3
ShowGrid( )                                          // active view
ShowGrid( <Boolean> )
ShowGrid( <View>, <Boolean> )                        // view 1, 2, or 3
SetLevelOfDetail( <Surface>, <Level of Detail> )     // 0 faster, 1 more accurate
```

```geogebra
SetBackgroundColor("White")
SetColor(L, "Blue")
SetColor(triangle, "#4E79A7")
SetDynamicColor(A, 1, 0, 0, 0.7)
SetLineThickness(L, 6)
SetLineStyle(L, 2)
SetPointStyle(A, 2)
SetPointSize(A, 5)
SetFilling(triangle, 0.2)
SetDecoration(S, 1)
ShowLabel(L, true)
SetLabelMode(L, 3)
SetCaption(L, "Line AB")
SetTooltipMode(A, 3)
SetLayer(L, 2)
ShowGrid(1, false)
SetFixed(A, true)
```

## Forbidden Syntax

```text
Triangle(A, B, C)
Rectangle(A, B, C, D)
RegularPolygon(A, B, 5)
v = (1, 2)
A = (1, 2)
blue thick segment AB
Polygons#sym:Polygons
```
