package com.smxemail.rangeresolver.mojo;

import com.smxemail.rangeresolver.api.RangeResolver;
import com.smxemail.rangeresolver.api.Resolved;
import com.smxemail.rangeresolver.lang.RangeResolverProcessor;
import com.smxemail.rangeresolver.lang.RepositoryResolver;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.List;

import static com.smxemail.rangeresolver.lang.RepositoryWriter.writeRangeResolveSource;
import static com.smxemail.rangeresolver.mojo.Support.resolveDepsFile;

@Mojo(name = "resolve-deps", defaultPhase = LifecyclePhase.INITIALIZE, inheritByDefault = false)
public class RangeResolverMojo extends AbstractMojo {

  @Parameter(required = true, readonly = true, property = "project")
  protected MavenProject project;

  @Component MavenSession mavenSession;

  public void execute() throws MojoExecutionException {

    if (project.isExecutionRoot()) {
      File deps = resolveDepsFile(mavenSession);
      if (deps.exists()) {
        getLog().info("Updating resolving ranges...");
        try {
          boolean updateSnapshots = mavenSession.getRequest().isUpdateSnapshots();
          String localRepoDir = mavenSession.getLocalRepository().getBasedir();
          String resolutionSource = project.getGroupId() + ":" + project.getArtifactId() + ":" + project.getVersion();

          RangeResolver rangeResolver = RangeResolverProcessor.parseRangeResolver(resolutionSource, new FileReader(deps));
          RepositoryResolver repositoryResolver = RepositoryResolver.createRepositoryResolver(getLog()::info, getLog()::warn, localRepoDir, updateSnapshots);

          List<Resolved> resolvedThings = repositoryResolver.resolveRanges(rangeResolver, resolutionSource);
          RangeResolver updated = rangeResolver.withResolved(resolvedThings);

          writeRangeResolveSource(updated, new FileWriter(deps));
        } catch (Exception e) {
          throw new MojoExecutionException(e.getMessage(), e);
        }
      } else {
        getLog().warn("Skipping range resolution - deps file is missing.");
      }
    }
  }
}
