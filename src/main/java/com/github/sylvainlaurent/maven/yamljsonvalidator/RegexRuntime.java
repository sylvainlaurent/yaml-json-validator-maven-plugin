package com.github.sylvainlaurent.maven.yamljsonvalidator;

import com.networknt.schema.SchemaContext;
import com.networknt.schema.regex.RegularExpression;
import com.networknt.schema.regex.RegularExpressionFactory;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Owns one isolated Graal context. No Graal types may leak into the plugin realm. */
final class RegexRuntime implements AutoCloseable, RegularExpressionFactory {
    private final RuntimeClassLoader loader;
    private final AutoCloseable context;
    private final RegularExpressionFactory delegate;
    private final Consumer<RegexRuntime> release;
    private boolean closed;

    RegexRuntime(RuntimeClassLoader loader, Consumer<RegexRuntime> release) {
        this.loader = loader;
        this.release = release;
        AutoCloseable created = null;
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(loader);
            Class<?> type = loader.loadClass("org.graalvm.polyglot.Context");
            Object builder = type.getMethod("newBuilder", String[].class).invoke(null, (Object) new String[] {"js"});
            builder.getClass().getMethod("option", String.class, String.class)
                    .invoke(builder, "engine.WarnInterpreterOnly", "false");
            created = (AutoCloseable) builder.getClass().getMethod("build").invoke(builder);
            delegate = (RegularExpressionFactory) loader
                    .loadClass("com.networknt.schema.regex.GraalJSRegularExpressionFactory")
                    .getConstructor(type).newInstance(created);
            context = created;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            Throwable cause = failure instanceof InvocationTargetException ? failure.getCause() : failure;
            if (created != null) {
                try { created.close(); } catch (Exception e) { cause.addSuppressed(e); }
            }
            throw new IllegalStateException("Could not initialize GraalJS on Java "
                    + System.getProperty("java.specification.version"), cause);
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    @Override
    public RegularExpression getRegularExpression(String regex) {
        RegularExpression expression = enter(() -> delegate.getRegularExpression(regex));
        return value -> enter(() -> expression.matches(value));
    }

    @Override
    public RegularExpression getRegularExpression(String regex, SchemaContext schemaContext) {
        RegularExpression expression = enter(() -> delegate.getRegularExpression(regex, schemaContext));
        return value -> enter(() -> expression.matches(value));
    }

    private <T> T enter(Supplier<T> action) {
        if (closed) {
            throw new IllegalStateException("Regex runtime is closed");
        }
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(loader);
            return action.get();
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(loader);
            context.close();
        } catch (Exception e) {
            throw new IllegalStateException("Could not close GraalJS runtime", e);
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
            release.accept(this);
        }
    }

    static final class RuntimeClassLoader extends URLClassLoader {
        RuntimeClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        private static boolean isolated(String name) {
            return name.startsWith("org.graalvm.") || name.startsWith("com.oracle.")
                    || name.startsWith("com.ibm.icu.")
                    || name.startsWith("com.networknt.schema.regex.GraalJS");
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> type = findLoadedClass(name);
                if (type == null) {
                    type = isolated(name) ? findClass(name) : super.loadClass(name, false);
                }
                if (resolve) {
                    resolveClass(type);
                }
                return type;
            }
        }

        private static boolean isolatedResource(String name) {
            return name.startsWith("META-INF/services/")
                    ? isolated(name.substring("META-INF/services/".length()))
                    : isolated(name.replace('/', '.'));
        }

        @Override
        public URL getResource(String name) {
            return isolatedResource(name) ? findResource(name) : super.getResource(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            return isolatedResource(name) ? findResources(name) : super.getResources(name);
        }
    }
}
