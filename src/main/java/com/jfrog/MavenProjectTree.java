package com.jfrog;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;

import static com.jfrog.Utils.getGavString;

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

    @Override
    public void execute() {
        String gav = getGavString(artifact);
        String parentGav = getGavString(parent);
        String pomPath = file.getAbsolutePath();
        System.out.printf("{\"gav\":\"%s\",\"parentGav\":\"%s\",\"pomPath\":\"%s\"}%n", gav, parentGav, pomPath);
    }
}
