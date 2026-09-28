package com.github.sylvainlaurent.maven.yamljsonvalidator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.AbsoluteIri;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaException;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.resource.InputStreamSource;
import com.networknt.schema.resource.IriResourceLoader;
import com.networknt.schema.resource.SchemaLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.function.Supplier;

/** Shares schema mappings, retrieval policy and the schema cache across validation sets. */
final class SchemaResolver implements AutoCloseable {
    private final File basedir;
    private final boolean allowRemoteSchemas;
    private final boolean offline;
    private final SchemaLoader loader;
    private SchemaRegistry registry;
    private final Supplier<RegexRuntime> runtimeFactory;
    private RegexRuntime runtime;
    private boolean closed;

    SchemaResolver(File basedir, SchemaMapping[] mappings, boolean allowRemoteSchemas, boolean offline,
                   Supplier<RegexRuntime> runtimeFactory) {
        this.basedir = basedir;
        this.allowRemoteSchemas = allowRemoteSchemas;
        this.offline = offline;
        this.runtimeFactory = runtimeFactory;
        SchemaCatalog catalog = new SchemaCatalog(basedir, mappings);
        loader = new ExactClasspathSchemaLoader(Collections.singletonList(catalog),
                Collections.singletonList(this::getResource));
    }

    Schema load(String schema) {
        ensureOpen();
        if (schema == null || schema.isEmpty()) {
            return null;
        }
        String location = schema;
        if (!SchemaCatalog.hasScheme(schema)) {
            File file = new File(schema);
            if (!file.isAbsolute()) {
                file = new File(basedir, schema);
            }
            if (file.isFile()) {
                location = file.toURI().toString();
            } else {
                try {
                    location = new URI("classpath", schema, null).toASCIIString();
                } catch (URISyntaxException e) {
                    throw new IllegalArgumentException("Invalid schema location: " + schema, e);
                }
            }
        }
        // Keep the original identifier as the base for relative references; map only at retrieval time.
        return registry().getSchema(SchemaLocation.of(location));
    }

    Schema loadStream(InputStream schema, URI documentUri) throws IOException {
        ensureOpen();
        if (schema == null) {
            return null;
        }
        if (documentUri == null || !documentUri.isAbsolute() || documentUri.isOpaque()
                || documentUri.getFragment() != null) {
            throw new IllegalArgumentException("Streamed schemas require an absolute hierarchical document URI without a fragment");
        }
        return registry().getSchema(SchemaLocation.of(documentUri.toASCIIString()),
                new ObjectMapper().readTree(schema));
    }

    private SchemaRegistry registry() {
        if (registry == null) {
            RegexRuntime created = runtimeFactory.get();
            try {
                SchemaRegistryConfig config = SchemaRegistryConfig.builder()
                        .regularExpressionFactory(created).build();
                registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_4,
                        builder -> builder.schemaLoader(loader).schemaRegistryConfig(config));
                runtime = created;
            } catch (Throwable e) {
                try {
                    created.close();
                } catch (Throwable closeFailure) {
                    if (closeFailure != e) {
                        e.addSuppressed(closeFailure);
                    }
                }
                throw e;
            }
        }
        return registry;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Schema resolver is closed");
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            registry = null;
            if (runtime != null) {
                runtime.close();
                runtime = null;
            }
        }
    }

    private InputStreamSource getResource(AbsoluteIri location) {
        String scheme = location.getScheme();
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            // This receives the mapped destination, so local copies remain usable offline.
            if (offline || !allowRemoteSchemas) {
                throw new SchemaException("Network access to schema [" + location + "] is disabled: "
                        + (offline ? "Maven is offline" : "allowRemoteSchemas is false"));
            }
        } else if (!"file".equalsIgnoreCase(scheme)) {
            throw new SchemaException("Unsupported schema location: " + location);
        }
        return IriResourceLoader.getInstance().getResource(location);
    }
}
