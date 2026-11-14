package io.repaint.rangeresolver.mojo;

import org.junit.jupiter.api.Test;

import static io.repaint.rangeresolver.mojo.VerifyDependenciesMojo.expandVersionToUpperRange;

import static org.assertj.core.api.Assertions.assertThat;

class VerifyDependenciesMojoTest {
    @Test
    void testExpandVersionToUpperRange() throws Exception {
        assertThat(expandVersionToUpperRange("1.3.2")).isEqualTo("[1.3.2,2.0.0)");
        assertThat(expandVersionToUpperRange("[1.3.2]")).isEqualTo("[1.3.2]");
        assertThat(expandVersionToUpperRange("(1.3.2]")).isEqualTo("(1.3.2]");
    }
}
