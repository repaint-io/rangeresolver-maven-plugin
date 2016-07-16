package com.smxemail.rangeresolver.mojo;

import com.smxemail.rangeresolver.api.Range;
import com.smxemail.rangeresolver.api.RangeResolver;
import com.smxemail.rangeresolver.api.Resolved;
import com.smxemail.rangeresolver.lang.RangeResolverProcessor;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Mojo(name = "resolve-properties", defaultPhase = LifecyclePhase.INITIALIZE)
public class ResolvePropertiesMojo extends AbstractRangeResolverMojo {

  public void execute() throws MojoExecutionException {

    getLog().info("Added project/dependency resolution as session properties...");

    File deps = resolveDepsFile(mavenSession);
    if (deps.exists()) {

      try {
        String resolutionSource = getSourceReference(mavenSession);

        List<Resolved> resolvedProjects =
            mavenSession
                .getAllProjects()
                .stream()
                .map(
                    proj ->
                        Resolved.of(
                            "",
                            proj.getGroupId(),
                            proj.getArtifactId(),
                            proj.getVersion(),
                            "",
                            proj.getArtifactId() + ".version",
                            Range.fixed(project.getVersion())))
                .collect(Collectors.toList());

        RangeResolver rangeResolver = RangeResolverProcessor.parseRangeResolver(resolutionSource, new FileReader(deps));

        for (MavenProject mavenProject : mavenSession.getAllProjects()) {
          for (Resolved resolved : resolvedProjects) {
            mavenProject.getProperties().put(resolved.propertyName(), resolved.version());
          }
          for (Resolved resolved : rangeResolver.resolved()) {
            mavenProject.getProperties().put(resolved.propertyName(), resolved.version());
          }
          for (Resolved resolved : rangeResolver.locked()) {
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
