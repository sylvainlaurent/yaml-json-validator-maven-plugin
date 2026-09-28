package com.github.sylvainlaurent.maven.yamljsonvalidator;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import com.networknt.schema.SchemaException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;
import static com.github.sylvainlaurent.maven.yamljsonvalidator.SchemaCatalogTest.mapping;
import static com.github.sylvainlaurent.maven.yamljsonvalidator.SchemaResolverTest.assertPositiveInteger;

public class RemoteSchemaTest {
    @TempDir
    Path directory;
    private HttpServer server;
    private final List<SchemaResolver> resolvers = new ArrayList<>();
    private final AtomicInteger requests = new AtomicInteger();

    @BeforeEach
    public void start_server() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        configure(server);
        server.start();
    }

    @AfterEach
    public void stop_server() {
        resolvers.forEach(SchemaResolver::close);
        server.stop(0);
    }

    @Test
    public void http_root_loads_relative_references() throws Exception {
        assertPositiveInteger(resolver(null, true, false).load(url()));
        assertEquals(2, requests.get(), "Both root and referenced schema must be fetched");
    }

    @Test
    public void remote_mirror_uses_the_same_mapping_for_relative_references() throws Exception {
        SchemaMapping[] mappings = {mapping("https://example.invalid/*", baseUrl())};
        assertPositiveInteger(resolver(mappings, true, false).load("https://example.invalid/root.json"));
        assertEquals(2, requests.get());
    }

    @Test
    public void network_policy_blocks_roots_and_mirrors_before_any_request() {
        SchemaMapping[] mappings = {mapping("urn:example:root", url())};
        for (boolean offline : new boolean[] {false, true}) {
            SchemaResolver blocked = resolver(mappings, offline, offline);
            for (String root : new String[] {url(), "urn:example:root"}) {
                SchemaException error = assertThrows(SchemaException.class, () -> blocked.load(root));
                String message = messages(error);
                assertTrue(message.contains(offline ? "Maven is offline" : "allowRemoteSchemas is false"), message);
                assertTrue(message.contains(url()), message);
            }
        }
        assertEquals(0, requests.get());
    }

    @Test
    public void network_policy_blocks_nested_references_before_any_request() throws Exception {
        Path root = directory.resolve("local.json");
        Files.write(root, ("{\"$ref\":\"" + url() + "\"}").getBytes(StandardCharsets.UTF_8));
        Path input = directory.resolve("input.json");
        Files.write(input, "2".getBytes(StandardCharsets.UTF_8));
        for (boolean offline : new boolean[] {false, true}) {
            ValidationService service = new ValidationService(
                    resolver(null, offline, offline).load(root.toString()), false, true, false, false);
            ValidationResult result = service.validate(input.toFile());
            assertTrue(result.hasError());
            String messages = result.getMessages().toString();
            assertTrue(messages.contains(offline ? "Maven is offline" : "allowRemoteSchemas is false"), messages);
        }
        assertEquals(0, requests.get());
    }

    @Test
    public void mapped_local_copy_works_with_network_disabled_and_offline() throws Exception {
        Path local = directory.resolve("local.json");
        Files.write(local, "{\"type\":\"integer\",\"minimum\":1}".getBytes(StandardCharsets.UTF_8));
        SchemaMapping[] mappings = {mapping(url(), local.toString())};
        assertPositiveInteger(resolver(mappings, false, false).load(url()));
        assertPositiveInteger(resolver(mappings, true, true).load(url()));
        assertEquals(0, requests.get());
    }

    @Test
    public void missing_mapped_resource_never_falls_back_to_network() {
        for (String target : new String[] {"missing.json", "classpath:missing-schema.json"}) {
            SchemaResolver mapped = resolver(new SchemaMapping[] {mapping(url(), target)}, true, false);
            assertThrows(SchemaException.class, () -> mapped.load(url()));
        }
        assertEquals(0, requests.get());
    }

    @Test
    public void https_root_uses_normal_certificate_and_hostname_verification() throws Exception {
        SSLContext tls = tlsContext();
        HttpsServer https = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        https.setHttpsConfigurator(new HttpsConfigurator(tls));
        configure(https);
        SSLSocketFactory original = HttpsURLConnection.getDefaultSSLSocketFactory();
        try {
            https.start();
            // Trust only this test's certificate; hostname verification remains enabled.
            HttpsURLConnection.setDefaultSSLSocketFactory(tls.getSocketFactory());
            String root = "https://127.0.0.1:" + https.getAddress().getPort() + "/schemas/root.json";
            assertPositiveInteger(resolver(null, true, false).load(root));
            assertThrows(SchemaException.class, () -> resolver(null, true, true).load(root));
            assertEquals(2, requests.get(), "Offline HTTPS retrieval must not send an additional request");
        } finally {
            HttpsURLConnection.setDefaultSSLSocketFactory(original);
            https.stop(0);
        }
    }

    private SchemaResolver resolver(SchemaMapping[] mappings, boolean remote, boolean offline) {
        SchemaResolver resolver = new SchemaResolver(directory.toFile(), mappings, remote, offline, TestRuntime::create);
        resolvers.add(resolver);
        return resolver;
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/schemas/";
    }

    private String url() {
        return baseUrl() + "root.json";
    }

    private void configure(HttpServer target) {
        target.createContext("/schemas/", exchange -> {
            requests.incrementAndGet();
            String body;
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/schemas/root.json")) {
                body = "{\"$ref\":\"integer.json\"}";
            } else if (path.equals("/schemas/integer.json")) {
                body = "{\"type\":\"integer\",\"minimum\":1}";
            } else {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/schema+json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
    }

    private SSLContext tlsContext() throws Exception {
        Path keyStore = directory.resolve("localhost.jks");
        String executable = System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool";
        Path keytool = Paths.get(System.getProperty("java.home"), "bin", executable);
        Path log = directory.resolve("keytool.log");
        Process process = new ProcessBuilder(keytool.toString(), "-genkeypair", "-alias", "localhost",
                "-keyalg", "RSA", "-keysize", "2048", "-keystore", keyStore.toString(), "-storetype", "JKS",
                "-storepass", "test-password", "-keypass", "test-password", "-dname", "CN=localhost",
                "-ext", "SAN=ip:127.0.0.1", "-validity", "2", "-noprompt")
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "keytool timed out");
            assertEquals(0, process.exitValue(), new String(Files.readAllBytes(log), StandardCharsets.UTF_8));
        } finally {
            process.destroy();
        }
        KeyStore keys = KeyStore.getInstance("JKS");
        try (InputStream input = Files.newInputStream(keyStore)) {
            keys.load(input, "test-password".toCharArray());
        }
        KeyManagerFactory km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        km.init(keys, "test-password".toCharArray());
        TrustManagerFactory tm = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tm.init(keys);
        SSLContext tls = SSLContext.getInstance("TLS");
        tls.init(km.getKeyManagers(), tm.getTrustManagers(), null);
        return tls;
    }

    private String messages(Throwable error) {
        StringBuilder result = new StringBuilder();
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            result.append(cause.getMessage()).append('\n');
        }
        return result.toString();
    }
}
