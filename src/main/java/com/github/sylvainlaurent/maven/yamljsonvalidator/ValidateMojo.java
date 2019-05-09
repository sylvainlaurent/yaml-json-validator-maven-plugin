package com.github.sylvainlaurent.maven.yamljsonvalidator;

import java.io.File;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * This mojo validates YAML and JSON files for well-formedness. If JSON schema is provided, it also
 * validates JSON files against it.
 */
@Mojo(name = "validate", defaultPhase = LifecyclePhase.PROCESS_SOURCES, threadSafe = true)
public class ValidateMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

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
     * Set to <code>true</code> to allow comments C style comments in JSON and YAML files without hickups.
     */
    @Parameter(defaultValue = "true")
    private boolean allowComments;

    @Override
    public void execute() throws MojoExecutionException {
        boolean encounteredError = false;

        if (skip) {
            getLog().info("Skipping validation");
            return;
        }

        for (final ValidationSet set : validationSets) {
            final ValidationService validationService = new ValidationService(set.getJsonSchema(),
                allowEmptyFiles, detectDuplicateKeys, allowComments);

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

}
