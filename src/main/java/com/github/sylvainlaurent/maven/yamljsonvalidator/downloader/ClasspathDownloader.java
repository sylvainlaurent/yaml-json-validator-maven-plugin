package com.github.sylvainlaurent.maven.yamljsonvalidator.downloader;

import java.io.InputStream;
import java.net.URI;

import com.github.fge.jsonschema.core.load.download.URIDownloader;

/**
 * <p>Enables loading ref's in schemas from classpath. E.g.:
 * <code>"$ref": "classpath:schema/includes/a-include.json"</code> </p>
 * <p>This only works, if the schemas are really on the classpath.
 * Adding them only via project dependencies is not enough. They need to be added
 * to the plugin dependencies so they will be available through the plugin classloader.</p>
 * <p>To make this all work, schemas defined by the configuration element <code>&#060;jsonSchema&#062;someSchema.json&#060;/jsonSchema&#062;</code>
 * will also be searched on the classpath if not found on the filesystem.</p>
*/
public class ClasspathDownloader implements URIDownloader {

    @Override
    public InputStream fetch(URI source) {
        return this.getClass().getClassLoader().getResourceAsStream(source.getSchemeSpecificPart());
    }
}
