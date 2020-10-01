package com.smxemail.rangeresolver.mojo;

import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static com.smxemail.rangeresolver.mojo.VerifyDependenciesMojo.expandVersionToUpperRange;

public class VerifyDependenciesMojoTest {

  @Test
  public void testExpandVersionToUpperRange() throws Exception {
    assertThat(expandVersionToUpperRange("1.3.2")).isEqualTo("[1.3.2,2.0.0)");
    assertThat(expandVersionToUpperRange("[1.3.2]")).isEqualTo("[1.3.2]");
    assertThat(expandVersionToUpperRange("(1.3.2]")).isEqualTo("(1.3.2]");
  }
}
