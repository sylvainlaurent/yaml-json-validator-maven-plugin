package com.github.sylvainlaurent.maven.yamljsonvalidator;

import java.io.File;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

@Mojo(name = "validate", defaultPhase = LifecyclePhase.PROCESS_SOURCES, threadSafe = true)
public class ValidateMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    @Parameter(defaultValue = "${project.basedir}", required = true, readonly = true)
    private File basedir;

    @Parameter
    private ValidationSet[] validationSets;

    @Parameter(defaultValue = "true")
    private boolean verbose;

    @Override
    public void execute() throws MojoExecutionException {
        boolean encounteredError = false;

        for (final ValidationSet set : validationSets) {
            final ValidationService validationService = new ValidationService(set.getJsonSchema());

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
