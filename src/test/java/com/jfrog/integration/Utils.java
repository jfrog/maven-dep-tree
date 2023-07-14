package com.jfrog.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.apache.maven.it.util.ResourceExtractor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static com.jfrog.Utils.createMapper;

/**
 * @author yahavi
 */
public class Utils {
    static final ObjectMapper mapper = createMapper();

    /**
     * Run "tree" on a test project and return the path to the depsTreeOutputFile.
     *
     * @param projectName   - The test project to run
     * @param testOutputDir - Output test directory
     * @return the content of the generated 'depsTreeOutputFile' file.
     * @throws IOException           in case of any unexpected I/O error.
     * @throws VerificationException in case of any Maven Verifier error.
     */
    static List<String> runMavenDepTree(String projectName, String testOutputDir) throws IOException, VerificationException {
        Path depsTreeOutputFilePath = Paths.get(testOutputDir, "depsTreeOutputFile");

        File testDir = ResourceExtractor.simpleExtractResources(Utils.class, "/integration/" + projectName);
        Verifier verifier = new Verifier(testDir.getAbsolutePath());
        if (StringUtils.equalsIgnoreCase(System.getProperty("debugITs"), "true")) {
            verifier.setEnvironmentVariable("MAVEN_OPTS", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005");
        }
        verifier.executeGoals(Lists.newArrayList("clean", "com.jfrog:maven-dep-tree:tree", "-DdepsTreeOutputFile=" + depsTreeOutputFilePath));
        verifier.verifyErrorFreeLog();
        return FileUtils.readLines(depsTreeOutputFilePath.toFile(), StandardCharsets.UTF_8);
    }

    /**
     * Run "tree" on a test project and return the path to the depsTreeOutputFile.
     *
     * @param projectName - The test project to run
     * @return the output.
     * @throws IOException           in case of any unexpected I/O error.
     * @throws VerificationException in case of any Maven Verifier error.
     */
    static List<String> runMavenProjectTree(String projectName) throws IOException, VerificationException {
        File testDir = ResourceExtractor.simpleExtractResources(Utils.class, "/integration/" + projectName);
        Verifier verifier = new Verifier(testDir.getAbsolutePath());
        if (StringUtils.equalsIgnoreCase(System.getProperty("debugITs"), "true")) {
            verifier.setEnvironmentVariable("MAVEN_OPTS", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005");
        }
        verifier.executeGoals(Lists.newArrayList("clean", "com.jfrog:maven-dep-tree:projects", "-q"));
        verifier.verifyErrorFreeLog();
        return verifier.loadFile(verifier.getBasedir(), verifier.getLogFileName(), false);
    }
}
