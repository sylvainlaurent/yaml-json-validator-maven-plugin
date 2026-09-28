package com.github.sylvainlaurent.maven.yamljsonvalidator;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.github.sylvainlaurent.maven.yamljsonvalidator.SchemaCatalogTest.mapping;
import static org.junit.jupiter.api.Assertions.*;

class SchemaCompatibilityTest {
    @TempDir
    Path directory;
    private final List<AutoCloseable> resources = new ArrayList<>();

    @AfterEach
    void closeResources() throws Exception {
        for (AutoCloseable resource : resources) {
            resource.close();
        }
    }

    @Test
    void rejects_malformed_required_in_both_loading_paths() {
        for (boolean stream : new boolean[] {false, true}) {
            RuntimeException error = assertThrows(RuntimeException.class,
                    () -> service(stream, "{\"type\":\"object\",\"required\":\"name\"}"));
            assertTrue(error.getMessage().contains("required"), error.toString());
        }
    }

    @Test
    void rejects_malformed_nested_constraints_in_both_loading_paths() {
        for (boolean stream : new boolean[] {false, true}) {
            RuntimeException error = assertThrows(RuntimeException.class,
                    () -> service(stream, "{\"properties\":{\"child\":{\"required\":\"name\"}}}"));
            assertTrue(error.getMessage().contains("required"), error.toString());
        }
    }

    @Test
    void validates_required_arrays_and_uses_the_declared_dialect_offline() throws Exception {
        for (boolean stream : new boolean[] {false, true}) {
            ValidationService required = service(stream, "{\"type\":\"object\",\"required\":[\"name\"]}");
            assertValidation(required, "{}", false);
            assertValidation(required, "{\"name\":\"example\"}", true);
            // A boolean exclusiveMinimum belongs to Draft 4, a number to later drafts.
            assertValidation(service(stream, "{\"minimum\":1,\"exclusiveMinimum\":true}"), "1", false);
            for (String dialect : new String[] {"http://json-schema.org/draft-06/schema#",
                    "http://json-schema.org/draft-07/schema#", "https://json-schema.org/draft/2019-09/schema",
                    "https://json-schema.org/draft/2020-12/schema"}) {
                String prefix = "{\"$schema\":\"" + dialect + "\",";
                assertValidation(service(stream, prefix + "\"exclusiveMinimum\":1}"), "1.5", true);
                assertThrows(RuntimeException.class, () -> service(stream, prefix + "\"exclusiveMinimum\":true}"));
            }
        }
    }

    @Test
    void ecma_patterns_accept_negated_empty_classes_and_nbsp() throws Exception {
        for (boolean stream : new boolean[] {false, true}) {
            ValidationService any = service(stream, "{\"type\":\"string\",\"pattern\":\"^[^]+$\"}");
            assertValidation(any, "\"x\"", true);
            assertValidation(any, "\"\\n\"", true);
            assertValidation(any, "\"\"", false);
            ValidationService whitespace = service(stream, "{\"pattern\":\"^\\\\s+$\"}");
            assertValidation(whitespace, "\"\u00a0\"", true);
            assertValidation(whitespace, "\"x\"", false);
        }
    }

    @Test
    void ecma_patterns_support_backreferences_and_pattern_properties() throws Exception {
        for (boolean stream : new boolean[] {false, true}) {
            ValidationService repeated = service(stream, "{\"pattern\":\"^(a)\\\\1$\"}");
            assertValidation(repeated, "\"aa\"", true);
            assertValidation(repeated, "\"ab\"", false);
            ValidationService properties = service(stream,
                    "{\"patternProperties\":{\"^\\\\s+$\":{\"type\":\"integer\"}},\"additionalProperties\":false}");
            assertValidation(properties, "{\"\u00a0\":1}", true);
            assertValidation(properties, "{\"\u00a0\":\"wrong type\"}", false);
            assertValidation(properties, "{\"x\":1}", false);
        }
    }

    @Test
    void rejects_invalid_regex_syntax_in_both_loading_paths() {
        for (boolean stream : new boolean[] {false, true}) {
            assertThrows(RuntimeException.class, () -> service(stream, "{\"pattern\":\"[\"}"));
        }
    }

    @Test
    void classpath_roots_and_mappings_require_the_exact_name() throws Exception {
        write("review-schema.json", "{\"type\":\"integer\"}");
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader loader = new URLClassLoader(new URL[] {directory.toUri().toURL()}, original)) {
            Thread.currentThread().setContextClassLoader(loader);
            SchemaResolver resolver = resolver(new SchemaMapping[] {
                mapping("urn:missing", "classpath:xreview-schema.json")
            });
            for (String root : new String[] {"xreview-schema.json", "classpath:xreview-schema.json",
                    "resource:xreview-schema.json", "urn:missing"}) {
                RuntimeException error = assertThrows(RuntimeException.class, () -> resolver.load(root), root);
                assertTrue(messages(error).contains("xreview-schema.json"), error.toString());
            }
            for (String root : new String[] {"classpath:review-schema.json", "classpath:/review-schema.json",
                    "classpath://review-schema.json", "resource:/review-schema.json"}) {
                assertNotNull(resolver.load(root), root);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Test
    void classpath_references_require_the_exact_name_in_both_loading_paths() throws Exception {
        write("review-schema.json", "{\"type\":\"integer\"}");
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader loader = new URLClassLoader(new URL[] {directory.toUri().toURL()}, original)) {
            Thread.currentThread().setContextClassLoader(loader);
            for (boolean stream : new boolean[] {false, true}) {
                ValidationService service = service(stream, "{\"$ref\":\"classpath:xreview-schema.json\"}");
                ValidationResult result = validate(service, "1");
                assertTrue(result.hasError(), "A different classpath schema must not be selected");
                assertTrue(result.getMessages().toString().contains("xreview-schema.json"));
            }
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Test
    void own_classloader_fallback_requires_the_exact_name() {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(new ClassLoader(null) { });
            SchemaResolver resolver = resolver(null);
            assertNotNull(resolver.load("schema-with-ref.json"));
            assertThrows(RuntimeException.class, () -> resolver.load("xschema-with-ref.json"));
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Test
    void stream_references_decode_classpath_uri_escapes_once() throws Exception {
        write("schema with spaces.json", "{\"type\":\"integer\"}");
        write("schema%20literal.json", "{\"type\":\"integer\"}");
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader loader = new URLClassLoader(new URL[] {directory.toUri().toURL()}, original)) {
            Thread.currentThread().setContextClassLoader(loader);
            for (String name : new String[] {"schema%20with%20spaces.json", "schema%2520literal.json"}) {
                ValidationService service = service(true, "{\"$ref\":\"classpath:/" + name + "\"}");
                assertValidation(service, "1", true);
                assertValidation(service, "\"1\"", false);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    private SchemaResolver resolver(SchemaMapping[] mappings) {
        SchemaResolver resolver = new SchemaResolver(directory.resolve("project").toFile(), mappings, false, true);
        resources.add(resolver);
        return resolver;
    }

    private ValidationService service(boolean stream, String schema) throws Exception {
        ValidationService service;
        if (stream) {
            service = new ValidationService(new ByteArrayInputStream(schema.getBytes(StandardCharsets.UTF_8)));
        } else {
            Path root = Files.createTempFile(directory, "schema-", ".json");
            Files.write(root, schema.getBytes(StandardCharsets.UTF_8));
            service = ValidationService.forSchema(resolver(null).load(root.toString()), false, true, false, false);
        }
        resources.add(service);
        return service;
    }

    private ValidationResult validate(ValidationService service, String json) throws Exception {
        return service.validate(write("input.json", json).toFile());
    }

    private void assertValidation(ValidationService service, String json, boolean valid) throws Exception {
        ValidationResult result = validate(service, json);
        assertEquals(!valid, result.hasError(), json + ": " + result.getMessages());
    }

    private Path write(String name, String content) throws Exception {
        Path path = directory.resolve(name);
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
        return path;
    }

    private String messages(Throwable error) {
        StringBuilder messages = new StringBuilder();
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            messages.append(cause.getMessage()).append('\n');
        }
        return messages.toString();
    }
}
