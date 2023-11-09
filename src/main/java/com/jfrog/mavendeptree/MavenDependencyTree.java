package com.jfrog.mavendeptree;

import com.jfrog.mavendeptree.dependenciesresults.MavenDepTreeResults;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static com.jfrog.mavendeptree.Utils.createDependencyTree;
import static com.jfrog.mavendeptree.Utils.writeResultsToFile;
import static java.lang.System.lineSeparator;

/**
 * @author yahavi
 */
@SuppressWarnings("unused")
@Mojo(name = "tree", requiresDependencyCollection = ResolutionScope.TEST)
public class MavenDependencyTree extends AbstractMojo {

    // Specify the system property to an empty file, where the paths to the output dependency trees will be written
    @Parameter(property = "depsTreeOutputFile", readonly = true, required = true)
    private File depsTreeOutputFile;

    // The Maven "project" we are currently working on, specifically as a submodule
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    // Maven execution session
    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    // A helper library that takes the project and session as inputs and constructs a Maven dependency tree
    @Component(hint = "default")
    private DependencyGraphBuilder dependencyGraphBuilder;

    @Override
    public void execute() throws MojoExecutionException {
        try (FileWriter fileWriter = new FileWriter(depsTreeOutputFile, true)) {
            MavenDepTreeResults results = createDependencyTree(dependencyGraphBuilder, session, project);
            File resultsPath = writeResultsToFile(project, results);
            fileWriter.append(resultsPath.getAbsolutePath()).append(lineSeparator());
        } catch (DependencyGraphBuilderException | IOException e) {
            throw new MojoExecutionException(ExceptionUtils.getRootCauseMessage(e), e);
        }
    }
}
