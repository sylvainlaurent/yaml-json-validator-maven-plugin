package com.github.sylvainlaurent.maven.yamljsonvalidator;

import com.networknt.schema.AbsoluteIri;
import com.networknt.schema.resource.InputStreamSource;
import com.networknt.schema.resource.ResourceLoader;
import com.networknt.schema.resource.SchemaIdResolver;
import com.networknt.schema.resource.SchemaLoader;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;

/** Keeps classloader fallback from changing the requested resource name. */
final class ExactClasspathSchemaLoader extends SchemaLoader {
    ExactClasspathSchemaLoader(List<SchemaIdResolver> mappings, List<ResourceLoader> loaders) {
        super(mappings, loaders);
    }

    @Override
    protected InputStreamSource getClasspathResource(AbsoluteIri location) {
        String scheme = location.getScheme();
        if (!"classpath".equalsIgnoreCase(scheme) && !"resource".equalsIgnoreCase(scheme)) {
            return null;
        }
        // Decode only here: classloaders take resource names, not URI-escaped paths.
        String name = URI.create(location.toString()).getSchemeSpecificPart();
        if (name.startsWith("//")) {
            name = name.substring(2);
        }
        if (name.startsWith("/")) {
            name = name.substring(1);
        }
        final String resource = name;
        return () -> {
            ClassLoader context = Thread.currentThread().getContextClassLoader();
            ClassLoader own = ResourceLoader.class.getClassLoader();
            InputStream input = context == null ? null : context.getResourceAsStream(resource);
            if (input == null && own != null && own != context) {
                input = own.getResourceAsStream(resource);
            }
            if (input == null) {
                throw new FileNotFoundException(location.toString());
            }
            return input;
        };
    }
}
