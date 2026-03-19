package com.automanim.service;

import com.automanim.model.CodeResult;
import com.automanim.model.RenderResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileOutputServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void saveRenderResultPersistsGeometryPath() throws IOException {
        RenderResult renderResult = new RenderResult();
        renderResult.setSuccess(true);
        renderResult.setSceneName("DemoScene");
        renderResult.setVideoPath("media/videos/demo.mp4");
        renderResult.setGeometryPath("5_mobject_geometry.json");
        renderResult.setAttempts(1);
        renderResult.setToolCalls(0);

        FileOutputService.saveRenderResult(tempDir, renderResult);

        String metadata = Files.readString(tempDir.resolve("5_render_result.json"));
        assertTrue(metadata.contains("\"geometry_path\""));
        assertTrue(metadata.contains("5_mobject_geometry.json"));
    }

    @Test
    void loadCodeResultRestoresMetadataWhenPresent() throws IOException {
        Files.writeString(tempDir.resolve("4_manim_code.py"), sampleCode("RecoveredScene"));
        Files.writeString(tempDir.resolve("4_code_result.json"), String.join("\n",
                "{",
                "  \"scene_name\": \"RecoveredScene\",",
                "  \"description\": \"manual resume\",",
                "  \"target_concept\": \"Manual Concept\",",
                "  \"target_description\": \"Recovered from disk\"",
                "}"));

        CodeResult codeResult = FileOutputService.loadCodeResult(tempDir.resolve("4_manim_code.py"));

        assertEquals("RecoveredScene", codeResult.getSceneName());
        assertEquals("manual resume", codeResult.getDescription());
        assertEquals("Manual Concept", codeResult.getTargetConcept());
        assertEquals("Recovered from disk", codeResult.getTargetDescription());
        assertTrue(codeResult.getManimCode().contains("class RecoveredScene(Scene):"));
    }

    @Test
    void loadCodeResultFallsBackToSceneNameWhenMetadataMissing() throws IOException {
        Files.writeString(tempDir.resolve("4_manim_code.py"), sampleCode("FallbackScene"));

        CodeResult codeResult = FileOutputService.loadCodeResult(tempDir.resolve("4_manim_code.py"));

        assertEquals("FallbackScene", codeResult.getSceneName());
        assertEquals("FallbackScene", codeResult.getTargetConcept());
        assertEquals("", codeResult.getTargetDescription());
    }

    private static String sampleCode(String sceneName) {
        return String.join("\n",
                "from manim import *",
                "",
                "class " + sceneName + "(Scene):",
                "    def construct(self):",
                "        self.wait(1)");
    }
}
