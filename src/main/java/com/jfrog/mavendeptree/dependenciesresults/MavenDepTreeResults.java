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
    // Null when plugin resolution was not requested or found nothing. A plugin that fails to
    // collect is skipped. A download failure still contributes its coordinates, with no partial marker.
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
