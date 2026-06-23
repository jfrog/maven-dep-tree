package com.jfrog.mavendeptree;

import com.jfrog.mavendeptree.dependenciesresults.MavenDepTreeResults;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.LifecycleExecutor;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.eclipse.aether.RepositorySystem;

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

    // Aether repository system for resolving plugin transitive dependencies
    @Component
    private RepositorySystem repositorySystem;

    // Used to discover lifecycle-default plugin bindings (e.g. maven-compiler-plugin on compile)
    @Component
    private LifecycleExecutor lifecycleExecutor;

    /**
     * When true, Maven build plugins that participate in the install lifecycle, together with
     * their transitive dependencies, are included in the output under the {@code pluginNodes}
     * field. These artifacts are downloaded during {@code mvn install} but are invisible to
     * {@code mvn dependency:tree}. Default is false to preserve existing behaviour.
     */
    @Parameter(property = "includePluginDeps", defaultValue = "false")
    private boolean includePluginDeps;

    @Override
    public void execute() throws MojoExecutionException {
        try (FileWriter fileWriter = new FileWriter(depsTreeOutputFile, true)) {
            MavenDepTreeResults results = createDependencyTree(dependencyGraphBuilder, session, project);

            if (includePluginDeps) {
                PluginDependencyResolver resolver = new PluginDependencyResolver(
                        repositorySystem, lifecycleExecutor, session, project, getLog());
                // Always set when enabled (even if empty) so the JSON is unambiguous:
                // null = off, {} = on but none found, populated = found.
                results.setPluginNodes(resolver.resolvePluginDependencies());
            }

            File resultsPath = writeResultsToFile(project, results);
            fileWriter.append(resultsPath.getAbsolutePath()).append(lineSeparator());
        } catch (DependencyGraphBuilderException | IOException e) {
            throw new MojoExecutionException(ExceptionUtils.getRootCauseMessage(e), e);
        }
    }
}
