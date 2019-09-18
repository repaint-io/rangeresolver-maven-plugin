package com.smxemail.rangeresolver.mojo;

import com.smxemail.rangeresolver.api.Range;
import com.smxemail.rangeresolver.api.RangeResolver;
import com.smxemail.rangeresolver.api.Resolved;
import com.smxemail.rangeresolver.api.ResolvedType;
import com.smxemail.rangeresolver.lang.RangeResolverProcessor;
import io.vavr.collection.HashSet;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Mojo(name = "resolve-properties", defaultPhase = LifecyclePhase.INITIALIZE, threadSafe = true)
public class ResolvePropertiesMojo extends AbstractRangeResolverMojo {

  @Override
  public void execute() throws MojoExecutionException {

    getLog().info("Added project/dependency resolution as session properties...");

    File deps = resolveDepsFile(mavenSession);
    if (deps.exists()) {

      try {
        String resolutionSource = getSourceReference(mavenSession);

        RangeResolver rangeResolver = RangeResolverProcessor.parseRangeResolver(resolutionSource, new FileReader(deps), allowUnlocked, allowSnapshots);

        if (!allowDeprecatedDependencies && !rangeResolver.deprecated().isEmpty()) {
          throwDeprecationException(rangeResolver.deprecated());
          return;
        }

        List<Resolved> resolvedProjects =
            mavenSession.getAllProjects().stream()
                .map(
                    proj ->
                        Resolved.of(
                            "",
                            proj.getGroupId(),
                            proj.getArtifactId(),
                            proj.getVersion(),
                            "",
                            proj.getArtifactId() + ".version",
                            Range.fixed(project.getVersion()),
                            ResolvedType.RESOLVED))
                .collect(Collectors.toList());

        HashSet<Resolved> allResolved =
            HashSet.<Resolved>empty()
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
}
