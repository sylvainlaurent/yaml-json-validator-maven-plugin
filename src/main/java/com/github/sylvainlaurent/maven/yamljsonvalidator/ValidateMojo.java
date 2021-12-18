package com.github.sylvainlaurent.maven.yamljsonvalidator;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * This mojo validates YAML and JSON files for well-formedness. If JSON schema is provided, it also
 * validates JSON files against it.
 */
@Mojo(name = "validate", defaultPhase = LifecyclePhase.PROCESS_SOURCES, threadSafe = true)
public class ValidateMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.basedir}", required = true, readonly = true)
    private File basedir;

    /**
     * <p>Specification of files to be validated.</p>
     *
     * <p>Use <code>&lt;includes&gt;</code> and
     * <code>&lt;excludes&gt;</code> elements with nested lists of <code>&lt;include&gt;</code>
     * and <code>&lt;exclude&gt;</code> elements respectively to specify lists of file masks of
     * included and excluded files. The file masks are treated as paths relative to
     * <code>${project.basedir}</code> and their syntax is that of
     * <code>org.codehaus.plexus.util.DirectoryScanner</code>.</p>
     *
     * <p>JSON schema can be specified with <code>&lt;jsonSchema&gt;</code> element.</p>
     */
    @Parameter
    private ValidationSet[] validationSets;

    /**
     * Set to <code>false</code> to disable printing of files being validated.
     */
    @Parameter(defaultValue = "true")
    private boolean verbose;

    @Parameter(defaultValue = "false")
    private boolean skip;

    /**
     * Set to <code>true</code> to accept empty JSON and YAML files as valid.
     */
    @Parameter(name = "allowEmptyFiles", defaultValue = "false")
    private boolean allowEmptyFiles;

    /**
     * Set to <code>true</code> to detect duplicate keys in JSON and YAML dictionaries.
     */
    @Parameter(defaultValue = "true")
    private boolean detectDuplicateKeys;

    /**
     * Set to <code>true</code> to allow json validation to pass if Java/C style
     * comments have been placed in JSON files.
     */
    @Parameter(defaultValue = "false")
    private boolean allowJsonComments;

    /**
     * Set to <code>true</code> to allow for single trailing comma following final value or member.
     */
    @Parameter(defaultValue = "false")
    private boolean allowTrailingComma;

    @Override
    public void execute() throws MojoExecutionException {
        boolean encounteredError = false;

        if (skip) {
            getLog().info("Skipping validation");
            return;
        }

        for (final ValidationSet set : validationSets) {
            InputStream inputStream = openJsonSchema(set.getJsonSchema());
            final ValidationService validationService = new ValidationService(
                    inputStream,
                    allowEmptyFiles,
                    detectDuplicateKeys,
                    allowJsonComments,
                    allowTrailingComma);

            final File[] files = set.getFiles(basedir);

            for (final File file : files) {
                if (verbose) {
                    getLog().info("Validating file " + file);
                }
                final ValidationResult result = validationService.validate(file);
                if (result.hasError()) {
                    encounteredError = true;
                }
                for (final String msg : result.getMessages()) {
                    getLog().warn(msg);
                }
            }
        }

        if (encounteredError) {
            throw new MojoExecutionException("Some files are not valid, see previous logs");
        }
    }

    InputStream openJsonSchema(String jsonSchemaFile) throws MojoExecutionException {
        if (jsonSchemaFile != null && jsonSchemaFile.length() > 0) {
            try {
                URI uri = new URI(jsonSchemaFile);
                if(uri.getScheme() == null) {
                    File file = new File(jsonSchemaFile);
                    if (file.isFile()) {
                        try {
                            return new FileInputStream(file);
                        } catch (FileNotFoundException e) {
                            throw new MojoExecutionException("Could not load schema file ["+ jsonSchemaFile + "]", e);
                        }
                    } else {
                        try {
                            InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(jsonSchemaFile);
                            if (inputStream != null) {
                                return inputStream;
                            }
                            throw new MojoExecutionException("Could not load schema neither from filesystem nor classpath ["+ jsonSchemaFile + "]");
                        } catch (Exception e){
                            throw new MojoExecutionException("Could not load schema file from classpath ["+ jsonSchemaFile + "]", e);
                        }
                    }
                } else {
                    try (final CloseableHttpClient httpclient = HttpClients.custom().useSystemProperties().build()) {
                        HttpGet get = new HttpGet();
                        get.setURI(uri);
                        return httpclient.execute(get).getEntity().getContent();
                    }
                }
            } catch (URISyntaxException | IOException use) {
                getLog().warn("An error occurs while reading schema from URI " + jsonSchemaFile);
                getLog().debug(use);
            }
        }
        return null;
    }

}
