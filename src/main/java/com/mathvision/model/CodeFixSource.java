package com.mathvision.model;

/**
 * Source stage that requested a shared code-fix pass.
 */
public enum CodeFixSource {
    CODE_EVALUATION_REVIEW,
    CODE_RENDER_FAILURE,
    CODE_SCENE_LAYOUT_EVALUATION
}
