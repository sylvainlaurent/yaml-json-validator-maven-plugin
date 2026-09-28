package com.github.sylvainlaurent.maven.yamljsonvalidator;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RegexRuntimeResolverTest {
    @Test
    void selects_pinned_runtime_at_java17_boundary() {
        for (String java : new String[] {"1.8", "9", "11", "16"}) {
            Dependency dependency = RegexRuntimeResolver.dependency(java);
            assertEquals("org.graalvm.js:js:jar:21.3.19", dependency.getArtifact().toString());
        }
        for (String java : new String[] {"17", "21", "23", "25", "26"}) {
            Dependency dependency = RegexRuntimeResolver.dependency(java);
            assertEquals("org.graalvm.polyglot:js-community:pom:25.0.4", dependency.getArtifact().toString());
            assertEquals("truffle-runtime", dependency.getExclusions().iterator().next().getArtifactId());
        }
    }

    @Test
    void uses_the_exact_maven_session_and_plugin_repositories_and_loads_only_jars() throws Exception {
        RepositorySystemSession session = session(false);
        List<RemoteRepository> repositories = Collections.singletonList(
                new RemoteRepository.Builder("configured-plugin-repo", "default", "file:/test/repository").build());
        File jar = new File("runtime.jar");
        RepositorySystem system = system((actualSession, request) -> {
            assertSame(session, actualSession);
            assertEquals(repositories, request.getCollectRequest().getRepositories());
            assertEquals("plugin", request.getCollectRequest().getRequestContext());
            assertEquals(RegexRuntimeResolver.dependency(System.getProperty("java.specification.version")),
                    request.getCollectRequest().getRoot());
            return new DependencyResult(request).setArtifactResults(Arrays.asList(
                    new ArtifactResult(new ArtifactRequest()).setArtifact(new DefaultArtifact("test:runtime:1").setFile(jar)),
                    new ArtifactResult(new ArtifactRequest()).setArtifact(new DefaultArtifact("test:runtime:pom:1").setFile(new File("runtime.pom")))));
        });
        assertEquals(Collections.singleton(jar.toURI().toURL()), new RegexRuntimeResolver(system, session, repositories).resolve());
    }

    @Test
    void offline_resolution_failure_identifies_the_runtime_and_missing_artifact() {
        RepositorySystem system = system((session, request) -> {
            throw new DependencyResolutionException(new DependencyResult(request),
                    new IllegalStateException("Missing test:runtime:jar:1"));
        });
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new RegexRuntimeResolver(system, session(true), Collections.emptyList()).resolve());
        assertTrue(error.getMessage().contains("offline"));
        assertTrue(error.getMessage().contains("Missing test:runtime:jar:1"));
        assertTrue(error.getMessage().contains("org.graalvm"));
    }

    private RepositorySystemSession session(boolean offline) {
        return (RepositorySystemSession) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {RepositorySystemSession.class}, (proxy, method, arguments) -> {
                    if ("isOffline".equals(method.getName())) {
                        return offline;
                    }
                    throw new AssertionError(method);
                });
    }

    private interface Resolve {
        DependencyResult resolve(RepositorySystemSession session, DependencyRequest request) throws Exception;
    }

    private RepositorySystem system(Resolve resolve) {
        return (RepositorySystem) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {RepositorySystem.class}, (proxy, method, arguments) -> {
                    if ("resolveDependencies".equals(method.getName())) {
                        return resolve.resolve((RepositorySystemSession) arguments[0], (DependencyRequest) arguments[1]);
                    }
                    throw new AssertionError(method);
                });
    }
}
