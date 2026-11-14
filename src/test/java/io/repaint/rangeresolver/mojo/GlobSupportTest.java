package io.repaint.rangeresolver.mojo;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static io.repaint.rangeresolver.mojo.GlobSupport.convertGlobToRegex;

import static org.assertj.core.api.Assertions.assertThat;

public class GlobSupportTest {
    @ParameterizedTest
    @CsvSource({
        "test*, test,     true",
        "test*, testglob, true",
        "test*, globtest, false",
        "test,  testglob, false",
        "test,  test,     true"
    })
    public void testGlob(String glob, String match, boolean matches) {
        assertThat(convertGlobToRegex(glob).matcher(match).matches()).isEqualTo(matches);
    }
}
