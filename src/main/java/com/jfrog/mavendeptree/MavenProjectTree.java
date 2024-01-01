package com.jfrog.mavendeptree;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static com.jfrog.mavendeptree.Utils.getGavString;
import static java.lang.System.lineSeparator;

/**
 * @author yahavi
 */
@SuppressWarnings("unused")
@Mojo(name = "projects", threadSafe = true)
public class MavenProjectTree extends AbstractMojo {
    @Parameter(property = "project.parentArtifact")
    private Artifact parent;

    @Parameter(property = "project.artifact")
    private Artifact artifact;

    @Parameter(property = "project.file")
    private File file;

    // Specify the system property to an empty file, where the projects command output will be written
    @Parameter(property = "depsTreeOutputFile", readonly = true)
    private File depsTreeOutputFile;

    @Override
    public void execute() throws MojoExecutionException {
        String gav = getGavString(artifact);
        String parentGav = getGavString(parent);
        String pomPath = file.getAbsolutePath();
        String projectInfo = String.format(
                "{\"gav\":\"%s\",\"parentGav\":\"%s\",\"pomPath\":\"%s\"}",
                gav, parentGav, pomPath
        );

        if (depsTreeOutputFile == null) {
            System.out.println(projectInfo);
            return;
        }

        try (FileWriter fileWriter = new FileWriter(depsTreeOutputFile, true)) {
            fileWriter.append(projectInfo).append(lineSeparator());
        } catch (IOException e) {
            String errorMessage = "Error writing to depsTreeOutputFile: " + e.getMessage();
            throw new MojoExecutionException(errorMessage, e);
        }
    }
}
