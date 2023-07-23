package com.jfrog.mavendeptree;

import com.jfrog.mavendeptree.dependenciesresults.MavenDependencyNode;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.internal.DefaultDependencyNode;
import org.testng.annotations.Test;
import org.testng.collections.Lists;
import org.testng.collections.Sets;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static com.jfrog.mavendeptree.Utils.getGavString;
import static com.jfrog.mavendeptree.Utils.populateDependencyMap;
import static org.testng.Assert.*;

/**
 * @author yahavi
 */
public class UtilsTests {

    @Test
    public void testPopulateDependencyMapNoChildren() {
        DependencyNode root = createDependency("root-group", "root-artifact", "root-version", null);
        runPopulateDependencyMap(root, 1);
    }

    @Test
    public void testPopulateDependencyMapDuplicateChildren() {
        DefaultDependencyNode root = createDependency("root-group", "root-artifact", "root-version", null);

        // Root's children
        DefaultDependencyNode firstChild = createDependency("child1-group", "child1-artifact", "child1-version", "scope1");
        DefaultDependencyNode secondChild = createDependency("child2-group", "child2-artifact", "child2-version", "scope2");
        root.setChildren(Lists.newArrayList(firstChild, secondChild));

        // First child's children
        DefaultDependencyNode thirdChild = createDependency("child3-group", "child3-artifact", "child3-version", "scope3");
        DefaultDependencyNode secondsChildWithOtherScope = createDependency("child2-group", "child2-artifact", "child2-version", "other-scope");

        firstChild.setChildren(Lists.newArrayList(secondsChildWithOtherScope, thirdChild));

        Map<String, MavenDependencyNode> nodes = runPopulateDependencyMap(root, 4);

        // Check root node
        MavenDependencyNode rootNode = nodes.get("root-group:root-artifact:root-version");
        assertEquals(rootNode.getChildren(), Sets.newHashSet("child1-group:child1-artifact:child1-version", "child2-group:child2-artifact:child2-version"));

        // Check first child's node
        MavenDependencyNode firstChildNode = nodes.get("child1-group:child1-artifact:child1-version");
        assertNotNull(firstChildNode);
        assertEquals(firstChildNode.getChildren(), Sets.newHashSet("child2-group:child2-artifact:child2-version", "child3-group:child3-artifact:child3-version"));
        assertEquals(firstChildNode.getConfigurations(), Sets.newHashSet("scope1"));

        // Check seconds child's node
        MavenDependencyNode secondChildNode = nodes.get("child2-group:child2-artifact:child2-version");
        assertNotNull(secondChildNode);
        assertEquals(secondChildNode.getChildren(), new HashSet<>());
        assertEquals(secondChildNode.getConfigurations(), Sets.newHashSet("scope2", "other-scope"));
    }

    @Test
    public void testGetNodeId() {
        Artifact artifact = createArtifact("group", "artifact", "1.0.0", "scope");
        assertEquals(getGavString(artifact), "group:artifact:1.0.0");
    }

    private Artifact createArtifact(String groupId, String artifactId, String version, String scope) {
        return new DefaultArtifact(groupId, artifactId, version, scope, "", "", null);
    }

    private DefaultDependencyNode createDependency(String groupId, String artifactId, String version, String scope) {
        Artifact artifact = createArtifact(groupId, artifactId, version, scope);
        return new DefaultDependencyNode(artifact);
    }

    private Map<String, MavenDependencyNode> runPopulateDependencyMap(DependencyNode root, int expectedNodes) {
        Map<String, MavenDependencyNode> nodes = new HashMap<>();
        populateDependencyMap(root, nodes);
        assertEquals(nodes.size(), expectedNodes);
        assertTrue(nodes.containsKey("root-group:root-artifact:root-version"));
        return nodes;
    }
}
