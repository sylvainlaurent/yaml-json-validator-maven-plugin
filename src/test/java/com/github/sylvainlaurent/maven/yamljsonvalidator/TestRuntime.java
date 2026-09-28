package com.github.sylvainlaurent.maven.yamljsonvalidator;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Maven resolves the test runtime through the same build session as other test dependencies. */
final class TestRuntime {
    static final URI DOCUMENT = new File("target/stream-schema.json").toURI();
    private static final RegexRuntimeLoader LOADER = new RegexRuntimeLoader();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(LOADER::dispose, "test-regex-loader-disposal"));
    }

    static RegexRuntime create() {
        return LOADER.create(jars());
    }

    private static List<URL> jars() {
        try {
            String classpath = new String(Files.readAllBytes(Paths.get("target/test-runtime-classpath.txt")),
                    StandardCharsets.UTF_8).trim();
            List<URL> jars = new ArrayList<>();
            for (String path : classpath.split(Pattern.quote(File.pathSeparator))) {
                if (path.endsWith(".jar")) {
                    jars.add(new File(path).toURI().toURL());
                }
            }
            return jars;
        } catch (IOException e) {
            throw new IllegalStateException("Run Maven generate-test-resources to resolve the test runtime", e);
        }
    }
}
