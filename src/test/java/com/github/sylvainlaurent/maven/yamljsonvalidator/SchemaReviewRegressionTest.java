package com.github.sylvainlaurent.maven.yamljsonvalidator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Schema;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import static com.github.sylvainlaurent.maven.yamljsonvalidator.SchemaCatalogTest.mapping;
import static org.junit.jupiter.api.Assertions.*;

class SchemaReviewRegressionTest {
    private final ObjectMapper mapper = new ObjectMapper();
    @TempDir
    Path directory;

    @Test
    void loads_schema_without_patterns_on_the_running_jvm() throws Exception {
        try (SchemaResolver resolver = resolver()) {
            assertTrue(load(resolver, "{\"type\":\"integer\"}").validate(mapper.readTree("1")).isEmpty());
        }
    }

    @Test
    void embedded_draft7_tuple_uses_its_own_dialect() throws Exception {
        try (SchemaResolver resolver = resolver()) {
            Schema schema = load(resolver, compound("{\"type\":\"array\",\"items\":[{\"type\":\"string\"}]}"));
            assertTrue(schema.validate(mapper.readTree("[\"ok\"]")).isEmpty());
            assertFalse(schema.validate(mapper.readTree("[1]")).isEmpty());
        }
    }

    @Test
    void custom_meta_schema_preserves_same_dialect_resources() throws Exception {
        String dialect = "https://example.com/meta/identified";
        String meta = "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\","
                + "\"$id\":\"" + dialect + "\",\"$dynamicAnchor\":\"meta\","
                + "\"allOf\":[{\"$ref\":\"https://json-schema.org/draft/2020-12/schema\"}],"
                + "\"required\":[\"$id\"]}";
        Path metaFile = directory.resolve("meta.json");
        Files.write(metaFile, meta.getBytes(StandardCharsets.UTF_8));
        SchemaMapping[] mappings = {mapping(dialect, metaFile.toString())};
        for (boolean stream : new boolean[] {false, true}) {
            for (boolean explicitDialect : new boolean[] {false, true}) {
                String json = "{\"$schema\":\"" + dialect + "\",\"$id\":\"https://example.com/root\","
                        + "\"type\":\"object\",\"properties\":{\"name\":{\"$id\":\"https://example.com/name\","
                        + (explicitDialect ? "\"$schema\":\"" + dialect + "\"," : "")
                        + "\"type\":\"string\"}}}";
                Path root = directory.resolve("root.json");
                try (SchemaResolver resolver = new SchemaResolver(directory.toFile(), mappings, false, true,
                        TestRuntime::create)) {
                    Schema schema;
                    if (stream) {
                        schema = resolver.loadStream(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),
                                root.toUri());
                    } else {
                        Files.write(root, json.getBytes(StandardCharsets.UTF_8));
                        schema = resolver.load(root.toString());
                    }
                    assertEquals(mapper.readTree(json), schema.getSchemaNode());
                    assertTrue(schema.validate(mapper.readTree("{\"name\":\"Ada\"}")).isEmpty());
                    assertFalse(schema.validate(mapper.readTree("{\"name\":1}")).isEmpty());
                }
            }
        }
    }

    @Test
    void streamed_relative_id_has_a_document_base() throws Exception {
        try (SchemaResolver resolver = resolver()) {
            Schema schema = load(resolver, "{\"id\":\"relative.json\",\"type\":\"integer\"}");
            assertTrue(schema.validate(mapper.readTree("1")).isEmpty());
            assertFalse(schema.validate(mapper.readTree("\"1\"")).isEmpty());
        }
    }

    @Test
    void nested_resources_inherit_dialects_and_original_constraints_remain_intact() throws Exception {
        String child = "{\"definitions\":{\"nested\":{\"$id\":\"nested.json\","
                + "\"items\":[{\"type\":\"string\"}]}},\"$ref\":\"nested.json\"}";
        String json = compound(child);
        try (SchemaResolver resolver = resolver()) {
            Schema schema = load(resolver, json);
            assertEquals(mapper.readTree(json), schema.getSchemaNode());
            assertTrue(schema.validate(mapper.readTree("[\"ok\"]")).isEmpty());
            assertFalse(schema.validate(mapper.readTree("[1]")).isEmpty());
        }
    }

    @Test
    void ordinary_data_is_not_treated_as_an_embedded_schema() throws Exception {
        String data = "{\"$id\":\"data.json\",\"$schema\":\"urn:not-a-dialect\",\"required\":\"name\"}";
        String json = "{\"$schema\":\"http://json-schema.org/draft-07/schema#\",\"const\":" + data
                + ",\"default\":" + data + ",\"examples\":[" + data + "],\"enum\":[" + data + "]}";
        try (SchemaResolver resolver = resolver()) {
            assertTrue(load(resolver, json).validate(mapper.readTree(data)).isEmpty());
        }
    }

    @Test
    void relative_modern_ids_and_references_use_the_supplied_document_location() throws Exception {
        Files.write(directory.resolve("integer.json"), "{\"type\":\"integer\"}".getBytes(StandardCharsets.UTF_8));
        for (String dialect : new String[] {"http://json-schema.org/draft-07/schema#",
                "https://json-schema.org/draft/2020-12/schema"}) {
            String json = "{\"$schema\":\"" + dialect + "\",\"$id\":\"relative.json\",\"$ref\":\"integer.json\"}";
            try (SchemaResolver resolver = resolver()) {
                Schema schema = resolver.loadStream(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),
                        directory.resolve("stream.json").toUri());
                assertTrue(schema.validate(mapper.readTree("1")).isEmpty());
                assertFalse(schema.validate(mapper.readTree("\"1\"")).isEmpty());
            }
        }
    }

    @Test
    void stream_requires_a_usable_explicit_document_uri() {
        try (SchemaResolver resolver = resolver()) {
            for (URI base : new URI[] {null, URI.create("relative.json"), URI.create("urn:opaque"),
                    URI.create("https://example.com/schema#fragment")}) {
                assertThrows(IllegalArgumentException.class, () -> resolver.loadStream(
                        new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)), base));
            }
        }
    }

    @Test
    void separate_executions_can_validate_concurrently() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<Boolean> validate = () -> {
            try (SchemaResolver resolver = resolver()) {
                return load(resolver, "{\"pattern\":\"^[^]+$\"}").validate(mapper.readTree("\"ok\"")).isEmpty();
            }
        };
        try {
            for (java.util.concurrent.Future<Boolean> result : executor.invokeAll(java.util.Arrays.asList(validate, validate))) {
                assertTrue(result.get());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private String compound(String child) {
        return "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\","
                + "\"$id\":\"https://example.com/root.json\",\"$ref\":\"tuple.json\",\"$defs\":{\"tuple\":{"
                + "\"$id\":\"tuple.json\",\"$schema\":\"http://json-schema.org/draft-07/schema#\","
                + child.substring(1) + "}}";
    }

    private SchemaResolver resolver() {
        return new SchemaResolver(new File("."), null, false, true, TestRuntime::create);
    }

    private Schema load(SchemaResolver resolver, String json) throws Exception {
        return resolver.loadStream(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), TestRuntime.DOCUMENT);
    }
}
