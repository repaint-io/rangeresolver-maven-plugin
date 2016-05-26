package com.smxemail.rangeresolver.mojo;

import com.smxemail.rangeresolver.lang.RepositoryResolver;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

import java.io.File;

import static com.smxemail.rangeresolver.mojo.Support.resolveDepsFile;

@Mojo(name = "attach-deps", defaultPhase = LifecyclePhase.INITIALIZE, inheritByDefault = false)
public class AttachResolverMojo extends AbstractMojo {

  @Parameter(required = true, readonly = true, property = "project")
  protected MavenProject project;

  @Component MavenProjectHelper projectHelper;

  @Parameter(defaultValue = "${session}", readonly = true)
  protected MavenSession mavenSession;

  public void execute() throws MojoExecutionException {
    if (project.isExecutionRoot()) {
      File deps = resolveDepsFile(mavenSession);
      if (deps.exists()) {
        getLog().info("Attaching deps as bill-of-materials...");
        projectHelper.attachArtifact(project, RepositoryResolver.DEPS, RepositoryResolver.BILL_OF_MATERIALS, deps);
      } else {
        getLog().warn("Skipping bill-of-materials - deps file is missing.");
      }
    }
  }
}
