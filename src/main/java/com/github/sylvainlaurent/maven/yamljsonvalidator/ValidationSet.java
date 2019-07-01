package com.github.sylvainlaurent.maven.yamljsonvalidator;

import java.io.File;

import org.codehaus.plexus.util.DirectoryScanner;

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

    public File[] getFiles(final File basedir) {
        final DirectoryScanner ds = new DirectoryScanner();
        ds.setBasedir(basedir);
        if (includes != null && includes.length > 0) {
            ds.setIncludes(includes);
        }
        if (excludes != null && excludes.length > 0) {
            ds.setExcludes(excludes);
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
