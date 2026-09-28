
package com.github.sylvainlaurent.maven.yamljsonvalidator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.io.File;

public class ValidationServiceTest {
    private final SchemaResolver resolver = new SchemaResolver(new File("."), null, true, false, TestRuntime::create);
    private ValidationService service = new ValidationService(null, false, true, false, false);

    @AfterEach
    void closeService() {
        resolver.close();
    }

    @Test
    public void test_empty_file_yml() {
        final ValidationResult result = service.validate(new File("src/test/resources/empty.yml"));
        assertTrue(result.hasError());
        assertEquals(1, result.getMessages().size());
    }

    @Test
    public void test_empty_file_json() {
        final ValidationResult result = service.validate(new File("src/test/resources/empty.json"));
        assertTrue(result.hasError());
        assertEquals(1, result.getMessages().size());
    }

    @Test
    public void test_malformed_file_yml() {
        final ValidationResult result = service.validate(new File("src/test/resources/malformed.yml"));
        assertTrue(result.hasError());
        assertEquals(1, result.getMessages().size());
    }

    @Test
    public void test_malformed_file_json() {
        final ValidationResult result = service.validate(new File("src/test/resources/malformed.json"));
        assertTrue(result.hasError());
        assertEquals(1, result.getMessages().size());
    }

    @Test
    public void test_invalid_yml() {
        service = new ValidationService(resolver.load("src/test/resources/swagger-schema.json"), false, true, false, false);
        final ValidationResult result = service.validate(new File("src/test/resources/not-valid.yml"));
        assertTrue(result.hasError());
        assertTrue(result.getMessages().size() >= 1);
    }

    @Test
    public void test_not_valid_swagger_json() {
        service = new ValidationService(resolver.load("src/test/resources/swagger-schema.json"), false, true, false, false);
        final ValidationResult result = service.validate(new File("src/test/resources/not-valid.json"));
        assertTrue(result.hasError());
        assertTrue(result.getMessages().size() >= 1);
    }

    @Test
    public void test_valid_yml() {
        service = new ValidationService(resolver.load("src/test/resources/swagger-schema.json"), false, true, false, false);
        final ValidationResult result = service.validate(new File("src/test/resources/swagger-editor-example.yml"));
        assertFalse(result.hasError());
        assertTrue(result.getMessages().isEmpty());
    }

    @Test
    public void test_valid_json() {
        service = new ValidationService(resolver.load("src/test/resources/swagger-schema.json"), false, true, false, false);
        final ValidationResult result = service.validate(new File("src/test/resources/swagger-editor-example.json"));
        assertFalse(result.hasError());
        assertTrue(result.getMessages().isEmpty());
    }

    @Test
    public void test_schema_with_reference_to_unreachable_host() {
        service = new ValidationService(resolver.load("src/test/resources/schema-with-ref.json"), false, true, false, false);
        final ValidationResult result = service.validate(new File("src/test/resources/swagger-editor-example.json"));
        assertFalse(result.hasError());
    }

    @Test
    public void test_comments_allowed_in_json() {
        service = new ValidationService(null, false, false, true, false);
        final ValidationResult result = service.validate(new File("src/test/resources/with-comments.json"));
        assertFalse(result.hasError());
        assertTrue(result.getMessages().isEmpty());
    }

    @Test
    public void test_comments_not_allowed_in_json() {
        service = new ValidationService(null, false, false, false, false);
        final ValidationResult result = service.validate(new File("src/test/resources/with-comments.json"));
        assertTrue(result.hasError());
        assertFalse(result.getMessages().isEmpty());
    }

    @Test
    public void test_comments_allowed_in_yml() {
        service = new ValidationService(null, false, false, true, false);
        final ValidationResult result = service.validate(new File("src/test/resources/with-comments.yml"));
        assertFalse(result.hasError());
        assertTrue(result.getMessages().isEmpty());
    }

    @Test
    public void test_comments_not_allowed_in_yml() {
        service = new ValidationService(null, false, false, false, false);
        final ValidationResult result = service.validate(new File("src/test/resources/with-comments.yml"));
        assertFalse(result.hasError());
        assertTrue(result.getMessages().isEmpty());
    }
}
