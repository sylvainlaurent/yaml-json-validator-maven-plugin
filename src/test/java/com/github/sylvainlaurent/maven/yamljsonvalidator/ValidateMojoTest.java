
package com.github.sylvainlaurent.maven.yamljsonvalidator;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;

public class ValidateMojoTest {
    @Test
    public void dont_accept_not_found_schema() {
        assertThrows(MojoExecutionException.class, () -> {
            ValidateMojo validateMojo = new ValidateMojo();
            validateMojo.openJsonSchema("non_existing");
        });
    }

    @Test
    public void accept_schema_from_filepath() throws MojoExecutionException {
        ValidateMojo validateMojo = new ValidateMojo();
        assertNotNull(validateMojo.openJsonSchema("src/test/resources/schema-with-ref.json"));
    }


    @Test
    public void accept_schema_from_classpath() throws MojoExecutionException {
        ValidateMojo validateMojo = new ValidateMojo();
        assertNotNull(validateMojo.openJsonSchema("schema-with-ref.json"));
    }

    @Test
    public void return_null_when_no_schema_entered() throws MojoExecutionException {
        ValidateMojo validateMojo = new ValidateMojo();
        assertNull(validateMojo.openJsonSchema(null));
        assertNull(validateMojo.openJsonSchema(""));
    }

}
