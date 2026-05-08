package com.mathvision.node;

import com.mathvision.model.SceneCodeEntry;
import com.mathvision.util.ManimCodeUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeGenerationNodeAssemblyTest {

    @Test
    void assembleManimPerSceneCodeInsertsMethodsInsideMainScene() {
        String skeleton = String.join("\n",
                "from manim import *",
                "",
                "class MainScene(Scene):",
                "    def construct(self):",
                "        self.scene_1_intro()",
                "        self.scene_2_finish()",
                "",
                "    # __SCENE_METHODS__");
        List<SceneCodeEntry> entries = List.of(
                new SceneCodeEntry(0, "scene_1", "scene_1_intro",
                        "title = Text(\"Intro\")\nself.play(Write(title))", false),
                new SceneCodeEntry(1, "scene_2", "scene_2_finish",
                        "def scene_2_finish(self):\n    self.wait(1)", false)
        );

        String code = CodeGenerationNode.assembleManimPerSceneCode(skeleton, entries);

        assertTrue(code.contains("    def scene_1_intro(self):\n        title = Text(\"Intro\")"));
        assertTrue(code.contains("    def scene_2_finish(self):\n        self.wait(1)"));
        assertFalse(code.contains("\ndef scene_1_intro(self):"));
        assertFalse(code.contains("\ndef scene_2_finish(self):"));
        assertTrue(ManimCodeUtils.validateFull(code).isEmpty());
    }
}
