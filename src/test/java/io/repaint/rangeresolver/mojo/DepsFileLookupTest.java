package io.repaint.rangeresolver.mojo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.repaint.rangeresolver.mojo.AbstractRangeResolverMojo.findNearestDepsFile;

import static org.assertj.core.api.Assertions.assertThat;

class DepsFileLookupTest {
    @TempDir
    Path root;

    @Test
    void findsDepsFileInProjectDirectory() throws IOException {
        Path module = Files.createDirectories(root.resolve("core"));
        Path deps = Files.createFile(module.resolve("pom.deps"));

        assertThat(findNearestDepsFile(module, module.resolve("pom.xml").toFile()))
                .contains(deps.toFile());
    }

    @Test
    void findsNearestParentDepsFileWhenBuiltFromAggregator() throws IOException {
        Path core = Files.createDirectories(root.resolve("core"));
        Path module = Files.createDirectories(core.resolve("agreement"));
        Path deps = Files.createFile(core.resolve("pom.deps"));

        assertThat(findNearestDepsFile(module, root.resolve("pom.xml").toFile()))
                .contains(deps.toFile());
    }

    @Test
    void prefersNearestDepsFile() throws IOException {
        Path core = Files.createDirectories(root.resolve("core"));
        Path module = Files.createDirectories(core.resolve("agreement"));
        Files.createFile(root.resolve("pom.deps"));
        Path deps = Files.createFile(core.resolve("pom.deps"));

        assertThat(findNearestDepsFile(module, root.resolve("pom.xml").toFile()))
                .contains(deps.toFile());
    }

    @Test
    void usesDepsFileNamedAfterRequestedPom() throws IOException {
        Path module = Files.createDirectories(root.resolve("core"));
        Files.createFile(module.resolve("pom.deps"));
        Path deps = Files.createFile(module.resolve("custom.deps"));

        assertThat(findNearestDepsFile(module, module.resolve("custom.xml").toFile()))
                .contains(deps.toFile());
    }

    @Test
    void reportsMissingDepsFile() throws IOException {
        Path module = Files.createDirectories(root.resolve("core").resolve("agreement"));

        assertThat(findNearestDepsFile(module, root.resolve("pom.xml").toFile()))
                .isEmpty();
    }
}
