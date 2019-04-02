
package com.github.sylvainlaurent.maven.yamljsonvalidator;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

public class ValidationServiceAllowEmptyFilesTest {
    private ValidationService service = new ValidationService(null, true, false);

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
