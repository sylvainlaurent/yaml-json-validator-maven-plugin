package com.github.sylvainlaurent.maven.yamljsonvalidator.downloader;

import java.io.InputStream;
import java.net.URI;

import com.github.fge.jsonschema.core.load.download.URIDownloader;

public class ClasspathDownloader implements URIDownloader {

    @Override
    public InputStream fetch(URI source) {
        return this.getClass().getClassLoader().getResourceAsStream(source.getSchemeSpecificPart());
    }
}
