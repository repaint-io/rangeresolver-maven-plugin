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
}
