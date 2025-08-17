package com.smxemail.rangeresolver.mojo;

import com.google.common.collect.HashMultimap;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;

import org.junit.jupiter.api.Test;

import com.smxemail.rangeresolver.api.Range;
import com.smxemail.rangeresolver.api.RangeResolver;
import com.smxemail.rangeresolver.api.Resolved;

import static com.google.common.truth.Truth.assertThat;

import static io.vavr.control.Option.none;

import static com.smxemail.rangeresolver.api.ArtifactType.DOCKER;
import static com.smxemail.rangeresolver.api.ResolvedType.RESOLVED;

class RangeResolverMojoTest {

  @Test
  public void rewriteDockerLines() {

    Resolved resolved = Resolved.of(
        "",
        DOCKER,
        "",
        "eclipse-temurin",
        "12ded611adf81135d72a57ab0c1837178e17fb6ccdecb88f5e3b85e5c45c078c",
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
}
