package com.github.sylvainlaurent.maven.yamljsonvalidator;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.shared.utils.io.DirectoryScanner;

import java.io.File;

public class ValidationSet {
    /**
     * may be null
     */
    private String jsonSchema;

    private String[] includes;

    private String[] excludes;

    private String baseDir;

    public String getJsonSchema() {
        return jsonSchema;
    }

    public void setJsonSchema(final String jsonSchema) {
        this.jsonSchema = jsonSchema;
    }

    public String[] getIncludes() {
        return includes;
    }

    public void setIncludes(final String[] includes) {
        this.includes = includes;
    }

    public String[] getExcludes() {
        return excludes;
    }

    public void setExcludes(final String[] excludes) {
        this.excludes = excludes;
    }

    public String getBaseDir() {
        return baseDir;
    }

    public void setBaseDir(String baseDir) {
        this.baseDir = baseDir;
    }

    public static File[] getFiles(final ValidationSet validationSet, final Log log, final File projectBaseDir,
                                  final boolean followSymlinks, final boolean addDefaultExcludes) {
        final DirectoryScanner ds = new DirectoryScanner();
        final String validationSetBaseDirStr = validationSet.getBaseDir();
        final File validationSetBaseDir = validationSetBaseDirStr != null
                ? new File(validationSetBaseDirStr) : projectBaseDir;
        ds.setBasedir(validationSetBaseDir);
        ds.setFollowSymlinks(followSymlinks);
        if (addDefaultExcludes) {
            ds.addDefaultExcludes();
        }
        if (validationSet.includes != null && validationSet.includes.length > 0) {
            ds.setIncludes(validationSet.includes);
        }
        if (validationSet.excludes != null && validationSet.excludes.length > 0) {
            ds.setExcludes(validationSet.excludes);
        }

        if (validationSetBaseDir == null
                || !validationSetBaseDir.exists()
                || !validationSetBaseDir.isDirectory()) {
            log.warn("Directory " + validationSetBaseDir + " does not exist or is not a directory. Skipping.");
            return new File[0];
        }

        ds.scan();
        final String[] filePaths = ds.getIncludedFiles();
        final File[] files = new File[filePaths.length];

        for (int i = 0; i < filePaths.length; i++) {
            files[i] = new File(validationSetBaseDir, filePaths[i]);
        }

        return files;
    }

}
