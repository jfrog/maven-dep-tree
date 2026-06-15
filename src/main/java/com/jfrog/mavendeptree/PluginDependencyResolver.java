package com.jfrog.mavendeptree;

import com.jfrog.mavendeptree.dependenciesresults.MavenDependencyNode;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.LifecycleExecutor;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.ArtifactProperties;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves Maven build plugins that participate in the {@code install} lifecycle, together
 * with their transitive dependencies, using Maven's Aether repository system. Both the plugin
 * artifacts themselves and their transitive closure are reported.
 *
 * <p>Both explicitly-declared POM plugins and lifecycle-default bindings (e.g. the
 * maven-compiler-plugin bound to {@code compile} by default for {@code jar} packaging)
 * are included. This mirrors what {@code mvn dependency:resolve-plugins} reports.
 *
 * <p>The resolver is intentionally non-fatal: any plugin or artifact that cannot be
 * resolved is logged and skipped, so the regular dependency tree is never broken.
 */
public class PluginDependencyResolver {

    /**
     * Phases NOT executed by {@code mvn install} (the entire clean and site lifecycles, plus
     * the default lifecycle's post-install {@code deploy}). A plugin whose every execution
     * targets only these is excluded. {@code integration-test}/{@code verify} run before
     * install, so they are intentionally not listed.
     */
    private static final Set<String> PHASES_NOT_IN_INSTALL = new HashSet<>(Arrays.asList(
            "pre-clean", "clean", "post-clean",
            "pre-site", "site", "post-site", "site-deploy",
            "deploy",
            // "none" is the Maven idiom to disable an execution (not a real lifecycle phase).
            "none"
    ));

    /**
     * Plugins excluded by default because their default-bound lifecycle phase is past
     * {@code install} (or outside it), even when the effective POM declares them without
     * explicit {@code <executions>}. Excluded unless the user explicitly re-binds them
     * to an earlier phase.
     */
    private static final Set<String> EXCLUDED_PLUGINS_BY_DEFAULT = new HashSet<>(Arrays.asList(
            "org.apache.maven.plugins:maven-deploy-plugin",
            "org.apache.maven.plugins:maven-site-plugin",
            "org.apache.maven.plugins:maven-release-plugin",
            "org.apache.maven.plugins:maven-gpg-plugin"
    ));

    private final RepositorySystem repositorySystem;
    private final LifecycleExecutor lifecycleExecutor;
    private final MavenSession session;
    private final MavenProject project;
    private final Log log;

    public PluginDependencyResolver(RepositorySystem repositorySystem,
                                    LifecycleExecutor lifecycleExecutor,
                                    MavenSession session,
                                    MavenProject project,
                                    Log log) {
        this.repositorySystem = repositorySystem;
        this.lifecycleExecutor = lifecycleExecutor;
        this.session = session;
        this.project = project;
        this.log = log;
    }

    /**
     * Returns a map of {@code "groupId:artifactId:version" -> MavenDependencyNode} for every build
     * plugin that participates in the install lifecycle and each of its transitive dependencies
     * (the plugin artifacts themselves are included alongside their closure).
     *
     * <p>Both explicitly-declared plugins (from {@code project.getBuildPlugins()}) and
     * lifecycle-default bindings are included, deduped by GAV.
     */
    public Map<String, MavenDependencyNode> resolvePluginDependencies() {
        Map<String, MavenDependencyNode> pluginDeps = new LinkedHashMap<>();

        // Collect all effective plugins: explicitly declared + lifecycle-default bindings.
        Map<String, Plugin> effectivePlugins = collectEffectivePlugins();
        if (effectivePlugins.isEmpty()) {
            log.debug("[mvn-plugin-deps] no build plugins found for this project");
            return pluginDeps;
        }

        // Resolved once and reused for every plugin. It can be null on unusual embedder/IDE
        // invocations; bail out gracefully rather than letting Aether throw an NPE.
        RepositorySystemSession repoSession = session.getRepositorySession();
        if (repoSession == null) {
            log.debug("[mvn-plugin-deps] no repository session available; skipping plugin dependency resolution");
            return pluginDeps;
        }

        int filteredOut = 0;
        for (Plugin plugin : effectivePlugins.values()) {
            String coord = plugin.getGroupId() + ":" + plugin.getArtifactId();
            if (!isPluginInInstallLifecycle(plugin)) {
                log.debug("[mvn-plugin-deps] plugin filtered (not in install lifecycle): " + coord);
                filteredOut++;
                continue;
            }
            log.debug("[mvn-plugin-deps] resolving transitive deps for plugin: " + coord + ":" + plugin.getVersion());
            int before = pluginDeps.size();
            resolvePluginTransitiveDeps(plugin, repoSession, pluginDeps);
            log.debug(String.format("[mvn-plugin-deps]   -> %d new transitive deps collected (total so far: %d)",
                    pluginDeps.size() - before, pluginDeps.size()));
        }

        log.debug(String.format("[mvn-plugin-deps] DONE: %d plugin transitive deps included after install-lifecycle filter (%d plugins filtered out)",
                pluginDeps.size(), filteredOut));
        return pluginDeps;
    }

    /**
     * Returns a merged map of all effective build plugins for this project:
     * explicitly declared ones from the POM plus lifecycle-default bindings obtained
     * via {@link LifecycleExecutor#calculateExecutionPlan}.
     * Keyed by {@code "groupId:artifactId"}.
     *
     * <p>If the execution plan can't be calculated, falls back to plugin-management defaults
     * so blocked plugin dependencies are still not missed (matches the previous CLI behaviour).
     * That fallback is permissive and may over-report (see {@link #addPluginManagementFallback}).
     */
    private Map<String, Plugin> collectEffectivePlugins() {
        Map<String, Plugin> plugins = new LinkedHashMap<>();

        // 1. Explicitly declared plugins from the POM (including inherited from parent).
        List<Plugin> buildPlugins = project.getBuildPlugins();
        if (buildPlugins != null) {
            for (Plugin p : buildPlugins) {
                plugins.put(p.getGroupId() + ":" + p.getArtifactId(), p);
            }
        }

        // 2. Lifecycle-default bindings — e.g. maven-compiler-plugin bound to 'compile'
        //    for jar packaging. These don't appear in getBuildPlugins() unless explicitly
        //    declared, but ARE downloaded and used during mvn install. The execution plan
        //    is the most accurate source for both the plugin set and their resolved versions.
        try {
            for (MojoExecution execution : lifecycleExecutor
                    .calculateExecutionPlan(session, "install")
                    .getMojoExecutions()) {
                addPlugin(plugins, execution.getPlugin(), "lifecycle-default");
            }
        } catch (Exception e) {
            // Broad by design: calculateExecutionPlan throws many checked exceptions and can also
            // fail at runtime (offline/curation-restricted). Fall back to plugin-management defaults
            // rather than dropping the lifecycle-default plugins this feature exists to scan.
            log.warn("[mvn-plugin-deps] could not calculate the install execution plan; falling back to " +
                    "plugin-management defaults. No blocked plugin dependency will be missed, but results may " +
                    "over-report: plugins declared in <pluginManagement> but not actually executed by 'mvn install' " +
                    "can contribute transitive dependencies. Reason: " + e.getMessage());
            addPluginManagementFallback(plugins);
        }

        return plugins;
    }

    /**
     * Adds the effective plugin-management plugins (which include the super-POM defaults
     * and their versions) as a permissive fallback when the execution plan is unavailable.
     * Plugins that still lack a version are skipped later during resolution.
     *
     * <p>Note: {@code <pluginManagement>} only declares versions/config; it does not bind
     * plugins to the build. So this can include plugins that {@code mvn install} would never
     * execute, causing transitive deps to be over-reported. This is an accepted trade-off on
     * the (rare) execution-plan failure path — it favours not missing a blocked dependency
     * over precision — and is surfaced to the user via a warning at the call site.
     */
    private void addPluginManagementFallback(Map<String, Plugin> plugins) {
        if (project.getPluginManagement() == null) {
            return;
        }
        for (Plugin p : project.getPluginManagement().getPlugins()) {
            addPlugin(plugins, p, "fallback plugin-management");
        }
    }

    private void addPlugin(Map<String, Plugin> plugins, Plugin plugin, String source) {
        String key = plugin.getGroupId() + ":" + plugin.getArtifactId();
        if (!plugins.containsKey(key)) {
            plugins.put(key, plugin);
            log.debug("[mvn-plugin-deps] " + source + " plugin: " + key + ":" + plugin.getVersion());
        }
    }

    /**
     * Returns true when the plugin participates in {@code mvn install}. With no explicit
     * {@code <phase>}, the plugin is included unless it is in {@link #EXCLUDED_PLUGINS_BY_DEFAULT}
     * — deliberately erring toward inclusion (a false positive is safer than missing a blocked
     * dependency), matching the previous CLI behaviour.
     */
    static boolean isPluginInInstallLifecycle(Plugin plugin) {
        String coord = plugin.getGroupId() + ":" + plugin.getArtifactId();
        List<PluginExecution> executions = plugin.getExecutions();

        // Stage 1: an explicitly-bound execution on an install-lifecycle phase means the plugin
        // participates. An execution with no phase isn't "explicit" - it inherits the default
        // binding (stage 2). Phases in PHASES_NOT_IN_INSTALL (and "none") never participate.
        boolean hasExplicitPhase = false;
        for (PluginExecution execution : executions) {
            String phase = execution.getPhase();
            if (phase == null || phase.isEmpty()) {
                continue;
            }
            hasExplicitPhase = true;
            if (!PHASES_NOT_IN_INSTALL.contains(phase)) {
                return true;
            }
        }

        // Stage 2: no explicit phase - fall back to the default binding, included unless excluded.
        if (!hasExplicitPhase) {
            return !EXCLUDED_PLUGINS_BY_DEFAULT.contains(coord);
        }

        // Explicit phase(s) present but all non-install -> does not participate.
        return false;
    }

    /**
     * Resolves the given plugin and its transitive artifact closure via Aether and populates
     * {@code pluginDeps}. The plugin artifact itself is included; GAVs already collected
     * (keyed including classifier) are skipped to avoid duplicates.
     */
    private void resolvePluginTransitiveDeps(Plugin plugin, RepositorySystemSession repoSession, Map<String, MavenDependencyNode> pluginDeps) {
        String groupId = plugin.getGroupId();
        String artifactId = plugin.getArtifactId();
        String version = plugin.getVersion();

        if (version == null || version.isEmpty()) {
            log.debug("[mvn-plugin-deps] skipping plugin with no version: " + groupId + ":" + artifactId);
            return;
        }

        Dependency pluginDep = new Dependency(
                new DefaultArtifact(groupId, artifactId, "jar", version), null);

        CollectRequest collectRequest = new CollectRequest(pluginDep, project.getRemotePluginRepositories());
        DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, null);

        DependencyResult result;
        try {
            result = repositorySystem.resolveDependencies(repoSession, dependencyRequest);
        } catch (Exception e) {
            // Broad on purpose: a single rogue plugin (resolution failure, or an NPE from an
            // odd embedder session) must never break the whole dependency tree.
            log.warn("[mvn-plugin-deps] failed to resolve plugin " + groupId + ":" + artifactId + ":" + version + " — " + e.getMessage());
            return;
        }

        // PreorderNodeListGenerator flattens the resolved graph into a de-duplicated node list,
        // correctly handling diamond dependencies (shared sub-trees are visited once, not dropped).
        PreorderNodeListGenerator nlg = new PreorderNodeListGenerator();
        result.getRoot().accept(nlg);
        for (DependencyNode dn : nlg.getNodes()) {
            org.eclipse.aether.artifact.Artifact a = dn.getArtifact();
            if (a == null) {
                continue;
            }
            // The resolution root is the plugin artifact itself, included alongside its transitive
            // deps: the plugin JAR is downloaded during 'mvn install' and is itself a curation candidate.
            String gav = pluginDepKey(a);
            if (pluginDeps.containsKey(gav)) {
                continue;
            }
            String classifier = a.getClassifier();
            String scope = dn.getDependency() != null ? dn.getDependency().getScope() : null;
            // Use the Maven artifact type (e.g. "test-jar", "maven-plugin") to match the semantics
            // of the regular dependency nodes; fall back to the Aether extension when absent.
            String type = a.getProperty(ArtifactProperties.TYPE, a.getExtension());
            pluginDeps.put(gav, new MavenDependencyNode(scope, type,
                    (classifier != null && !classifier.isEmpty()) ? classifier : null));
        }
    }

    /**
     * Builds the dedup key for a resolved plugin dependency. Includes the classifier so that
     * classifier-distinct artifacts (e.g. a {@code :tests} jar) do not collide. Mirrors the
     * {@code groupId:artifactId:version[-classifier]} format used by {@code Utils.getGavString}.
     */
    private static String pluginDepKey(org.eclipse.aether.artifact.Artifact a) {
        String gav = a.getGroupId() + ":" + a.getArtifactId() + ":" + a.getVersion();
        String classifier = a.getClassifier();
        return (classifier != null && !classifier.isEmpty()) ? gav + "-" + classifier : gav;
    }
}
