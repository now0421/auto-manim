package com.mathvision.util;

import com.mathvision.model.Narrative.StoryboardObject;
import com.mathvision.model.Narrative.StoryboardConstraint;

/**
 * Code-generation-only interpretation helpers for storyboard semantics.
 */
public final class StoryboardCodegenSemantics {

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
        if (StoryboardConstraintCatalog.isCoordinateDerivedDependencyRelation(relation)) {
            return true;
        }
        if (object.getConstraints() != null) {
            for (StoryboardConstraint constraint : object.getConstraints()) {
                if (constraint == null) {
                    continue;
                }
                String constraintRelation = normalize(constraint.getRelation());
                if (StoryboardConstraintCatalog.isCoordinateDerivedRelation(constraintRelation)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
