package com.github.sylvainlaurent.maven.yamljsonvalidator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Schema;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SchemaLifecycleTest {
    private static final String PATTERN = "{\"type\":\"string\",\"pattern\":\"^[^]+$\"}";
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void resolver_closure_releases_its_context_without_affecting_another_resolver() throws Exception {
        try (SchemaResolver first = resolver(); SchemaResolver second = resolver()) {
            Schema a = load(first, PATTERN);
            Schema b = load(second, PATTERN);
            assertMatches(a);
            assertMatches(b);
            first.close();
            first.close();
            assertThrows(IllegalStateException.class, () -> a.validate(mapper.readTree("\"x\"")));
            assertThrows(IllegalStateException.class, () -> load(first, PATTERN));
            assertMatches(b);
        }
    }

    @Test
    void services_share_the_resolvers_schema_without_owning_its_context() throws Exception {
        try (SchemaResolver resolver = resolver()) {
            Schema schema = load(resolver, PATTERN);
            ValidationService service = new ValidationService(schema, false, true, false, false);
            assertTrue(service.validate(new File("missing-input-file.json")).hasError());
            assertMatches(schema);
        }
    }

    @Test
    void resolver_is_closed_when_schema_parsing_fails() throws Exception {
        Schema retained;
        try (SchemaResolver resolver = resolver()) {
            retained = load(resolver, PATTERN);
            assertThrows(IOException.class, () -> load(resolver, "{"));
        }
        assertThrows(IllegalStateException.class, () -> retained.validate(mapper.readTree("\"x\"")));
        try (SchemaResolver other = resolver()) {
            assertMatches(load(other, PATTERN));
        }
    }

    @Test
    void absent_schema_does_not_resolve_or_initialize_graal() {
        try (SchemaResolver resolver = new SchemaResolver(new File("."), null, false, true,
                () -> { throw new AssertionError("Unexpected runtime resolution"); })) {
            assertNull(resolver.load(null));
            assertNull(resolver.load(""));
        }
    }

    private void assertMatches(Schema schema) throws Exception {
        assertTrue(schema.validate(mapper.readTree("\"x\"")).isEmpty());
    }

    private SchemaResolver resolver() {
        return new SchemaResolver(new File("."), null, false, true, TestRuntime::create);
    }

    private Schema load(SchemaResolver resolver, String json) throws Exception {
        return resolver.loadStream(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), TestRuntime.DOCUMENT);
    }
}
