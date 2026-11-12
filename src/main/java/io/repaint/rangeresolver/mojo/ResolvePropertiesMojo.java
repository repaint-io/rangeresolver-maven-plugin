package io.repaint.rangeresolver.mojo;

import dev.zazr.collection.HashSet;
import dev.zazr.control.Option;

import io.repaint.rangeresolver.api.Range;
import io.repaint.rangeresolver.api.RangeResolver;
import io.repaint.rangeresolver.api.Resolved;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static io.repaint.rangeresolver.api.ArtifactType.MAVEN;
import static io.repaint.rangeresolver.api.ResolvedType.RESOLVED;
import static io.repaint.rangeresolver.lang.RangeResolverProcessor.parseRangeResolver;

import static java.nio.charset.StandardCharsets.UTF_8;

@Mojo(name = "resolve-properties", defaultPhase = LifecyclePhase.INITIALIZE, threadSafe = true)
public class ResolvePropertiesMojo extends AbstractRangeResolverMojo {
  @Override
  public void execute() throws MojoExecutionException {
    getLog().info("Added project/dependency resolution as session properties...");

    File deps = resolveDepsFile(mavenSession);
    if (deps.exists()) {
      try {
        String resolutionSource = getSourceReference(mavenSession);

        RangeResolver rangeResolver = parseRangeResolver(
            resolutionSource, Files.newBufferedReader(deps.toPath(), UTF_8), allowUnlocked, allowSnapshots);

        if (!allowDeprecatedDependencies && !rangeResolver.deprecated().isEmpty()) {
          throwDeprecationException(rangeResolver.deprecated());
          return;
        }

        List<Resolved> resolvedProjects = mavenSession.getAllProjects().stream()
            .map(this::resolveProject)
            .toList();

        HashSet<Resolved> allResolved = HashSet.<Resolved>empty()
            .addAll(resolvedProjects)
            .addAll(rangeResolver.resolved())
            .addAll(rangeResolver.locked())
            .addAll(rangeResolver.deprecated());

        for (MavenProject mavenProject : mavenSession.getAllProjects()) {
          for (Resolved resolved : allResolved) {
            mavenProject.getProperties().put(resolved.propertyName(), resolved.version());
          }
        }

      } catch (IOException e) {
        throw new MojoExecutionException(e.getMessage(), e);
      }
    } else {
      getLog().warn("Skipping property resolution - deps file is missing.");
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
        Range.fixed(project.getVersion()),
        RESOLVED);
  }
}
