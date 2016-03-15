# yaml-json-validator-maven-plugin

This maven plugin allows to validate yaml and json files to check that they are well formed and optionally validate against JSON schemas.

## plugin configuration

```
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
              <includes>
                <include>src/main/resources/*.yml</include>
                <include>src/main/resources/*.json</include>
                <!-- other <include> may be added -->
              </includes>
              <excludes>
                <exclude>src/main/resources/do-not-validate*.yml</exclude>
                <!-- <exclude> is optional, others may be added -->
              </excludes>
            </configuration>
          </execution>
        </executions>
      </plugin>
```

Validation failures make the build fail.

Requires java 1.7.
