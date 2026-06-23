package com.jfrog.mavendeptree;

import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for the install-lifecycle filtering in {@link PluginDependencyResolver}.
 * {@link PluginDependencyResolver#isPluginInInstallLifecycle(Plugin)} is pure logic over the
 * Maven model (static, no collaborators), so it is called directly. Aether-backed resolution
 * is covered by the integration tests.
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
}
