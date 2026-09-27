package com.github.sylvainlaurent.maven.yamljsonvalidator;

import com.networknt.schema.AbsoluteIri;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaException;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.resource.InputStreamSource;
import com.networknt.schema.resource.IriResourceLoader;
import com.networknt.schema.resource.SchemaLoader;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Locale;

/** Shares schema mappings, retrieval policy and the schema cache across validation sets. */
final class SchemaResolver {
    private final File basedir;
    private final boolean allowRemoteSchemas;
    private final boolean offline;
    private final SchemaRegistry registry;

    SchemaResolver(File basedir, SchemaMapping[] mappings, boolean allowRemoteSchemas, boolean offline) {
        this.basedir = basedir;
        this.allowRemoteSchemas = allowRemoteSchemas;
        this.offline = offline;
        SchemaCatalog catalog = new SchemaCatalog(basedir, mappings);
        SchemaLoader loader = new SchemaLoader(Collections.singletonList(catalog), Collections.singletonList(this::getResource)) {
            @Override
            protected InputStreamSource getClasspathResource(AbsoluteIri location) {
                String scheme = location.getScheme();
                if ("classpath".equalsIgnoreCase(scheme) || "resource".equalsIgnoreCase(scheme)) {
                    // The built-in loader expects resource names, so decode URI escapes at this boundary.
                    String name = URI.create(location.toString()).getSchemeSpecificPart();
                    location = AbsoluteIri.of(scheme.toLowerCase(Locale.ROOT) + ":" + name);
                }
                return super.getClasspathResource(location);
            }
        };
        registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_4, builder -> builder.schemaLoader(loader));
    }

    Schema load(String schema) {
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
        return registry.getSchema(SchemaLocation.of(location));
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
