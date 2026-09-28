# yaml-json-validator-maven-plugin

[![Build Status](https://travis-ci.org/sylvainlaurent/yaml-json-validator-maven-plugin.svg?branch=master)](https://travis-ci.org/sylvainlaurent/yaml-json-validator-maven-plugin)

This maven plugin allows to validate yaml and json files to check that they are well formed and optionally validate against JSON schemas.

Both JSON and YAML files can be validated against a JSON schema. The library [NetworkNT JSON Schema Validator](https://github.com/networknt/json-schema-validator) is internally used for this.

Supported schema drafts are Draft 4, Draft 6, Draft 7, 2019-09, and 2020-12.
Schemas without a `$schema` declaration use Draft 4. Draft 3 is no longer supported.
Schema loading and input validation follow NetworkNT's behavior. The plugin does
not perform a separate check of schema documents against their meta-schemas.

Regular expressions use NetworkNT's GraalJS adapter for ECMA-262 semantics in
`pattern`, `patternProperties`, and regex format checks. The plugin selects a
pinned runtime for the JVM running Maven (independently of compiler toolchains):

| Java version | GraalJS runtime |
| --- | --- |
| 8–16 | `org.graalvm.js:js:21.3.19` |
| 17+ | `org.graalvm.polyglot:js-community:pom:25.0.4`, without `truffle-runtime` |

Maven resolves the selected runtime and its dependencies through the active
repository session and plugin repositories, honoring settings, mirrors, proxies,
and the local repository. The JARs are loaded in isolation and are not bundled
with this plugin. An ordinary JDK is sufficient; no GraalVM installation is needed.
The interpreter runtime may still require Graal's native-resource cache to be writable.

Offline schema validation requires the selected runtime to be cached by an earlier
online run. `allowRemoteSchemas` controls schema retrieval, independently of Maven
artifact resolution. Validation without a schema does not resolve or start GraalJS.
The Maven goal shares one context across its validation sets and closes it on
success or failure.
The plugin's container owns the isolated classloader and releases it at shutdown;
separate executions have independent contexts while sharing runtime classes.

On Java 25/26, this runtime still produces JVM warnings about internal API access.
The following optional JVM startup flags were tested with those JVMs; users can
set them through `MAVEN_OPTS` or `.mvn/jvm.config`:

```text
--enable-native-access=ALL-UNNAMED
--sun-misc-unsafe-memory-access=allow
```

These flags permit the underlying operations; they do not remove Graal's internal
API usage. The plugin does not add them or suppress process output. It disables
Graal's interpreter-performance notice because interpreter execution is intentional.

References can use `classpath:` to load schemas from the plugin's classpath, including
dependencies declared on the plugin, as well as explicit file or HTTP(S) URLs.
Classpath resource names are exact, allowing the usual leading slash. Falling back
to another classloader never changes the requested name.

Unknown string formats, such as `"format": "boolean"`, are ignored under Draft 4.
To restrict a string to boolean spellings, use `"enum": ["true", "false"]`;
to require a JSON boolean value, use `"type": "boolean"`.

## Plugin configuration

```xml
      <plugin>
        <groupId>com.github.sylvainlaurent.maven</groupId>
        <artifactId>yaml-json-validator-maven-plugin</artifactId>
        <version>...</version>
        <executions>
          <execution>
            <id>validate</id>
            <phase>validate</phase>
            <goals>
              <goal>validate</goal>
            </goals>
            <configuration>
              <validationSets>
                <validationSet>
                  <jsonSchema>src/main/resources/my-schema.json</jsonSchema>
                  <includes>
                    <include>src/main/resources/*.json</include>
                  </includes>
                  <excludes>
                    <exclude>src/main/resources/do-not-validate*.json</exclude>
                    <!-- <exclude> is optional, others may be added -->
                  </excludes>
                </validationSet>
                <validationSet>
                  <!-- no jsonSchema is specified, check only that file are well formed -->
                  <includes>
                    <include>src/main/resources/*.yml</include>
                  </includes>
                </validationSet>
              </validationSets>
              <!-- Duplicate keys detection is turned on by default. -->
              <detectDuplicateKeys>false</detectDuplicateKeys>
              <allowEmptyFiles>false</allowEmptyFiles>
              <!-- Allow comments in json files is turned off by default -->
              <allowJsonComments>true</allowJsonComments>
              <!-- Trailing commas are not allwed by default -->
              <allowTrailingComma>true</allowTrailingComma>
              <skip>false</skip>
            </configuration>
          </execution>
        </executions>
      </plugin>
```

Validation failures make the build fail.

## Schema locations and mappings

`jsonSchema` accepts a file path, a `file:` URI, a `classpath:` resource, an HTTP(S)
URL, or an identifier resolved through `schemaMappings`. Relative file paths are
resolved against the current Maven project's base directory. Plain names are
searched on the filesystem first, then on the plugin's classpath.

Mappings apply to every validation set in the plugin execution, including the root
schema and nested `$ref` references. For example, add these elements alongside
`validationSets` in the plugin's `configuration`:

```xml
<schemaMappings>
  <schemaMapping>
    <from>https://example.com/schemas/special.json</from>
    <to>src/main/schemas/special.json</to>
  </schemaMapping>
  <schemaMapping>
    <from>https://example.com/schemas/*</from>
    <to>classpath:schemas/</to>
  </schemaMapping>
</schemaMappings>
<allowRemoteSchemas>false</allowRemoteSchemas>
```

- An exact match wins over a prefix. Otherwise, the longest matching prefix wins,
  regardless of declaration order. Only one mapping is applied; mappings do not chain.
- A single trailing `*` in `from` designates a prefix. The remaining suffix,
  including any subdirectories, is appended to `to`. For example,
  `https://example.com/schemas/common/address.json` maps to
  `classpath:schemas/common/address.json`. Include the trailing `/` in a directory
  destination. There are no wildcards in `to`, and other glob or regex patterns
  are not supported.
- Sources are absolute schema identifiers, including URLs and URNs such as
  `urn:example:order`. Matching is literal and case-sensitive. Mappings identify
  whole schema documents; omit fragments such as `#/definitions/order` from both
  sides. References may still contain fragments, which the validator resolves
  within the retrieved document.
- Destinations may be relative or absolute file paths, `file:` URIs, `classpath:`
  resources, or HTTP(S) URLs for remote mirrors. Relative file destinations use
  `${project.basedir}`. Use URI encoding for spaces in explicit URIs; ordinary file
  paths can contain spaces directly.
- Duplicate sources, empty entries, and unsupported patterns or destinations fail
  the build. If a mapped resource is missing, loading fails without trying the
  original URL.

The original schema identifier remains the base for relative references. A schema
retrieved locally for `https://example.com/schemas/order.json` can therefore refer
to `common/address.json`, which is resolved through the same mappings.

### Network access

`allowRemoteSchemas` defaults to `true`. Setting it to `false`, or running Maven
with `-o`, disables HTTP(S) retrieval for both root schemas and references. Maven
offline mode takes precedence over `allowRemoteSchemas`.

Network access is checked after mapping: a URL mapped to a local copy works
offline, while a mapping to an HTTP(S) mirror requires network access. Local files,
classpath resources, and the standard meta-schemas bundled with NetworkNT remain
available. Standard meta-schema URLs continue to use NetworkNT's bundled copies.
When an unmapped reference requires disabled network access, validation fails with
an explanation.

Requires Java 8.
