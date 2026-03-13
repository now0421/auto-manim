package com.automanim.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class KnowledgeNode {

    private String id;
    private String concept;

    @JsonProperty("min_depth")
    private int minDepth = -1;

    @JsonProperty("is_foundation")
    private boolean foundation;

    private List<String> equations;
    private Map<String, String> definitions;
    private String interpretation;
    private List<String> examples;

    @JsonProperty("visual_spec")
    private Map<String, Object> visualSpec;

    public KnowledgeNode() {}

    public KnowledgeNode(String id, String concept, int minDepth, boolean foundation) {
        this.id = id;
        this.concept = concept;
        this.minDepth = minDepth;
        this.foundation = foundation;
    }

    public synchronized void updateMinDepth(int candidateDepth) {
        if (minDepth < 0 || candidateDepth < minDepth) {
            minDepth = candidateDepth;
        }
    }

    public boolean isEnriched() {
        return equations != null && !equations.isEmpty()
                && definitions != null && !definitions.isEmpty();
    }

    public boolean hasVisualSpec() {
        return visualSpec != null && !visualSpec.isEmpty();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getConcept() { return concept; }
    public void setConcept(String concept) { this.concept = concept; }

    public int getMinDepth() { return minDepth; }
    public void setMinDepth(int minDepth) { this.minDepth = minDepth; }

    public boolean isFoundation() { return foundation; }
    public void setFoundation(boolean foundation) { this.foundation = foundation; }

    public List<String> getEquations() { return equations; }
    public void setEquations(List<String> equations) { this.equations = equations; }

    public Map<String, String> getDefinitions() { return definitions; }
    public void setDefinitions(Map<String, String> definitions) { this.definitions = definitions; }

    public String getInterpretation() { return interpretation; }
    public void setInterpretation(String interpretation) { this.interpretation = interpretation; }

    public List<String> getExamples() { return examples; }
    public void setExamples(List<String> examples) { this.examples = examples; }

    public Map<String, Object> getVisualSpec() { return visualSpec; }
    public void setVisualSpec(Map<String, Object> visualSpec) { this.visualSpec = visualSpec; }

    @Override
    public String toString() {
        return "KnowledgeNode{id='" + id + "', concept='" + concept + "', minDepth=" + minDepth
                + ", foundation=" + foundation + "}";
    }
}
