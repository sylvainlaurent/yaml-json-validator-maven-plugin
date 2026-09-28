
package com.github.sylvainlaurent.maven.yamljsonvalidator;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import java.io.File;

public class ValidationServiceAllowEmptyFilesTest {
    private final ValidationService service = new ValidationService(null, true, true, true, false);

    @Test
    public void test_empty_file_yml() {
        final ValidationResult result = service.validate(new File("src/test/resources/empty.yml"));
        assertFalse(result.hasError());
    }

    @Test
    public void test_empty_file_json() {
        final ValidationResult result = service.validate(new File("src/test/resources/empty.json"));
        assertFalse(result.hasError());
    }
}
