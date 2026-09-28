package com.github.sylvainlaurent.maven.yamljsonvalidator;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;

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
     * <p>JSON schema can be specified with <code>&lt;jsonSchema&gt;</code>: a file path,
     * classpath resource, file URI, HTTP(S) URL or mapped schema identifier. Relative file
     * paths use the project base directory; plain names fall back to the classpath.</p>
     */
    @Parameter
    private ValidationSet[] validationSets;

    /**
     * <p>Schema mappings shared by every validation set. Each <code>schemaMapping</code>
     * contains <code>from</code> (an absolute schema identifier) and <code>to</code>
     * (a file path, file URI, classpath resource or HTTP(S) URL).</p>
     * <p>A single trailing <code>*</code> in <code>from</code> matches a prefix;
     * its remaining suffix is appended to <code>to</code>, which has no wildcard.
     * Exact matches win, otherwise the longest prefix wins. Mappings do not chain.
     * Relative file destinations are resolved against the project base directory.</p>
     */
    @Parameter
    private SchemaMapping[] schemaMappings;

    /**
     * Allow HTTP(S) schema retrieval after applying mappings, for both root schemas and
     * references. Maven offline mode always disables network retrieval. Local files,
     * classpath resources and bundled meta-schemas remain available.
     */
    @Parameter(defaultValue = "true")
    private boolean allowRemoteSchemas = true;

    @Parameter(defaultValue = "${settings.offline}", readonly = true)
    private boolean offline;

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

        final SchemaResolver resolver;
        try {
            resolver = new SchemaResolver(basedir, schemaMappings, allowRemoteSchemas, offline);
        } catch (IllegalArgumentException e) {
            throw new MojoExecutionException("Invalid schema mappings: " + e.getMessage(), e);
        }
        try {
            for (final ValidationSet set : validationSets) {
                final ValidationService validationService;
                try {
                    validationService = ValidationService.forSchema(resolver.load(set.getJsonSchema()),
                            allowEmptyFiles, detectDuplicateKeys, allowJsonComments, allowTrailingComma);
                } catch (RuntimeException e) {
                    throw new MojoExecutionException("Could not load schema [" + set.getJsonSchema() + "]: " + e.getMessage(), e);
                }

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
        } finally {
            resolver.close();
        }

        if (encounteredError) {
            throw new MojoExecutionException("Some files are not valid, see previous logs");
        }
    }

}
