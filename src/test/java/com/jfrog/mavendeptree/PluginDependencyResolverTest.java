package com.jfrog.mavendeptree;

import com.jfrog.mavendeptree.dependenciesresults.MavenDependencyNode;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactProperties;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.util.artifact.DefaultArtifactTypeRegistry;
import org.testng.annotations.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link PluginDependencyResolver}. Declared-dependency tests capture the
 * Aether request, so no real repository is needed.
 */
public class PluginDependencyResolverTest {

    @Test
    public void testExplicitInstallPhaseIncluded() {
        assertTrue(PluginDependencyResolver.isPluginInInstallLifecycle(plugin("org.example", "custom-plugin", "compile")));
        assertTrue(PluginDependencyResolver.isPluginInInstallLifecycle(plugin("org.example", "custom-plugin", "package")));
        assertTrue(PluginDependencyResolver.isPluginInInstallLifecycle(plugin("org.example", "custom-plugin", "verify")));
        assertTrue(PluginDependencyResolver.isPluginInInstallLifecycle(plugin("org.example", "custom-plugin", "integration-test")));
    }

    @Test
    public void testExplicitPostInstallPhaseExcluded() {
        assertFalse(PluginDependencyResolver.isPluginInInstallLifecycle(plugin("org.example", "custom-plugin", "deploy")));
        assertFalse(PluginDependencyResolver.isPluginInInstallLifecycle(plugin("org.example", "custom-plugin", "site")));
        assertFalse(PluginDependencyResolver.isPluginInInstallLifecycle(plugin("org.example", "custom-plugin", "clean")));
    }

    @Test
    public void testAnyInstallPhaseWinsOverPostInstall() {
        Plugin plugin = plugin("org.example", "custom-plugin", "deploy");
        plugin.addExecution(execution("package"));
        assertTrue(PluginDependencyResolver.isPluginInInstallLifecycle(plugin));
    }

    @Test
    public void testNoExecutionsIncludedByDefault() {
        assertTrue(PluginDependencyResolver.isPluginInInstallLifecycle(plugin("org.example", "custom-plugin", null)));
        assertTrue(PluginDependencyResolver.isPluginInInstallLifecycle(
                plugin("org.apache.maven.plugins", "maven-jar-plugin", null)));
    }

    @Test
    public void testNoExplicitPhaseFallsBackToDefault() {
        // Execution present but without a phase -> treated as default-bound, so included unless excluded.
        Plugin plugin = plugin("org.example", "custom-plugin", null);
        plugin.addExecution(execution(null));
        assertTrue(PluginDependencyResolver.isPluginInInstallLifecycle(plugin));
    }

    @Test
    public void testExcludedByDefaultWithoutExplicitPhase() {
        assertFalse(PluginDependencyResolver.isPluginInInstallLifecycle(
                plugin("org.apache.maven.plugins", "maven-deploy-plugin", null)));
        assertFalse(PluginDependencyResolver.isPluginInInstallLifecycle(
                plugin("org.apache.maven.plugins", "maven-site-plugin", null)));
        assertFalse(PluginDependencyResolver.isPluginInInstallLifecycle(
                plugin("org.apache.maven.plugins", "maven-gpg-plugin", null)));
    }

    @Test
    public void testExcludedByDefaultIsReincludedWhenBoundToInstallPhase() {
        // A user can re-bind an excluded plugin to an install-lifecycle phase.
        Plugin plugin = plugin("org.apache.maven.plugins", "maven-deploy-plugin", "package");
        assertTrue(PluginDependencyResolver.isPluginInInstallLifecycle(plugin));
    }

    @Test
    public void testDeclaredPluginDependencyIsAddedToCollectRequest() {
        // A pinned dependency must reach Aether as a direct dependency of the plugin.
        Plugin plugin = plugin("org.example", "custom-plugin", null);
        plugin.setVersion("1.0");
        plugin.addDependency(modelDependency("org.ow2.asm", "asm", "9.7"));

        CollectRequest collectRequest = collectRequest(plugin);
        assertEquals(artifactCoords(collectRequest.getRoot().getArtifact()), "org.example:custom-plugin:1.0");
        List<Dependency> declared = collectRequest.getDependencies();
        assertEquals(declared.size(), 1);
        assertEquals(artifactCoords(declared.get(0).getArtifact()), "org.ow2.asm:asm:9.7");
        assertEquals(declared.get(0).getScope(), "runtime");
    }

    @Test
    public void testFailedArtifactKeepsCollectedCoordinates() {
        Plugin plugin = plugin("org.example", "custom-plugin", null);
        plugin.setVersion("1.0");
        plugin.addDependency(modelDependency("org.ow2.asm", "asm", "9.7"));

        // Aether throws after collecting the graph when an artifact cannot be downloaded.
        Map<String, MavenDependencyNode> nodes = resolve(plugin, (proxy, method, args) -> {
            DependencyRequest request = (DependencyRequest) args[1];
            DefaultDependencyNode root = new DefaultDependencyNode(request.getCollectRequest().getRoot());
            for (Dependency declared : request.getCollectRequest().getDependencies()) {
                root.getChildren().add(new DefaultDependencyNode(declared));
            }
            throw new DependencyResolutionException(new DependencyResult(request).setRoot(root), new IllegalStateException("denied"));
        });

        assertEquals(nodes.size(), 2);
        assertTrue(nodes.containsKey("org.example:custom-plugin:1.0"));
        assertTrue(nodes.get("org.ow2.asm:asm:9.7").getConfigurations().contains("runtime"));
    }

    @Test
    public void testCollectExceptionSkipsPlugin() {
        Map<String, MavenDependencyNode> nodes = resolve(versionedPlugin(), (proxy, method, args) -> {
            DependencyRequest request = (DependencyRequest) args[1];
            DependencyResult result = new DependencyResult(request).setRoot(new DefaultDependencyNode(request.getCollectRequest().getRoot()));
            result.setCollectExceptions(Collections.singletonList(new IllegalStateException("range")));
            throw new DependencyResolutionException(result, new IllegalStateException("range"));
        });

        assertTrue(nodes.isEmpty());
    }

    @Test
    public void testNullResultSkipsPlugin() {
        Map<String, MavenDependencyNode> nodes = resolve(versionedPlugin(), (proxy, method, args) -> {
            throw new DependencyResolutionException(new DependencyResult((DependencyRequest) args[1]), "no result", new IllegalStateException("no result")) {
                @Override
                public DependencyResult getResult() {
                    return null;
                }
            };
        });

        assertTrue(nodes.isEmpty());
    }

    @Test
    public void testGenericExceptionSkipsPlugin() {
        Map<String, MavenDependencyNode> nodes = resolve(versionedPlugin(), (proxy, method, args) -> {
            throw new IllegalStateException("embedder");
        });

        assertTrue(nodes.isEmpty());
    }

    @Test
    public void testDeclaredDependencyKeepsExclusionClassifierAndType() {
        Plugin plugin = versionedPlugin();
        org.apache.maven.model.Dependency pinned = modelDependency("org.ow2.asm", "asm", "9.7");
        pinned.setOptional(true);
        pinned.setClassifier("sources");
        pinned.setType("test-jar");
        org.apache.maven.model.Exclusion exclusion = new org.apache.maven.model.Exclusion();
        exclusion.setGroupId("org.example");
        exclusion.setArtifactId("excluded");
        pinned.addExclusion(exclusion);
        plugin.addDependency(pinned);

        Dependency declared = collectRequest(plugin).getDependencies().get(0);
        assertEquals(declared.getScope(), "runtime");
        assertTrue(declared.isOptional());
        assertEquals(declared.getArtifact().getClassifier(), "sources");
        assertEquals(declared.getArtifact().getProperty(ArtifactProperties.TYPE, ""), "test-jar");
        assertEquals(declared.getExclusions().size(), 1);
        org.eclipse.aether.graph.Exclusion only = declared.getExclusions().iterator().next();
        assertEquals(only.getGroupId(), "org.example");
        assertEquals(only.getArtifactId(), "excluded");
    }

    @Test
    public void testSystemScopedDependencyIsSkipped() {
        Plugin plugin = plugin("org.example", "custom-plugin", null);
        plugin.setVersion("1.0");
        plugin.addDependency(modelDependency("org.ow2.asm", "asm", "9.7"));

        org.apache.maven.model.Dependency system = modelDependency("com.sun", "tools", "1.8");
        system.setScope("system");
        system.setSystemPath("/lib/tools.jar");
        plugin.addDependency(system);

        List<Dependency> declared = collectRequest(plugin).getDependencies();
        assertEquals(declared.size(), 1);
        assertEquals(artifactCoords(declared.get(0).getArtifact()), "org.ow2.asm:asm:9.7");
    }

    private static Plugin versionedPlugin() {
        Plugin plugin = plugin("org.example", "custom-plugin", null);
        plugin.setVersion("1.0");
        return plugin;
    }

    private static Plugin plugin(String groupId, String artifactId, String phase) {
        Plugin plugin = new Plugin();
        plugin.setGroupId(groupId);
        plugin.setArtifactId(artifactId);
        if (phase != null) {
            plugin.addExecution(execution(phase));
        }
        return plugin;
    }

    private static PluginExecution execution(String phase) {
        PluginExecution execution = new PluginExecution();
        execution.setPhase(phase);
        return execution;
    }

    /** Returns the collect request the resolver sends to Aether; the stub fails after capturing it. */
    private static CollectRequest collectRequest(Plugin plugin) {
        List<DependencyRequest> requests = new ArrayList<>();
        resolve(plugin, (proxy, method, args) -> {
            requests.add((DependencyRequest) args[1]);
            throw new IllegalStateException("captured dependency request");
        });

        assertEquals(requests.size(), 1);
        return requests.get(0).getCollectRequest();
    }

    /** Runs the resolver against a RepositorySystem whose calls are answered by the given handler. */
    private static Map<String, MavenDependencyNode> resolve(Plugin plugin, InvocationHandler repositorySystemHandler) {
        Build build = new Build();
        build.addPlugin(plugin);
        Model model = new Model();
        model.setBuild(build);
        MavenProject project = new MavenProject(model);

        DefaultRepositorySystemSession repoSession = new DefaultRepositorySystemSession();
        repoSession.setArtifactTypeRegistry(new DefaultArtifactTypeRegistry());
        MavenSession session = new MavenSession(null, repoSession,
                new DefaultMavenExecutionRequest(), new DefaultMavenExecutionResult());

        RepositorySystem repositorySystem = (RepositorySystem) Proxy.newProxyInstance(
                RepositorySystem.class.getClassLoader(), new Class<?>[]{RepositorySystem.class}, repositorySystemHandler);

        return new PluginDependencyResolver(repositorySystem, null, session, project, new SystemStreamLog())
                .resolvePluginDependencies();
    }

    private static org.apache.maven.model.Dependency modelDependency(String groupId, String artifactId, String version) {
        org.apache.maven.model.Dependency dependency = new org.apache.maven.model.Dependency();
        dependency.setGroupId(groupId);
        dependency.setArtifactId(artifactId);
        dependency.setVersion(version);
        return dependency;
    }

    private static String artifactCoords(Artifact artifact) {
        return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
    }
}
