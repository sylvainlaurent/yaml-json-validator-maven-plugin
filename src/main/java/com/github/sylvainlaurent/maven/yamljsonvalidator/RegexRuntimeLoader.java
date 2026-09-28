package com.github.sylvainlaurent.maven.yamljsonvalidator;

import com.networknt.schema.regex.RegularExpressionFactory;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.PreDestroy;
import javax.inject.Named;
import javax.inject.Singleton;

/** Container-owned loaders avoid loading Truffle's native helper twice in the same plugin realm. */
@Named
@Singleton
public final class RegexRuntimeLoader {
    private final Map<List<URL>, RegexRuntime.RuntimeClassLoader> loaders = new HashMap<>();
    private final Set<RegexRuntime> contexts = new HashSet<>();
    private boolean disposed;

    synchronized RegexRuntime create(Collection<URL> runtimeJars) {
        if (disposed) {
            throw new IllegalStateException("Regex runtime loader is disposed");
        }
        List<URL> urls = new ArrayList<>(runtimeJars);
        // Reload only Graal-specific adapter classes; share the regex SPI with the parent.
        urls.add(RegularExpressionFactory.class.getProtectionDomain().getCodeSource().getLocation());
        RegexRuntime.RuntimeClassLoader loader = loaders.get(urls);
        if (loader == null) {
            loader = new RegexRuntime.RuntimeClassLoader(urls.toArray(new URL[0]), getClass().getClassLoader());
            loaders.put(urls, loader);
        }
        RegexRuntime runtime = new RegexRuntime(loader, this::release);
        contexts.add(runtime);
        return runtime;
    }

    private synchronized void release(RegexRuntime runtime) {
        contexts.remove(runtime);
    }

    @PreDestroy
    public synchronized void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        RuntimeException failure = null;
        for (RegexRuntime runtime : new ArrayList<>(contexts)) {
            try {
                runtime.close();
            } catch (RuntimeException e) {
                if (failure == null) { failure = e; } else { failure.addSuppressed(e); }
            }
        }
        for (RegexRuntime.RuntimeClassLoader loader : loaders.values()) {
            try {
                loader.close();
            } catch (IOException e) {
                if (failure == null) {
                    failure = new IllegalStateException("Could not close GraalJS classloader", e);
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        contexts.clear();
        loaders.clear();
        if (failure != null) {
            throw failure;
        }
    }
}
