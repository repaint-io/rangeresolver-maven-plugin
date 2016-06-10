package com.smxemail.rangeresolver.mojo;

import com.smxemail.rangeresolver.lang.RepositoryResolver;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import java.io.File;

@Mojo(name = "attach-deps", defaultPhase = LifecyclePhase.VERIFY, inheritByDefault = false)
public class AttachResolverMojo extends AbstractRangeResolverMojo {
  public void execute() throws MojoExecutionException {
    if (project.isExecutionRoot()) {
      File deps = resolveDepsFile();
      if (deps.exists()) {
        getLog().info("Attaching deps as bill-of-materials...");
        projectHelper.attachArtifact(project, RepositoryResolver.DEPS, RepositoryResolver.BILL_OF_MATERIALS, deps);
      } else {
        getLog().warn("Skipping bill-of-materials - deps file is missing.");
      }
    }
  }
}
