
package com.github.sylvainlaurent.maven.yamljsonvalidator;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Test;

public class ValidateMojoTest {
    @Test(expected = MojoExecutionException.class)
    public void dont_accept_not_found_schema() throws MojoExecutionException {
        ValidateMojo validateMojo = new ValidateMojo();
        validateMojo.openJsonSchema("non_existing");
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
