package com.jfrog.mavendeptree.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jfrog.mavendeptree.dependenciesresults.MavenDepTreeResults;
import com.jfrog.mavendeptree.dependenciesresults.MavenDependencyNode;
import org.apache.commons.io.FileUtils;
import org.apache.maven.it.VerificationException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.testng.collections.Sets;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static com.jfrog.mavendeptree.Utils.createMapper;
import static com.jfrog.mavendeptree.integration.Utils.getPluginVersion;
import static com.jfrog.mavendeptree.integration.Utils.runMavenDepTree;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/**
 * @author yahavi
 */
public class MavenDepTreeITest {
    private final String pluginVersion = getPluginVersion();
    ObjectMapper mapper = createMapper();
    private String testOutputDir;

    @BeforeMethod
    public void setUp() throws IOException {
        testOutputDir = Files.createTempDirectory("maven-dep-tree-integration").toFile().getAbsolutePath();
    }

    @AfterMethod
    public void tearDown() throws IOException {
        FileUtils.forceDelete(new File(testOutputDir));
    }

    @Test
    public void testMultiModule() throws VerificationException, IOException, ParserConfigurationException, SAXException {
        // Run Mojo
        List<String> depsTreeOutputFiles = runMavenDepTree("multi-module", testOutputDir, pluginVersion);

        // Read output path
        assertEquals(depsTreeOutputFiles.size(), 4);

        // Deserialize maven dependency tree
        for (String depsTreeOutputFile : depsTreeOutputFiles) {
            MavenDepTreeResults mavenDependencyTree = mapper.readValue(new File(depsTreeOutputFile), MavenDepTreeResults.class);

            // Assert module in node list
            assertNotNull(mavenDependencyTree.getNodes().get(mavenDependencyTree.getRoot()));

            String root = mavenDependencyTree.getRoot();
            if (depsTreeOutputFile.contains("multi1")) {
                assertEquals(mavenDependencyTree.getNodes().size(), 14);
                MavenDependencyNode springCore = mavenDependencyTree.getNodes().get("org.springframework:spring-core:2.5.6");
                assertEquals(springCore.getConfigurations(), Sets.newHashSet("compile"));
            } else if (depsTreeOutputFile.contains("multi2")) {
                assertEquals(root, "org.jfrog.test:multi2:3.7-SNAPSHOT");
            } else if (depsTreeOutputFile.contains("multi3")) {
                assertEquals(root, "org.jfrog.test:multi3:3.7-SNAPSHOT");
            } else {
                assertEquals(root, "org.jfrog.test:multi:3.7-SNAPSHOT");
                assertEquals(mavenDependencyTree.getNodes().get("junit:junit:3.8.1"), new MavenDependencyNode("test", "jar"));
            }
        }
    }

    @Test
    public void testMavenArchetypeSimple() throws VerificationException, IOException {
        testMavenArchetype("maven-archetype-simple");
    }

    @Test
    public void testMavenArchetypeDependencyManagement() throws VerificationException, IOException {
        testMavenArchetype("maven-archetype-dependency-management");
    }

    private void testMavenArchetype(String projectName) throws VerificationException, IOException {
        // Run Mojo
        List<String> depsTreeOutputFile = runMavenDepTree(projectName, testOutputDir, pluginVersion);

        // Read output path
        assertEquals(depsTreeOutputFile.size(), 1);

        // Deserialize maven dependency tree
        MavenDepTreeResults mavenDependencyTree = mapper.readValue(new File(depsTreeOutputFile.get(0)), MavenDepTreeResults.class);

        // Check root
        assertEquals(mavenDependencyTree.getRoot(), "org.example:maven-archetype-simple:1.0-SNAPSHOT");

        // Check nodes
        Map<String, MavenDependencyNode> nodes = mavenDependencyTree.getNodes();
        assertEquals(nodes.size(), 2);
        MavenDependencyNode expectedFirstNode = new MavenDependencyNode();
        expectedFirstNode.addChild("junit:junit:3.8.1");
        assertEquals(nodes.get("org.example:maven-archetype-simple:1.0-SNAPSHOT"), expectedFirstNode);
        MavenDependencyNode expectedSecondNode = new MavenDependencyNode("test", "jar");
        assertEquals(nodes.get("junit:junit:3.8.1"), expectedSecondNode);
    }
}
