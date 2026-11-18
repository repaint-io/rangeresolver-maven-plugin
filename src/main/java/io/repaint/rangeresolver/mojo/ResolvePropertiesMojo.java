package io.repaint.rangeresolver.mojo;

import dev.zazr.collection.HashSet;
import dev.zazr.control.Option;

import io.repaint.rangeresolver.api.Range;
import io.repaint.rangeresolver.api.RangeResolver;
import io.repaint.rangeresolver.api.Resolved;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

import static io.repaint.rangeresolver.api.ArtifactType.MAVEN;
import static io.repaint.rangeresolver.api.ResolvedType.RESOLVED;
import static io.repaint.rangeresolver.lang.RangeResolverProcessor.parseRangeResolver;

import static java.nio.charset.StandardCharsets.UTF_8;

/// Exposes the versions declared in the deps file as properties of the project being built, so they can be referenced
/// from plugin configuration (for example, OSGi `Import-Package` version ranges).
///
/// The deps file is the one nearest to the project: its own directory is checked first, then each parent directory.
/// This lets a build started from an aggregator POM, which has no deps file of its own, still pick up the deps file of
/// each module tree it builds.
@Mojo(name = "resolve-properties", defaultPhase = LifecyclePhase.INITIALIZE, threadSafe = true)
public class ResolvePropertiesMojo extends AbstractRangeResolverMojo {
    /// Fails the build when no deps file is found, rather than warning and leaving any `${...}` references to its
    /// properties unresolved.
    @Parameter(property = "failOnMissingDeps", defaultValue = "false")
    boolean failOnMissingDeps;

    @Override
    public void execute() throws MojoExecutionException {
        File requestedPom = sourcePomFile(mavenSession);
        Optional<File> depsFile = findNearestDepsFile(project.getBasedir().toPath(), requestedPom);

        if (depsFile.isEmpty()) {
            String message = "No deps file found in %s or any parent directory"
                    .formatted(project.getBasedir().getAbsolutePath());
            if (failOnMissingDeps) {
                throw new MojoExecutionException(message);
            }
            getLog().warn(message + " - skipping property resolution.");
            return;
        }

        File deps = depsFile.orElseThrow();
        getLog().info("Adding dependency resolution from %s as project properties...".formatted(deps));

        File sourcePom = deps.toPath()
                .resolveSibling(deps.getName().substring(0, deps.getName().lastIndexOf(".") + 1) + "xml")
                .toFile();
        String resolutionSource = getSourceReference(sourcePom);

        RangeResolver rangeResolver;
        try (Reader reader = Files.newBufferedReader(deps.toPath(), UTF_8)) {
            rangeResolver = parseRangeResolver(resolutionSource, reader, allowUnlocked, allowSnapshots);
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

        if (!allowDeprecatedDependencies && !rangeResolver.deprecated().isEmpty()) {
            throwDeprecationException(rangeResolver.deprecated());
            return;
        }

        List<Resolved> reactorProjects =
                mavenSession.getAllProjects().stream().map(this::resolveProject).toList();

        HashSet<Resolved> allResolved = HashSet.<Resolved>empty()
                .addAll(reactorProjects)
                .addAll(rangeResolver.resolved())
                .addAll(rangeResolver.locked())
                .addAll(rangeResolver.deprecated());

        for (Resolved resolved : allResolved) {
            project.getProperties().put(resolved.propertyName(), resolved.version());
        }
    }

    private Resolved resolveProject(MavenProject proj) {
        return Resolved.of(
                "",
                MAVEN,
                proj.getGroupId(),
                proj.getArtifactId(),
                proj.getVersion(),
                Option.none(),
                proj.getArtifactId() + ".version",
                Range.fixed(proj.getVersion()),
                RESOLVED);
    }
}
