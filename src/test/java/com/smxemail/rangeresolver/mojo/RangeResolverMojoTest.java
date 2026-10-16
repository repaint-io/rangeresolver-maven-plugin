package com.smxemail.rangeresolver.mojo;

import com.google.common.collect.HashMultimap;

import dev.zazr.collection.HashSet;
import dev.zazr.collection.List;

import org.junit.jupiter.api.Test;

import com.smxemail.rangeresolver.api.Range;
import com.smxemail.rangeresolver.api.RangeResolver;
import com.smxemail.rangeresolver.api.Resolved;

import static dev.zazr.control.Option.none;

import static org.assertj.core.api.Assertions.assertThat;

import static com.smxemail.rangeresolver.api.ArtifactType.DOCKER;
import static com.smxemail.rangeresolver.api.ResolvedType.RESOLVED;

class RangeResolverMojoTest {

  Resolved resolved = Resolved.of(
      "",
      DOCKER,
      "",
      "eclipse-temurin",
      "sha256:12ded611adf81135d72a57ab0c1837178e17fb6ccdecb88f5e3b85e5c45c078c",
      none(),
      "",
      Range.fixed("24"),
      RESOLVED);

  RangeResolver resolver = RangeResolver.of(
      HashMultimap.create(),
      List.empty(),
      HashSet.empty(),
      HashSet.empty(),
      List.empty(),
      List.empty(),
      List.of(resolved),
      List.empty(),
      List.empty(),
      List.empty());

  @Test
  public void rewriteDockerLines() {

    // spotless:off
    var dockerLines = """
      FROM eclipse-temurin:24 AS builder
      """.trim().lines().collect(List.collector());
    // spotless:on

    var newLines = RangeResolverMojo.rewriteDockerLines(resolver, dockerLines);

    // spotless:off
    var expected = """
      FROM eclipse-temurin@sha256:12ded611adf81135d72a57ab0c1837178e17fb6ccdecb88f5e3b85e5c45c078c AS builder
      """.trim();
    // spotless:on

    assertThat(newLines).isEqualTo(expected);
  }

  @Test
  public void rewriteDockerShaLines() {

    // spotless:off
    var dockerLines = """
      FROM eclipse-temurin@sha256:83177670e43c17769a5402a60601545318e45e63adf634fb5022340e516cded4 AS builder
      """.trim().lines().collect(List.collector());
    // spotless:on

    var newLines = RangeResolverMojo.rewriteDockerLines(resolver, dockerLines);

    // spotless:off
    var expected = """
      FROM eclipse-temurin@sha256:12ded611adf81135d72a57ab0c1837178e17fb6ccdecb88f5e3b85e5c45c078c AS builder
      """.trim();
    // spotless:on

    assertThat(newLines).isEqualTo(expected);
  }

  @Test
  public void referencedKeysIncludesTileXmlBuildAndProfilePlugins() throws Exception {

    // spotless:off
    var tileXml = """
      <project xmlns="http://maven.apache.org/POM/4.0.0">
        <properties>
          <failBuildOnCVSS>5</failBuildOnCVSS>
        </properties>
        <build>
          <plugins>
            <plugin>
              <groupId>org.cyclonedx</groupId>
              <artifactId>cyclonedx-maven-plugin</artifactId>
              <version>2.9.1</version>
            </plugin>
            <plugin>
              <groupId>io.repaint.maven</groupId>
              <artifactId>tiles-maven-plugin</artifactId>
              <configuration>
                <tiles>
                  <tile>com.smxemail.tiles:com.smxemail.tiles.release:[2.2.130]</tile>
                </tiles>
              </configuration>
            </plugin>
          </plugins>
        </build>
        <profiles>
          <profile>
            <id>security</id>
            <build>
              <plugins>
                <plugin>
                  <groupId>org.owasp</groupId>
                  <artifactId>dependency-check-maven</artifactId>
                  <version>12.1.0</version>
                </plugin>
              </plugins>
            </build>
          </profile>
        </profiles>
      </project>
      """;
    // spotless:on

    var root = new org.jdom2.input.SAXBuilder()
        .build(new java.io.StringReader(tileXml))
        .getRootElement();

    assertThat(RangeResolverMojo.referencedKeys(root))
        .contains(
            "org.cyclonedx:cyclonedx-maven-plugin",
            "org.owasp:dependency-check-maven",
            "io.repaint.maven:tiles-maven-plugin",
            "com.smxemail.tiles:com.smxemail.tiles.release");
    assertThat(RangeResolverMojo.propertyNames(root)).contains("failBuildOnCVSS");
  }
}
