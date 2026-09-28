package com.github.sylvainlaurent.maven.yamljsonvalidator;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;

/** Resolves only the runtime selected for Maven's JVM, using Maven's own session. */
final class RegexRuntimeResolver {
    private final RepositorySystem system;
    private final RepositorySystemSession session;
    private final List<RemoteRepository> repositories;

    RegexRuntimeResolver(RepositorySystem system, RepositorySystemSession session,
                         List<RemoteRepository> repositories) {
        this.system = system;
        this.session = session;
        this.repositories = repositories;
    }

    static Dependency dependency(String javaVersion) {
        int feature = Integer.parseInt(javaVersion.startsWith("1.")
                ? javaVersion.substring(2) : javaVersion);
        if (feature < 17) {
            return new Dependency(new DefaultArtifact("org.graalvm.js:js:21.3.19"), "runtime");
        }
        return new Dependency(new DefaultArtifact("org.graalvm.polyglot:js-community:pom:25.0.4"),
                "runtime", false, Collections.singletonList(
                        new Exclusion("org.graalvm.truffle", "truffle-runtime", "*", "*")));
    }

    Set<URL> resolve() {
        Dependency root = dependency(System.getProperty("java.specification.version"));
        CollectRequest collect = new CollectRequest(root, repositories);
        collect.setRequestContext("plugin");
        DependencyRequest request = new DependencyRequest(collect, (node, parents) -> {
            Dependency dependency = node.getDependency();
            return dependency == null || "compile".equals(dependency.getScope())
                    || "runtime".equals(dependency.getScope());
        });
        try {
            Set<URL> jars = new LinkedHashSet<>();
            for (ArtifactResult result : system.resolveDependencies(session, request).getArtifactResults()) {
                Artifact artifact = result.getArtifact();
                if ("jar".equals(artifact.getExtension())) {
                    jars.add(artifact.getFile().toURI().toURL());
                }
            }
            return jars;
        } catch (DependencyResolutionException | MalformedURLException e) {
            throw new IllegalStateException("Could not resolve GraalJS runtime " + root.getArtifact()
                    + (session.isOffline() ? " while Maven is offline; populate the local repository online first" : "")
                    + ": " + e.getMessage(), e);
        }
    }
}
