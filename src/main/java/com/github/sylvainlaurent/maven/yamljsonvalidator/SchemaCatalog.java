package com.github.sylvainlaurent.maven.yamljsonvalidator;

import com.networknt.schema.AbsoluteIri;
import com.networknt.schema.resource.SchemaIdResolver;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Applies a single exact match or the longest matching prefix, independently of declaration order. */
final class SchemaCatalog implements SchemaIdResolver {
    private final Map<String, String> exact = new HashMap<>();
    private final List<Map.Entry<String, String>> prefixes;

    SchemaCatalog(File basedir, SchemaMapping[] mappings) {
        Map<String, String> prefixMap = new HashMap<>();
        Set<String> sources = new HashSet<>();
        if (mappings != null) {
            for (SchemaMapping mapping : mappings) {
                if (mapping == null) {
                    throw new IllegalArgumentException("Schema mapping must contain from and to");
                }
                String from = required(mapping.getFrom(), "from");
                String to = required(mapping.getTo(), "to");
                boolean prefix = from.endsWith("*");
                String source = prefix ? from.substring(0, from.length() - 1) : from;
                if (source.contains("*") || to.contains("*")) {
                    throw new IllegalArgumentException("Schema mapping [" + from
                            + "]: only a single trailing '*' in from is supported; to must have no wildcard");
                }
                URI identifier = uri(from);
                if (!identifier.isAbsolute() || identifier.getRawFragment() != null) {
                    throw new IllegalArgumentException("Schema mapping source must be an absolute identifier without a fragment: " + from);
                }
                if (!sources.add(from)) {
                    throw new IllegalArgumentException("Duplicate schema mapping source: " + from);
                }
                String destination = location(basedir, to);
                // A replacement prefix may be just a scheme, for example "classpath:".
                URI target = uri(prefix ? destination + "schema" : destination);
                String scheme = target.getScheme();
                if (target.getRawFragment() != null || !("file".equalsIgnoreCase(scheme)
                        || "classpath".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)
                        || "https".equalsIgnoreCase(scheme))) {
                    throw new IllegalArgumentException("Unsupported schema mapping destination: " + to);
                }
                (prefix ? prefixMap : exact).put(source, destination);
            }
        }
        prefixes = new ArrayList<>(prefixMap.entrySet());
        prefixes.sort(Comparator.comparingInt((Map.Entry<String, String> entry) -> entry.getKey().length()).reversed());
    }

    @Override
    public AbsoluteIri resolve(AbsoluteIri identifier) {
        String source = identifier.toString();
        String destination = exact.get(source);
        if (destination != null) {
            return AbsoluteIri.of(destination);
        }
        for (Map.Entry<String, String> prefix : prefixes) {
            if (source.startsWith(prefix.getKey())) {
                return AbsoluteIri.of(prefix.getValue() + source.substring(prefix.getKey().length()));
            }
        }
        return null;
    }

    static boolean hasScheme(String value) {
        // Windows drive letters belong to filesystem paths, not URI schemes.
        return !value.matches("^[A-Za-z]:[\\\\/].*") && value.matches("^[A-Za-z][A-Za-z0-9+.-]*:.*");
    }

    static String location(File basedir, String value) {
        if (hasScheme(value)) {
            return value;
        }
        File file = new File(value);
        if (!file.isAbsolute()) {
            file = new File(basedir, value);
        }
        String result = file.toURI().toString();
        // File.toURI only preserves directory separators for directories that already exist.
        if ((value.endsWith("/") || value.endsWith(File.separator)) && !result.endsWith("/")) {
            result += "/";
        }
        return result;
    }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Schema mapping " + field + " must not be empty");
        }
        return value.trim();
    }

    private static URI uri(String value) {
        try {
            return new URI(value);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid schema location: " + value, e);
        }
    }
}
