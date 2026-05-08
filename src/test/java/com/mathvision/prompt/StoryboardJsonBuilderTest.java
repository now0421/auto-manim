package com.mathvision.prompt;

import com.mathvision.model.Narrative.Storyboard;
import com.mathvision.model.Narrative.StoryboardConstraint;
import com.mathvision.model.Narrative.StoryboardObject;
import com.mathvision.model.Narrative.StoryboardPlacement;
import com.mathvision.model.Narrative.StoryboardPlacementAxis;
import com.mathvision.model.Narrative.StoryboardScene;
import com.mathvision.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoryboardJsonBuilderTest {

    @Test
    void codegenJsonSuppressesPlacementForDerivedObjectsOnly() throws Exception {
        Storyboard storyboard = new Storyboard();

        StoryboardObject anchor = objectWithPlacement("A", "point", "static", "independent", -3.0, 1.0);
        StoryboardObject pmin = objectWithPlacement("Pmin", "point", "derived", "intersection", 0.6, -1.0);
        pmin.setDependencyObjects(List.of("ABprime", "l"));
        pmin.setConstraintNote("lies on both ABprime and l");
        storyboard.setObjectRegistry(List.of(anchor, pmin));

        StoryboardScene scene = new StoryboardScene();
        scene.setSceneId("scene_1");
        scene.setTitle("Intersection");
        scene.setEnteringObjects(List.of(scenePatch("A", -3.0, 1.0), scenePatch("Pmin", 0.6, -1.0)));
        storyboard.setScenes(List.of(scene));

        JsonNode codegen = JsonUtils.mapper().readTree(StoryboardJsonBuilder.buildForCodegen(storyboard));
        String codegenText = codegen.toString();
        assertTrue(codegenText.contains("\"id\":\"A\""));
        assertTrue(codegenText.contains("\"placement\""));
        assertFalse(findObject(codegen.get("object_registry"), "Pmin").has("placement"));
        assertFalse(findObject(codegen.get("scenes").get(0).get("entering_objects"), "Pmin").has("placement"));

        JsonNode sceneFix = JsonUtils.mapper().readTree(StoryboardJsonBuilder.buildForSceneEvaluationFix(storyboard));
        assertFalse(findObject(sceneFix.get("scenes").get(0).get("entering_objects"), "Pmin").has("placement"));
    }

    @Test
    void codegenJsonIncludesStructuredConstraintsAndUsesThemForDerivedPlacement() throws Exception {
        Storyboard storyboard = new Storyboard();

        StoryboardObject base = objectWithPlacement("A", "point", "static", "independent", -3.0, 1.0);
        StoryboardObject reflected = objectWithPlacement("A_ref", "point", "static", "independent", -3.0, -3.0);
        reflected.setConstraints(List.of(constraint(
                "A_ref_reflection",
                "geometry",
                "reflection_across",
                List.of("A_ref", "A", "l"),
                Map.of("target", "A_ref", "source", "A", "mirror_line", "l"),
                Map.of(),
                "hard")));
        StoryboardObject line = objectWithPlacement("l", "line", "static", "independent", 0.0, 0.0);
        storyboard.setObjectRegistry(List.of(base, line, reflected));

        StoryboardScene scene = new StoryboardScene();
        scene.setSceneId("scene_1");
        scene.setTitle("Reflection");
        scene.setConstraints(List.of(constraint(
                "reflection_group",
                "geometry",
                "equal_measure_group",
                List.of("A_ref", "A", "l"),
                Map.of("members", List.of("A_ref", "A")),
                Map.of("measure", "distance_to_line"),
                "hard")));
        scene.setEnteringObjects(List.of(scenePatch("A", -3.0, 1.0), scenePatch("A_ref", -3.0, -3.0)));
        storyboard.setScenes(List.of(scene));

        JsonNode codegen = JsonUtils.mapper().readTree(StoryboardJsonBuilder.buildForCodegen(storyboard));

        JsonNode reflectedNode = findObject(codegen.get("object_registry"), "A_ref");
        assertTrue(reflectedNode.has("constraints"));
        assertFalse(reflectedNode.has("placement"));
        assertTrue(codegen.get("scenes").get(0).has("constraints"));
        assertTrue(codegen.toString().contains("\"relation\":\"reflection_across\""));
    }

    private static StoryboardObject objectWithPlacement(String id,
                                                        String kind,
                                                        String behavior,
                                                        String dependencyRelation,
                                                        double x,
                                                        double y) {
        StoryboardObject object = new StoryboardObject();
        object.setId(id);
        object.setKind(kind);
        object.setBehavior(behavior);
        object.setDependencyRelation(dependencyRelation);
        object.setPlacement(placement(x, y));
        return object;
    }

    private static StoryboardObject scenePatch(String id, double x, double y) {
        StoryboardObject object = new StoryboardObject();
        object.setId(id);
        object.setPlacement(placement(x, y));
        return object;
    }

    private static StoryboardPlacement placement(double x, double y) {
        StoryboardPlacement placement = new StoryboardPlacement();
        placement.setCoordinateSpace("world");
        StoryboardPlacementAxis xAxis = new StoryboardPlacementAxis();
        xAxis.setValue(x);
        StoryboardPlacementAxis yAxis = new StoryboardPlacementAxis();
        yAxis.setValue(y);
        placement.setX(xAxis);
        placement.setY(yAxis);
        return placement;
    }

    private static StoryboardConstraint constraint(String id,
                                                   String category,
                                                   String relation,
                                                   List<String> objects,
                                                   Map<String, Object> roles,
                                                   Map<String, Object> params,
                                                   String strength) {
        StoryboardConstraint constraint = new StoryboardConstraint();
        constraint.setId(id);
        constraint.setCategory(category);
        constraint.setRelation(relation);
        constraint.setObjects(objects);
        constraint.setRoles(roles);
        constraint.setParams(params);
        constraint.setStrength(strength);
        return constraint;
    }

    private static JsonNode findObject(JsonNode array, String id) {
        for (JsonNode object : array) {
            if (id.equals(object.path("id").asText())) {
                return object;
            }
        }
        throw new AssertionError("Missing object " + id);
    }
}
