package com.smxemail.rangeresolver.mojo;

import static com.google.common.truth.Truth.assertThat;
import static com.smxemail.rangeresolver.mojo.GlobSupport.convertGlobToRegex;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class GlobSupportTest {
  @ParameterizedTest
  @CsvSource(
      {"test*, test,     true",
       "test*, testglob, true",
       "test*, globtest, false",
       "test,  testglob, false",
       "test,  test,     true"})
  public void
      testGlob(String glob, String match, boolean matches) {
    assertThat(convertGlobToRegex(glob).matcher(match).matches()).isEqualTo(matches);
  }
}
