package com.github.sylvainlaurent.maven.yamljsonvalidator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Schema;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SchemaLifecycleTest {
    private static final String PATTERN = "{\"type\":\"string\",\"pattern\":\"^[^]+$\"}";
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void resolver_closure_releases_its_context_without_affecting_another_resolver() throws Exception {
        try (SchemaResolver first = resolver(); SchemaResolver second = resolver()) {
            Schema a = first.loadStream(stream(PATTERN));
            Schema b = second.loadStream(stream(PATTERN));
            assertMatches(a);
            assertMatches(b);
            first.close();
            first.close();
            assertThrows(IllegalStateException.class, () -> a.validate(mapper.readTree("\"x\"")));
            assertThrows(IllegalStateException.class, () -> first.loadStream(stream(PATTERN)));
            assertMatches(b);
        }
    }

    @Test
    void borrowed_service_does_not_close_the_resolvers_context() throws Exception {
        try (SchemaResolver resolver = resolver()) {
            Schema schema = resolver.loadStream(stream(PATTERN));
            ValidationService borrowed = ValidationService.forSchema(schema, false, true, false, false);
            borrowed.close();
            borrowed.close();
            assertMatches(schema);
        }
    }

    @Test
    void stream_service_releases_its_context_after_validation_errors() throws Exception {
        try (ValidationService service = new ValidationService(stream(PATTERN))) {
            // Keep the compiled schema to check the actual Graal context, not just the service's closed flag.
            Field field = ValidationService.class.getDeclaredField("schema");
            field.setAccessible(true);
            Schema schema = (Schema) field.get(service);
            assertMatches(schema);
            assertTrue(service.validate(new File("missing-input-file.json")).hasError());
            service.close();
            service.close();
            assertThrows(IllegalStateException.class, () -> schema.validate(mapper.readTree("\"x\"")));
        }
    }

    @Test
    void resolver_is_closed_when_schema_preparation_fails() throws Exception {
        Schema retained;
        try (SchemaResolver resolver = resolver()) {
            retained = resolver.loadStream(stream(PATTERN));
            assertThrows(RuntimeException.class,
                    () -> resolver.loadStream(stream("{\"required\":\"name\"}")));
        }
        assertThrows(IllegalStateException.class, () -> retained.validate(mapper.readTree("\"x\"")));
        // A failed public constructor must also leave other services usable.
        assertThrows(RuntimeException.class, () -> new ValidationService(stream("{\"required\":\"name\"}")));
        try (ValidationService service = new ValidationService(stream(PATTERN))) {
            assertNotNull(service);
        }
    }

    private void assertMatches(Schema schema) throws Exception {
        assertTrue(schema.validate(mapper.readTree("\"x\"")).isEmpty());
    }

    private SchemaResolver resolver() {
        return new SchemaResolver(new File("."), null, false, true);
    }

    private ByteArrayInputStream stream(String schema) {
        return new ByteArrayInputStream(schema.getBytes(StandardCharsets.UTF_8));
    }
}
