
package com.github.sylvainlaurent.maven.yamljsonvalidator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;

public class ValidationServiceTest {
    private ValidationService service = new ValidationService(null);

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
        service = new ValidationService(new File("src/test/resources/swagger-schema.json"));
        final ValidationResult result = service.validate(new File("src/test/resources/not-valid.yml"));
        assertTrue(result.hasError());
        assertTrue(result.getMessages().size() >= 1);
    }

    @Test(expected = RuntimeException.class)
    public void dont_accept_not_found_schema() {
        service = new ValidationService(new File("non_existing"));
    }

    @Test
    public void test_not_valid_swagger_json() {
        service = new ValidationService(new File("src/test/resources/swagger-schema.json"));
        final ValidationResult result = service.validate(new File("src/test/resources/not-valid.json"));
        assertTrue(result.hasError());
        assertTrue(result.getMessages().size() >= 1);
    }

    @Test
    public void test_valid_yml() {
        service = new ValidationService(new File("src/test/resources/swagger-schema.json"));
        final ValidationResult result = service.validate(new File("src/test/resources/swagger-editor-example.yml"));
        assertFalse(result.hasError());
        assertTrue(result.getMessages().isEmpty());
    }

    @Test
    public void test_valid_json() {
        service = new ValidationService(new File("src/test/resources/swagger-schema.json"));
        final ValidationResult result = service.validate(new File("src/test/resources/swagger-editor-example.json"));
        assertFalse(result.hasError());
        assertTrue(result.getMessages().isEmpty());
    }
}
