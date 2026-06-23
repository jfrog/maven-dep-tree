package com.jfrog.mavendeptree.dependenciesresults;

import java.util.Map;

/**
 * @author yahavi
 */
public class MavenDepTreeResults {
    private String root;
    private Map<String, MavenDependencyNode> nodes;
    // Build plugins (and their transitive deps) resolved when includePluginDeps=true.
    // Kept separate from nodes so the CLI can distinguish project deps from plugin deps
    // and apply curation checks only to the relevant artifact types.
    // Null when plugin dependency resolution was not requested, or when it was requested
    // but no plugin dependencies were found. Resolution itself is non-fatal: individual
    // plugin/artifact failures are logged and skipped rather than producing a null result.
    private Map<String, MavenDependencyNode> pluginNodes;

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

    public Map<String, MavenDependencyNode> getPluginNodes() {
        return pluginNodes;
    }

    public void setPluginNodes(Map<String, MavenDependencyNode> pluginNodes) {
        this.pluginNodes = pluginNodes;
    }
}
