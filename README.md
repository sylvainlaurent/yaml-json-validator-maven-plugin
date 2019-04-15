# yaml-json-validator-maven-plugin

This maven plugin allows to validate yaml and json files to check that they are well formed and optionally validate against JSON schemas.

Both JSON and YAML files can be validated against a JSON schema. The library [fge/json-schema-validator](https://github.com/fge/json-schema-validator) is internally used for this.

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
              <allowEmptyFiles>false</allowEmptyFiles>
              <skip>false</skip>
            </configuration>
          </execution>
        </executions>
      </plugin>
```

Validation failures make the build fail.

Requires java 1.7.
