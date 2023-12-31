package com.jfrog.mavendeptree.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.apache.maven.it.util.ResourceExtractor;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static com.jfrog.mavendeptree.Utils.createMapper;
import static org.testng.Assert.fail;

/**
 * @author yahavi
 */
public class Utils {
    static final ObjectMapper mapper = createMapper();

    /**
     * Extract the plugin version from pom.xml
     *
     * @return the plugin version
     */
    static String getPluginVersion() {
        File pomFile = new File("pom.xml");
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(pomFile);
            return doc.getElementsByTagName("version").item(0).getFirstChild().getNodeValue();
        } catch (Exception e) {
            fail(ExceptionUtils.getRootCauseMessage(e));
            return "";
        }
    }

    /**
     * Run "tree" on a test project and return the path to the depsTreeOutputFile.
     *
     * @param projectName   - The test project to run
     * @param testOutputDir - Output test directory
     * @param pluginVersion - The plugin version
     * @return the content of the generated 'depsTreeOutputFile' file.
     * @throws IOException           in case of any unexpected I/O error.
     * @throws VerificationException in case of any Maven Verifier error.
     */
    static List<String> runMavenDepTree(String projectName, String testOutputDir, String pluginVersion) throws IOException, VerificationException {
        Path depsTreeOutputFilePath = Paths.get(testOutputDir, "depsTreeOutputFile");

        File testDir = ResourceExtractor.simpleExtractResources(Utils.class, "/integration/" + projectName);
        Verifier verifier = new Verifier(testDir.getAbsolutePath());
        if (StringUtils.equalsIgnoreCase(System.getProperty("debugITs"), "true")) {
            verifier.setEnvironmentVariable("MAVEN_OPTS", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005");
        }
        verifier.executeGoals(Lists.newArrayList("clean", "com.jfrog:maven-dep-tree:" + pluginVersion + ":tree", "-DdepsTreeOutputFile=" + depsTreeOutputFilePath));
        verifier.verifyErrorFreeLog();
        return FileUtils.readLines(depsTreeOutputFilePath.toFile(), StandardCharsets.UTF_8);
    }

    /**
     * Run "tree" on a test project and return the path to the depsTreeOutputFile.
     *
     * @param projectName   - The test project to run
     * @param pluginVersion - The plugin version
     * @return the output.
     * @throws IOException           in case of any unexpected I/O error.
     * @throws VerificationException in case of any Maven Verifier error.
     */
    static List<String> runMavenProjectTree(String projectName, String pluginVersion, Boolean withOutputFile) throws IOException, VerificationException {
        try {
            File testDir = ResourceExtractor.simpleExtractResources(Utils.class, "/integration/" + projectName);
            Verifier verifier = new Verifier(testDir.getAbsolutePath());

            if (StringUtils.equalsIgnoreCase(System.getProperty("debugITs"), "true")) {
                verifier.setEnvironmentVariable("MAVEN_OPTS", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005");
            }

            String outputFile = verifier.getLogFileName();
            List<String> goals = Lists.newArrayList("clean", "com.jfrog:maven-dep-tree:" + pluginVersion + ":projects", "-q");

            if (withOutputFile) {
                goals.add("-DdepsTreeOutputFile=" + testDir.getAbsolutePath() + "/testOutput.out");
                outputFile = "testOutput.out";
            }

            verifier.executeGoals(goals);
            verifier.verifyErrorFreeLog();
            List<String> results = verifier.loadFile(verifier.getBasedir(), outputFile, false);

            if (withOutputFile) {
                Files.deleteIfExists(Path.of(testDir.getAbsolutePath(), outputFile));
            }

            return results;
        } catch (IOException | VerificationException e) {
            throw e;
        }
    }
}
