package com.github.sylvainlaurent.maven.yamljsonvalidator;

import com.networknt.schema.AbsoluteIri;
import java.net.URI;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

public class SchemaCatalogTest {
    @TempDir
    Path directory;

    @Test
    public void exact_match_wins_in_either_declaration_order() {
        SchemaMapping exact = mapping("https://example.com/schema.json", "classpath:exact.json");
        SchemaMapping prefix = mapping("https://example.com/*", "classpath:prefix/");
        assertEquals("classpath:exact.json", resolve("https://example.com/schema.json", prefix, exact));
        assertEquals("classpath:exact.json", resolve("https://example.com/schema.json", exact, prefix));
    }

    @Test
    public void longest_prefix_wins_and_preserves_the_suffix() {
        SchemaMapping general = mapping("https://example.com/*", "classpath:general/");
        SchemaMapping specific = mapping("https://example.com/schemas/*", "classpath:specific/");
        String uri = "https://example.com/schemas/nested/a%20b.json";
        assertEquals("classpath:specific/nested/a%20b.json", resolve(uri, general, specific));
        assertEquals("classpath:specific/nested/a%20b.json", resolve(uri, specific, general));
        assertEquals("classpath:specific/", resolve("https://example.com/schemas/", specific));
        assertNull(resolve("https://example.com/schemas-other/a.json", specific));
    }

    @Test
    public void maps_urns_without_chaining() {
        assertEquals("classpath:schemas/example.json", resolve("urn:example.json", mapping("urn:*", "classpath:schemas/")));
        assertEquals("https://example.com/schema.json", resolve("urn:example:schema",
                mapping("urn:example:schema", "https://example.com/schema.json"),
                mapping("https://example.com/schema.json", "classpath:should-not-be-used.json")));
    }

    @Test
    public void file_destinations_are_project_relative_and_uri_encoded() {
        assertEquals(directory.resolve("local copies/a b.json").toUri(),
                URI.create(resolve("urn:example:schema", mapping("urn:example:schema", "local copies/a b.json"))));
        assertEquals(directory.resolve("local copies/a b.json").toUri(),
                URI.create(resolve("https://example.com/a%20b.json", mapping("https://example.com/*", "local copies/"))));
        String absolute = directory.resolve("absolute.json").toString();
        assertEquals(directory.resolve("absolute.json").toUri(),
                URI.create(resolve("urn:example:schema", mapping("urn:example:schema", absolute))));
    }

    @Test
    public void accepts_explicit_file_and_remote_destinations() {
        String file = directory.resolve("schema.json").toUri().toString();
        assertEquals(file, resolve("urn:example:schema", mapping("urn:example:schema", file)));
        assertEquals("https://mirror.example/schema.json", resolve("urn:example:schema",
                mapping("urn:example:schema", "https://mirror.example/schema.json")));
    }

    @Test
    public void rejects_empty_entries_and_duplicate_sources() {
        for (String empty : new String[] {null, "", "  "}) {
            assertThrows(IllegalArgumentException.class, () -> catalog(mapping(empty, "local.json")));
            assertThrows(IllegalArgumentException.class, () -> catalog(mapping("urn:example:schema", empty)));
        }
        assertThrows(IllegalArgumentException.class, () -> catalog((SchemaMapping) null));
        assertThrows(IllegalArgumentException.class, () -> catalog(
                mapping("urn:example:schema", "a.json"), mapping("urn:example:schema", "b.json")));
        assertThrows(IllegalArgumentException.class, () -> catalog(
                mapping("https://example.com/*", "a/"), mapping("https://example.com/*", "b/")));
    }

    @Test
    public void rejects_invalid_sources_and_destinations() {
        for (String source : new String[] {"*", "relative.json", "https://example.com/**",
                "https://example.com/*/schema.json", "https://example.com/a b.json", "https://example.com/a.json#part"}) {
            assertThrows(IllegalArgumentException.class, () -> catalog(mapping(source, "local.json")), source);
        }
        for (String target : new String[] {"classpath:schemas/*", "ftp://example.com/a.json", "https://example.com/a.json#part"}) {
            assertThrows(IllegalArgumentException.class, () -> catalog(mapping("urn:example:schema", target)), target);
        }
    }

    @Test
    public void distinguishes_windows_paths_from_uri_schemes() {
        assertFalse(SchemaCatalog.hasScheme("C:\\schemas\\root.json"));
        assertFalse(SchemaCatalog.hasScheme("C:/schemas/root.json"));
        assertTrue(SchemaCatalog.hasScheme("urn:example:schema"));
    }

    static SchemaMapping mapping(String from, String to) {
        SchemaMapping mapping = new SchemaMapping();
        mapping.setFrom(from);
        mapping.setTo(to);
        return mapping;
    }

    private SchemaCatalog catalog(SchemaMapping... mappings) {
        return new SchemaCatalog(directory.toFile(), mappings);
    }

    private String resolve(String identifier, SchemaMapping... mappings) {
        AbsoluteIri result = catalog(mappings).resolve(AbsoluteIri.of(identifier));
        return result == null ? null : result.toString();
    }
}
