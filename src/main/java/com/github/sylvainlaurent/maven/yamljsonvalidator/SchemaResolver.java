package com.github.sylvainlaurent.maven.yamljsonvalidator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.AbsoluteIri;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaException;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.regex.GraalJSRegularExpressionFactory;
import com.networknt.schema.resource.InputStreamSource;
import com.networknt.schema.resource.IriResourceLoader;
import com.networknt.schema.resource.SchemaLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.graalvm.polyglot.Context;

/** Shares schema mappings, retrieval policy and the schema cache across validation sets. */
final class SchemaResolver implements AutoCloseable {
    private final File basedir;
    private final boolean allowRemoteSchemas;
    private final boolean offline;
    private final SchemaLoader loader;
    private final Set<Schema> checkedSchemas = Collections.newSetFromMap(new IdentityHashMap<Schema, Boolean>());
    private SchemaRegistry registry;
    private Context context;
    private boolean closed;

    SchemaResolver(File basedir, SchemaMapping[] mappings, boolean allowRemoteSchemas, boolean offline) {
        this.basedir = basedir;
        this.allowRemoteSchemas = allowRemoteSchemas;
        this.offline = offline;
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
        return prepare(registry().getSchema(SchemaLocation.of(location)), location);
    }

    Schema loadStream(InputStream schema) throws IOException {
        ensureOpen();
        if (schema == null) {
            return null;
        }
        return prepare(registry().getSchema(new ObjectMapper().readTree(schema)),
                "input stream");
    }

    private Schema prepare(Schema schema, String location) {
        if (!checkedSchemas.contains(schema)) {
            String dialect = schema.getSchemaContext().getDialect().getId();
            Schema metaSchema = registry().getSchema(SchemaLocation.of(dialect));
            List<Error> errors = metaSchema.validate(schema.getSchemaNode(), execution ->
                    execution.executionConfig(config -> config.formatAssertionsEnabled(true)));
            if (!errors.isEmpty()) {
                throw new SchemaException("Invalid schema [" + location + "]: " + errors);
            }
            checkedSchemas.add(schema);
        }
        return schema;
    }

    private SchemaRegistry registry() {
        if (registry == null) {
            Context created = Context.newBuilder("js").option("engine.WarnInterpreterOnly", "false").build();
            try {
                SchemaRegistryConfig config = SchemaRegistryConfig.builder()
                        .regularExpressionFactory(new GraalJSRegularExpressionFactory(created)).build();
                registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_4,
                        builder -> builder.schemaLoader(loader).schemaRegistryConfig(config));
                context = created;
            } catch (RuntimeException | java.lang.Error e) {
                created.close();
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
            checkedSchemas.clear();
            registry = null;
            if (context != null) {
                context.close();
                context = null;
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
