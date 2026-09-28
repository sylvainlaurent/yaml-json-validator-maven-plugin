
package com.github.sylvainlaurent.maven.yamljsonvalidator;

import static org.junit.jupiter.api.Assertions.*;
import static com.github.sylvainlaurent.maven.yamljsonvalidator.SchemaCatalogTest.mapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaException;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.io.TempDir;

public class SchemaResolverTest {
    private final List<SchemaResolver> resolvers = new ArrayList<>();
    private final SchemaResolver resolver = resolver(new File("."), null, true, false);
    @TempDir
    Path directory;
    @Test
    public void dont_accept_not_found_schema() {
        assertThrows(SchemaException.class, () -> resolver.load("non_existing"));
    }

    @Test
    public void accept_schema_from_filepath() {
        assertNotNull(resolver.load("src/test/resources/schema-with-ref.json"));
    }


    @Test
    public void accept_schema_from_classpath() {
        assertNotNull(resolver.load("schema-with-ref.json"));
    }

    @Test
    public void return_null_when_no_schema_entered() {
        assertNull(resolver.load(null));
        assertNull(resolver.load(""));
    }

    @Test
    public void mapped_root_keeps_its_logical_base_for_nested_references_and_fragments() throws Exception {
        Files.createDirectory(directory.resolve("schemas"));
        write("root override.json", "{\"$schema\":\"http://json-schema.org/draft-04/schema#\","
                + "\"$ref\":\"integer.json#/definitions/positive\"}");
        write("schemas/integer.json", "{\"definitions\":{\"positive\":{\"type\":\"integer\",\"minimum\":1}}}");
        SchemaMapping[] mappings = {
            mapping("https://example.invalid/schemas/*", "schemas/"),
            mapping("https://example.invalid/schemas/root.json", "root override.json")
        };
        for (boolean offline : new boolean[] {false, true}) {
            SchemaResolver mapped = resolver(directory.toFile(), mappings, false, offline);
            assertPositiveInteger(mapped.load("https://example.invalid/schemas/root.json"));
            assertPositiveInteger(mapped.load("https://example.invalid/schemas/integer.json#/definitions/positive"));
        }
    }

    @Test
    public void plain_root_paths_are_project_relative_and_prefer_files_to_classpath() throws Exception {
        write("schema-with-ref.json", "{\"type\":\"integer\",\"minimum\":1}");
        SchemaResolver local = resolver(directory.toFile(), null, false, true);
        assertPositiveInteger(local.load("schema-with-ref.json"));
        assertPositiveInteger(local.load(directory.resolve("schema-with-ref.json").toString()));
        assertPositiveInteger(local.load(directory.resolve("schema-with-ref.json").toUri().toString()));
    }

    @Test
    public void relative_file_references_support_spaces() throws Exception {
        write("root schema.json", "{\"$ref\":\"integer%20schema.json\"}");
        write("integer schema.json", "{\"type\":\"integer\",\"minimum\":1}");
        assertPositiveInteger(resolver(directory.toFile(), null, false, true).load("root schema.json"));
    }

    @Test
    public void classpath_mapping_works_without_the_context_class_loader() throws Exception {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(new ClassLoader(null) { });
            SchemaResolver mapped = resolver(directory.toFile(), new SchemaMapping[] {
                mapping("https://example.invalid/*", "classpath:schemas/")
            }, false, true);
            assertPositiveInteger(mapped.load("https://example.invalid/positive-integer.json"));
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Test
    public void classpath_names_and_relative_references_support_uri_encoded_spaces() throws Exception {
        write("root schema.json", "{\"$ref\":\"integer%20schema.json\"}");
        write("integer schema.json", "{\"type\":\"integer\",\"minimum\":1}");
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader resources = new URLClassLoader(new URL[] {directory.toUri().toURL()}, original)) {
            Thread.currentThread().setContextClassLoader(resources);
            // The resources are on the classpath, outside the project's filesystem lookup.
            SchemaResolver classpath = resolver(directory.resolve("project").toFile(),
                    new SchemaMapping[] {mapping("https://example.invalid/*", "classpath:")}, false, true);
            assertPositiveInteger(classpath.load("root schema.json"));
            assertPositiveInteger(classpath.load("classpath:root%20schema.json"));
            assertPositiveInteger(classpath.load("https://example.invalid/root%20schema.json"));
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    static void assertPositiveInteger(Schema schema) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        assertTrue(schema.validate(mapper.readTree("2")).isEmpty());
        assertFalse(schema.validate(mapper.readTree("0")).isEmpty());
        assertFalse(schema.validate(mapper.readTree("\"2\"")).isEmpty());
    }

    private SchemaResolver resolver(File basedir, SchemaMapping[] mappings, boolean remote, boolean offline) {
        SchemaResolver resolver = new SchemaResolver(basedir, mappings, remote, offline, TestRuntime::create);
        resolvers.add(resolver);
        return resolver;
    }

    @AfterEach
    void closeResolvers() {
        resolvers.forEach(SchemaResolver::close);
    }

    private void write(String name, String content) throws Exception {
        Files.write(directory.resolve(name), content.getBytes(StandardCharsets.UTF_8));
    }
}
