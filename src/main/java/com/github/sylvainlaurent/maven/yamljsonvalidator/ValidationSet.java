package com.github.sylvainlaurent.maven.yamljsonvalidator;

import org.apache.maven.shared.utils.io.DirectoryScanner;

import java.io.File;

public class ValidationSet {
    /**
     * may be null
     */
    private String jsonSchema;

    private String[] includes;

    private String[] excludes;

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

    public static File[] getFiles(final ValidationSet validationSet, final File basedir, boolean followSymlinks) {
        final DirectoryScanner ds = new DirectoryScanner();
        ds.setBasedir(basedir);
        ds.setFollowSymlinks(followSymlinks);
        if (validationSet.includes != null && validationSet.includes.length > 0) {
            ds.setIncludes(validationSet.includes);
        }
        if (validationSet.excludes != null && validationSet.excludes.length > 0) {
            ds.setExcludes(validationSet.excludes);
        }
        ds.scan();
        final String[] filePaths = ds.getIncludedFiles();
        final File[] files = new File[filePaths.length];

        for (int i = 0; i < filePaths.length; i++) {
            files[i] = new File(basedir, filePaths[i]);
        }

        return files;
    }

}
