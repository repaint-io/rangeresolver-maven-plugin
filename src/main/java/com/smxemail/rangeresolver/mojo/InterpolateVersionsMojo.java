package com.smxemail.rangeresolver.mojo;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.shared.filtering.MavenFileFilter;
import org.apache.maven.shared.filtering.MavenFilteringException;

import java.io.File;
import java.util.Collections;

@Mojo(name = "interpolate-deps", defaultPhase = LifecyclePhase.VERIFY)
public class InterpolateVersionsMojo extends AbstractRangeResolverMojo {

  @Component MavenFileFilter mavenFileFilter;

  public void execute() throws MojoExecutionException {
    try {
      getLog().info("Attaching interpolated pom...");
      // interpolate properties and replace published pom
      outputDirectory.mkdirs();
      File resolvedPom = new File(outputDirectory, "interpolated-pom.xml");
      mavenFileFilter.copyFile(project.getFile(), resolvedPom, true, project, Collections.emptyList(), true, "UTF-8", mavenSession);
      project.setFile(resolvedPom);
    } catch (MavenFilteringException e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }
  }
}
