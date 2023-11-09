package com.jfrog.mavendeptree;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.jfrog.mavendeptree.dependenciesresults.MavenDepTreeResults;
import com.jfrog.mavendeptree.dependenciesresults.MavenDependencyNode;
import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yahavi
 */
public class Utils {
    private static final ObjectMapper mapper = createMapper();

    /**
     * Create an Object Mapper used to deserialize the dependency tree to a JSON file.
     *
     * @return Object Mapper.
     */
    public static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return mapper;
    }

    /**
     * Create the dependency tree for the given Maven project.
     *
     * @param dependencyGraphBuilder - A helper library that takes the project and session as inputs and constructs a Maven dependency tree
     * @param session                - Maven execution session
     * @param project                - The Maven "project" we are currently working on, specifically as a submodule
     * @return Maven dependency tree
     * @throws DependencyGraphBuilderException if some of the dependencies could not be resolved.
     */
    public static MavenDepTreeResults createDependencyTree(DependencyGraphBuilder dependencyGraphBuilder, MavenSession session,
                                                           MavenProject project) throws DependencyGraphBuilderException {
        ProjectBuildingRequest buildingRequest =
                new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
        buildingRequest.setProject(project);
        DependencyNode root = dependencyGraphBuilder.buildDependencyGraph(buildingRequest, null);
        Map<String, MavenDependencyNode> nodes = new HashMap<>();
        populateDependencyMap(root, nodes);
        return new MavenDepTreeResults(getGavString(root.getArtifact()), nodes);
    }

    /**
     * Recursively populates the dependency nodes map from the given dependency tree.
     *
     * @param node  - The dependency tree of a Maven project
     * @param nodes - Nodes map to populate
     */
    static void populateDependencyMap(DependencyNode node, Map<String, MavenDependencyNode> nodes) {
        Artifact artifact = node.getArtifact();
        MavenDependencyNode mavenDependencyNode = nodes.get(getGavString(artifact));
        if (mavenDependencyNode == null) {
            // Node does not exist in the Map - add it
            mavenDependencyNode = new MavenDependencyNode(artifact.getScope());
            nodes.put(getGavString(artifact), mavenDependencyNode);
        } else {
            // Node exists in the map - add the scope
            mavenDependencyNode.addConfiguration(artifact.getScope());
        }
        List<DependencyNode> children = node.getChildren();
        if (children != null) {
            for (DependencyNode child : children) {
                mavenDependencyNode.addChild(getGavString(child.getArtifact()));
                populateDependencyMap(child, nodes);
            }
        }
    }

    /**
     * Write the dependency tree results to a file.
     *
     * @param project - The Maven "project" we are currently working on, specifically as a submodule
     * @param results - Maven dependency tree result
     * @return the file containing the Maven dependency tree of the Project.
     * @throws IOException in case of any unexpected I/O error.
     */
    static File writeResultsToFile(MavenProject project, MavenDepTreeResults results) throws IOException {
        File targetDir = Paths.get(project.getModel().getBuild().getDirectory(), "maven-dep-tree").toFile();
        FileUtils.forceMkdir(targetDir);
        File resultsPath = targetDir.toPath().resolve(Base64.getEncoder().encodeToString(project.getName().getBytes(StandardCharsets.UTF_8))).toFile();
        mapper.writeValue(resultsPath, results);
        return resultsPath;
    }

    /**
     * Get the node ID of the given Maven artifact.
     *
     * @param artifact - Maven artifact
     * @return node ID.
     */
    public static String getGavString(Artifact artifact) {
        if (artifact == null) {
            return "";
        }
        return String.join(":", artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
    }
}
