# yaml-json-validator-maven-plugin

[![Build Status](https://travis-ci.org/sylvainlaurent/yaml-json-validator-maven-plugin.svg?branch=master)](https://travis-ci.org/sylvainlaurent/yaml-json-validator-maven-plugin)

This maven plugin allows to validate yaml and json files to check that they are well formed and optionally validate against JSON schemas.

Both JSON and YAML files can be validated against a JSON schema. The library [NetworkNT JSON Schema Validator](https://github.com/networknt/json-schema-validator) is internally used for this.

Supported schema drafts are Draft 4, Draft 6, Draft 7, 2019-09, and 2020-12.
Schemas without a `$schema` declaration use Draft 4. Draft 3 is no longer supported.
References can use `classpath:` to load schemas from the plugin's classpath, including
dependencies declared on the plugin, as well as explicit file or HTTP(S) URLs.

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
