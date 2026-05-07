package com.mathvision.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Trace artifact entry for one storyboard validation checkpoint.
 */
public class StoryboardValidationTraceEntry {

    @JsonProperty("sequence")
    private int sequence;

    @JsonProperty("phase")
    private String phase;

    @JsonProperty("cleanup_attempt")
    private int cleanupAttempt;

    @JsonProperty("passed")
    private boolean passed;

    @JsonProperty("scene_count")
    private int sceneCount;

    @JsonProperty("issue_count")
    private int issueCount;

    @JsonProperty("issues")
    private List<String> issues = new ArrayList<>();

    @JsonProperty("fix_attempted")
    private boolean fixAttempted;

    @JsonProperty("fix_applied")
    private boolean fixApplied;

    @JsonProperty("tool_calls")
    private int toolCalls;

    @JsonProperty("execution_time_seconds")
    private double executionTimeSeconds;

    @JsonProperty("message")
    private String message;

    public int getSequence() { return sequence; }
    public void setSequence(int sequence) { this.sequence = sequence; }

    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }

    public int getCleanupAttempt() { return cleanupAttempt; }
    public void setCleanupAttempt(int cleanupAttempt) { this.cleanupAttempt = cleanupAttempt; }

    public boolean isPassed() { return passed; }
    public void setPassed(boolean passed) { this.passed = passed; }

    public int getSceneCount() { return sceneCount; }
    public void setSceneCount(int sceneCount) { this.sceneCount = sceneCount; }

    public int getIssueCount() { return issueCount; }
    public void setIssueCount(int issueCount) { this.issueCount = issueCount; }

    public List<String> getIssues() { return issues; }
    public void setIssues(List<String> issues) {
        this.issues = issues != null ? new ArrayList<>(issues) : new ArrayList<>();
    }

    public boolean isFixAttempted() { return fixAttempted; }
    public void setFixAttempted(boolean fixAttempted) { this.fixAttempted = fixAttempted; }

    public boolean isFixApplied() { return fixApplied; }
    public void setFixApplied(boolean fixApplied) { this.fixApplied = fixApplied; }

    public int getToolCalls() { return toolCalls; }
    public void setToolCalls(int toolCalls) { this.toolCalls = toolCalls; }

    public double getExecutionTimeSeconds() { return executionTimeSeconds; }
    public void setExecutionTimeSeconds(double executionTimeSeconds) {
        this.executionTimeSeconds = executionTimeSeconds;
    }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
