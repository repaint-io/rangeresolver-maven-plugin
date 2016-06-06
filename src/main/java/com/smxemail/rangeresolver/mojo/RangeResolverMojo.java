package com.smxemail.rangeresolver.mojo;

import com.smxemail.rangeresolver.api.RangeResolver;
import com.smxemail.rangeresolver.api.ResolutionResult;
import com.smxemail.rangeresolver.lang.RangeResolverProcessor;
import com.smxemail.rangeresolver.lang.RepositoryResolver;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import static com.smxemail.rangeresolver.lang.RepositoryWriter.writeRangeResolveSource;

@Mojo(name = "resolve-deps", defaultPhase = LifecyclePhase.INITIALIZE, inheritByDefault = false)
public class RangeResolverMojo extends AbstractRangeResolverMojo {

  public void execute() throws MojoExecutionException {

    if (project.isExecutionRoot()) {
      File deps = resolveDepsFile();
      if (deps.exists()) {
        getLog().info("Updating resolving ranges...");
        try {
          boolean updateSnapshots = mavenSession.getRequest().isUpdateSnapshots();
          String localRepoDir = mavenSession.getLocalRepository().getBasedir();
          String resolutionSource = project.getGroupId() + ":" + project.getArtifactId() + ":" + project.getVersion();

          RangeResolver rangeResolver = RangeResolverProcessor.parseRangeResolver(resolutionSource, new FileReader(deps));
          RepositoryResolver repositoryResolver = RepositoryResolver.createRepositoryResolver(getLog()::info, getLog()::warn, localRepoDir, updateSnapshots);

          ResolutionResult resolvedThings = repositoryResolver.resolveRanges(rangeResolver, resolutionSource);
          RangeResolver updated = rangeResolver.withResolved(resolvedThings.resolved());

          writeRangeResolveSource(updated, new FileWriter(deps));
          commitChangesToDependencies(deps, "Updated dependency ranges.");
        } catch (Exception e) {
          throw new MojoExecutionException(e.getMessage(), e);
        }
      } else {
        getLog().warn("Skipping range resolution - deps file is missing.");
      }
    }
  }
}
