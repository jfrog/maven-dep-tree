package com.jfrog.mavendeptree.dependenciesresults;

import java.util.Map;

/**
 * @author yahavi
 */
public class MavenDepTreeResults {
    private String root;
    private Map<String, MavenDependencyNode> nodes;

    // Empty constructor for deserialization
    @SuppressWarnings("unused")
    public MavenDepTreeResults() {
    }

    public MavenDepTreeResults(String root, Map<String, MavenDependencyNode> nodes) {
        this.root = root;
        this.nodes = nodes;
    }

    public String getRoot() {
        return root;
    }

    public Map<String, MavenDependencyNode> getNodes() {
        return nodes;
    }
}
