package com.jfrog.mavendeptree.integration;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.it.VerificationException;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

import static com.jfrog.mavendeptree.integration.Utils.*;
import static org.testng.Assert.*;

/**
 * @author yahavi
 */
public class MavenProjectTreeITest {
    private final String pluginVersion = getPluginVersion();

    @Test
    public void testMultiModule() throws VerificationException, IOException {
        // Run Mojo
        List<String> projectInfos = runMavenProjectTree("multi-module", pluginVersion);

        // Test output
        assertEquals(projectInfos.size(), 4);
        for (String projectInfoJson : projectInfos) {
            ProjectInfo projectInfo = mapper.readValue(escapePathInWindows(projectInfoJson), ProjectInfo.class);
            switch (projectInfo.getGav()) {
                case "org.jfrog.test:multi:3.7-SNAPSHOT":
                    assertEquals(projectInfo.getParentGav(), "");
                    assertTrue(StringUtils.endsWith(projectInfo.getPomPath(), Paths.get("multi-module", "pom.xml").toString()));
                    break;
                case "org.jfrog.test:multi1:3.7-SNAPSHOT":
                    assertEquals(projectInfo.getParentGav(), "org.jfrog.test:multi:3.7-SNAPSHOT");
                    assertTrue(StringUtils.endsWith(projectInfo.getPomPath(), Paths.get("multi-module", "multi1", "pom.xml").toString()));
                    break;
                case "org.jfrog.test:multi2:3.7-SNAPSHOT":
                    assertEquals(projectInfo.getParentGav(), "org.jfrog.test:multi:3.7-SNAPSHOT");
                    assertTrue(StringUtils.endsWith(projectInfo.getPomPath(), Paths.get("multi-module", "multi2", "pom.xml").toString()));
                    break;
                case "org.jfrog.test:multi3:3.7-SNAPSHOT":
                    assertEquals(projectInfo.getParentGav(), "org.jfrog.test:multi:3.7-SNAPSHOT");
                    assertTrue(StringUtils.endsWith(projectInfo.getPomPath(), Paths.get("multi-module", "multi3", "pom.xml").toString()));
                    break;
                default:
                    fail("Unexpected GAV: " + projectInfo.getGav());
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
        List<String> projectInfoJson = runMavenProjectTree(projectName, pluginVersion);

        // Test output
        assertEquals(projectInfoJson.size(), 1);
        ProjectInfo projectInfo = mapper.readValue(escapePathInWindows(projectInfoJson.get(0)), ProjectInfo.class);
        assertEquals(projectInfo.getGav(), "org.example:maven-archetype-simple:1.0-SNAPSHOT");
        assertEquals(projectInfo.getParentGav(), "");
        assertTrue(StringUtils.endsWith(projectInfo.getPomPath(), Paths.get(projectName, "pom.xml").toString()));
    }

    private static String escapePathInWindows(String path) {
        return path.replaceAll("\\\\", "\\\\\\\\");
    }

    @NoArgsConstructor
    @Getter
    @Setter
    public static class ProjectInfo {
        String gav;
        String parentGav;
        String pomPath;
    }
}
