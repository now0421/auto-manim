package com.mathvision.util;

import com.mathvision.model.Narrative.StoryboardObject;

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
        return COORDINATE_DERIVED_RELATIONS.contains(relation);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
