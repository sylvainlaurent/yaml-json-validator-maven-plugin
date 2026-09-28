package com.github.sylvainlaurent.maven.yamljsonvalidator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.regex.RegularExpressionFactory;
import java.net.URL;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RegexRuntimeTest {
    @Test
    void advanced_ecma_patterns_and_unicode_survive_isolation() {
        ObjectMapper mapper = new ObjectMapper();
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try (RegexRuntime runtime = TestRuntime.create()) {
            SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
                    b -> b.schemaRegistryConfig(SchemaRegistryConfig.builder().regularExpressionFactory(runtime).build()));
            String[][] examples = {
                {"(?<=a)b", "ab", "cb"}, {"(?<name>a)\\k<name>", "aa", "ab"},
                {"^.$", "\uD83D\uDE00", "ab"}, {"^\\p{L}+$", "\u00e9", "123"},
                {"^\\u{1F600}$", "\uD83D\uDE00", "x"}, {"x", "axb", "abc"},
                {"^x$", "x", "\nx\n"}
            };
            for (String[] example : examples) {
                Schema schema = registry.getSchema(mapper.createObjectNode().put("pattern", example[0]));
                assertTrue(schema.validate(mapper.getNodeFactory().textNode(example[1])).isEmpty(), example[0]);
                assertFalse(schema.validate(mapper.getNodeFactory().textNode(example[2])).isEmpty(), example[0]);
                assertSame(previous, Thread.currentThread().getContextClassLoader());
            }
            assertThrows(RuntimeException.class, () -> runtime.getRegularExpression("["));
            assertSame(previous, Thread.currentThread().getContextClassLoader());
        }
        assertSame(previous, Thread.currentThread().getContextClassLoader());
    }

    @Test
    void private_classes_and_services_never_fall_back_to_the_parent() throws Exception {
        AtomicInteger parentLookups = new AtomicInteger();
        ClassLoader parent = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("com.oracle.") || name.startsWith("org.graalvm.")) {
                    parentLookups.incrementAndGet();
                }
                return super.loadClass(name, resolve);
            }

            @Override
            public URL getResource(String name) {
                parentLookups.incrementAndGet();
                return super.getResource(name);
            }
        };
        try (RegexRuntime.RuntimeClassLoader loader = new RegexRuntime.RuntimeClassLoader(new URL[0], parent)) {
            assertThrows(ClassNotFoundException.class, () -> loader.loadClass("org.graalvm.polyglot.Context"));
            assertThrows(ClassNotFoundException.class, () -> loader.loadClass("com.oracle.js.parser.Parser"));
            assertNull(loader.getResource("META-INF/services/com.oracle.truffle.api.provider.TruffleLanguageProvider"));
            assertFalse(loader.getResources("META-INF/services/com.oracle.truffle.api.provider.TruffleLanguageProvider").hasMoreElements());
            assertSame(RegularExpressionFactory.class, loader.loadClass(RegularExpressionFactory.class.getName()));
            assertEquals(0, parentLookups.get());
        }
    }

    @Test
    void failed_initialization_restores_the_context_classloader() {
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        RegexRuntimeLoader owner = new RegexRuntimeLoader();
        try {
            assertThrows(IllegalStateException.class, () -> owner.create(Collections.emptyList()));
        } finally {
            owner.dispose();
        }
        assertSame(previous, Thread.currentThread().getContextClassLoader());
        try (RegexRuntime runtime = TestRuntime.create()) {
            assertTrue(runtime.getRegularExpression("^[^]+$").matches("still usable"));
        }
    }
}
