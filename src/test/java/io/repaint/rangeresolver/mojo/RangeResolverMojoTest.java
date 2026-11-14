package io.repaint.rangeresolver.mojo;

import com.google.common.collect.HashMultimap;

import dev.zazr.collection.HashSet;
import dev.zazr.collection.List;

import io.repaint.rangeresolver.api.Range;
import io.repaint.rangeresolver.api.RangeResolver;
import io.repaint.rangeresolver.api.Resolved;

import org.jdom2.input.SAXBuilder;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.util.Map;
import java.util.Set;

import static dev.zazr.control.Option.none;

import static io.repaint.rangeresolver.api.ArtifactType.DOCKER;
import static io.repaint.rangeresolver.api.ArtifactType.MAVEN;
import static io.repaint.rangeresolver.api.ResolvedType.RESOLVED;

import static org.assertj.core.api.Assertions.assertThat;

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

        var dockerLines = """
        FROM eclipse-temurin@sha256:83177670e43c17769a5402a60601545318e45e63adf634fb5022340e516cded4 AS builder
        """.trim().lines().collect(List.collector());

        var newLines = RangeResolverMojo.rewriteDockerLines(resolver, dockerLines);

        var expected = """
        FROM eclipse-temurin@sha256:12ded611adf81135d72a57ab0c1837178e17fb6ccdecb88f5e3b85e5c45c078c AS builder
        """.trim();

        assertThat(newLines).isEqualTo(expected);
    }

    @Test
    public void referencedKeysIncludesTileXmlBuildAndProfilePlugins() throws Exception {

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
                    <tile>io.repaint:io.repaint.tiles.enforcement:[1.0.3]</tile>
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

        var root = new SAXBuilder().build(Reader.of(tileXml)).getRootElement();

        assertThat(RangeResolverMojo.referencedKeys(root))
                .contains(
                        "org.cyclonedx:cyclonedx-maven-plugin",
                        "org.owasp:dependency-check-maven",
                        "io.repaint.maven:tiles-maven-plugin",
                        "io.repaint:io.repaint.tiles.enforcement");
        assertThat(RangeResolverMojo.propertyNames(root)).contains("failBuildOnCVSS");
    }

    static final String EXTENSIONS_XML = """
      <extensions>
        <extension>
          <groupId>eu.maveniverse.maven.nisse</groupId>
          <artifactId>extension</artifactId>
          <version>0.9.11</version>
        </extension>
      </extensions>
      """;

    @Test
    public void extensionKeysFromExtensionsXml() throws Exception {
        var root = new SAXBuilder().build(Reader.of(EXTENSIONS_XML)).getRootElement();

        assertThat(RangeResolverMojo.extensionKeys(root)).containsExactly("eu.maveniverse.maven.nisse:extension");
    }

    @Test
    public void rewriteExtensionsUpdatesVersion() throws Exception {
        var root = new SAXBuilder()
                .build(Reader.of(EXTENSIONS_XML.replace(
                        "<extensions>", "<extensions xmlns=\"http://maven.apache.org/EXTENSIONS/1.2.0\">")))
                .getRootElement();

        var nisse = Resolved.of(
                "",
                MAVEN,
                "eu.maveniverse.maven.nisse",
                "extension",
                "0.9.12",
                none(),
                "",
                Range.fixed("0.9.12"),
                RESOLVED);

        var changed = RangeResolverMojo.rewriteExtensions(Map.of(nisse.key(), nisse), Set.of(), root);

        assertThat(changed).isTrue();
        assertThat(root.getChild("extension", root.getNamespace()).getChildText("version", root.getNamespace()))
                .isEqualTo("0.9.12");

        assertThat(RangeResolverMojo.rewriteExtensions(Map.of(nisse.key(), nisse), Set.of(), root))
                .isFalse();
    }
}
