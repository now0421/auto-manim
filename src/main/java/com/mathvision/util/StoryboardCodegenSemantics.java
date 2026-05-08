package com.mathvision.util;

import com.mathvision.model.Narrative.StoryboardObject;
import com.mathvision.model.Narrative.StoryboardConstraint;

import java.util.Set;

/**
 * Code-generation-only interpretation helpers for storyboard semantics.
 */
public final class StoryboardCodegenSemantics {

    private static final Set<String> COORDINATE_DERIVED_RELATIONS = Set.of(
            "connects_points",
            "reflection_across_line",
            "intersection",
            "midpoint",
            "angle_between",
            "label_for",
            "projection",
            "foot_of_perpendicular",
            "perpendicular_foot",
            "perpendicular_bisector",
            "parallel_through",
            "perpendicular_through",
            "circle_through",
            "arc_between",
            "ray_from_points",
            "segment_between",
            "line_through_points"
    );

    private StoryboardCodegenSemantics() {}

    public static boolean shouldSuppressPlacementForCodegen(StoryboardObject object) {
        if (object == null) {
            return false;
        }
        String behavior = normalize(object.getBehavior());
        if (StoryboardObject.BEHAVIOR_DERIVED.equals(behavior)) {
            return true;
        }
        String relation = normalize(object.getDependencyRelation());
        if (COORDINATE_DERIVED_RELATIONS.contains(relation)) {
            return true;
        }
        if (object.getConstraints() != null) {
            for (StoryboardConstraint constraint : object.getConstraints()) {
                if (constraint == null) {
                    continue;
                }
                String constraintRelation = normalize(constraint.getRelation());
                if (COORDINATE_DERIVED_RELATIONS.contains(constraintRelation)
                        || isCoordinateDerivedConstraintRelation(constraintRelation)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isCoordinateDerivedConstraintRelation(String relation) {
        return Set.of(
                "lies_on",
                "point_on_object",
                "intersection_of",
                "reflection_across",
                "midpoint_of",
                "projection_onto",
                "foot_of_perpendicular",
                "parallel_to",
                "perpendicular_to",
                "angle_between_rays",
                "angle_between_lines",
                "arc_sweep",
                "ray_from_to",
                "line_through_points"
        ).contains(relation);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
