package com.github.sylvainlaurent.maven.yamljsonvalidator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.io.TempDir;

public class ValidationServiceSchemaTest {
    private final List<SchemaResolver> resolvers = new ArrayList<>();

    @AfterEach
    void closeServices() {
        resolvers.forEach(SchemaResolver::close);
    }

    @TempDir
    Path directory;

    @Test
    public void unknown_boolean_format_does_not_restrict_strings() throws IOException {
        // Issue #28: boolean is an unknown format, not a restriction to "true" or "false".
        ValidationService service = service("{\"type\":\"string\",\"format\":\"boolean\"}");
        assertValid(service, "\"true\"");
        assertValid(service, "\"false\"");
        assertValid(service, "\"not-a-boolean\"");
    }

    @Test
    public void unknown_boolean_format_preserves_the_string_type_constraint() throws IOException {
        ValidationService service = service("{\"type\":\"string\",\"format\":\"boolean\"}");
        assertInvalid(service, "true");
        assertInvalid(service, "123");
    }

    @Test
    public void validates_actual_boolean_values() throws IOException {
        ValidationService service = service("{\"type\":\"boolean\"}");
        assertValid(service, "true");
        assertValid(service, "false");
        assertInvalid(service, "\"true\"");
    }

    @Test
    public void uses_draft4_when_the_dialect_is_omitted() throws IOException {
        // Draft 4 uses a boolean exclusiveMinimum; newer drafts use a number.
        ValidationService service = service("{\"type\":\"number\",\"minimum\":1,\"exclusiveMinimum\":true}");
        assertInvalid(service, "1");
        assertValid(service, "1.5");
    }

    @Test
    public void validates_draft202012_unevaluated_properties() throws IOException {
        ValidationService service = service("{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\","
                + "\"type\":\"object\",\"properties\":{\"known\":{\"type\":\"string\"}},"
                + "\"unevaluatedProperties\":false}");
        assertValid(service, "{\"known\":\"value\"}");
        assertInvalid(service, "{\"known\":\"value\",\"extra\":true}");
    }

    @Test
    public void validates_classpath_references_without_the_context_class_loader() throws IOException {
        // Maven plugin dependencies must remain visible through the plugin's own class loader.
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(new ClassLoader(null) { });
            ValidationService service = service("{\"$ref\":\"classpath:schemas/positive-integer.json\"}");
            assertValid(service, "2");
            assertInvalid(service, "0");
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Test
    public void validates_file_references() throws IOException {
        Path schema = directory.resolve("referenced-schema.json");
        Files.write(schema, "{\"type\":\"integer\"}".getBytes(StandardCharsets.UTF_8));
        ValidationService service = service("{\"$ref\":\"" + schema.toUri() + "\"}");
        assertValid(service, "1");
        assertInvalid(service, "\"not-an-integer\"");
    }

    @Test
    public void unresolved_reference_reports_a_validation_error() throws IOException {
        ValidationService service = service("{\"$ref\":\"classpath:missing-schema.json\"}");
        assertInvalid(service, "{}");
    }

    private ValidationService service(String schema) throws IOException {
        SchemaResolver resolver = new SchemaResolver(directory.toFile(), null, true, false, TestRuntime::create);
        resolvers.add(resolver);
        return new ValidationService(resolver.loadStream(new ByteArrayInputStream(schema.getBytes(StandardCharsets.UTF_8)),
                directory.resolve("schema.json").toUri()), false, true, false, false);
    }

    private ValidationResult validate(ValidationService service, String json) throws IOException {
        Path input = directory.resolve("input.json");
        Files.write(input, json.getBytes(StandardCharsets.UTF_8));
        return service.validate(input.toFile());
    }

    private void assertValid(ValidationService service, String json) throws IOException {
        ValidationResult result = validate(service, json);
        assertFalse(result.hasError(), result.getMessages().toString());
        assertTrue(result.getMessages().isEmpty(), result.getMessages().toString());
    }

    private void assertInvalid(ValidationService service, String json) throws IOException {
        ValidationResult result = validate(service, json);
        assertTrue(result.hasError(), "Expected invalid input: " + json);
        assertFalse(result.getMessages().isEmpty(), "Validation errors must explain the failure");
    }
}
