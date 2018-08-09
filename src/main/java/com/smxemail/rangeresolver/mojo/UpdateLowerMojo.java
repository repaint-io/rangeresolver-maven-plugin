package com.smxemail.rangeresolver.mojo;

import static com.smxemail.rangeresolver.lang.RepositoryWriter.writeRangeResolveSource;

import com.smxemail.rangeresolver.api.RangeResolver;
import com.smxemail.rangeresolver.api.ResolutionResult;
import com.smxemail.rangeresolver.api.Resolve;
import com.smxemail.rangeresolver.api.Resolved;
import com.smxemail.rangeresolver.lang.RangeResolverProcessor;
import com.smxemail.rangeresolver.lang.RepositoryResolver;
import io.vavr.collection.List;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Map;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(
    name = "update-lower-bounds",
    defaultPhase = LifecyclePhase.INITIALIZE,
    inheritByDefault = false)
public class UpdateLowerMojo extends AbstractRangeResolverMojo {

  @Override
  public void execute() throws MojoExecutionException {
    if (project.isExecutionRoot()) {
      File deps = resolveDepsFile();
      if (deps.exists()) {
        getLog().info("Updating lower bounds...");
        try {
          boolean updateSnapshots = mavenSession.getRequest().isUpdateSnapshots();
          String localRepoDir = mavenSession.getLocalRepository().getBasedir();
          String resolutionSource = gav(project).replaceAll("-SNAPSHOT", "");
          RangeResolver rangeResolver =
              RangeResolverProcessor.parseRangeResolver(
                  resolutionSource, new FileReader(deps), allowUnlocked, allowSnapshots);
          RepositoryResolver repositoryResolver =
              RepositoryResolver.createRepositoryResolver(
                  getLog()::info,
                  getLog()::warn,
                  localRepoDir,
                  allowUnlocked,
                  allowSnapshots,
                  updateSnapshots);

          ResolutionResult resolutionResult =
              repositoryResolver.resolveRanges(rangeResolver, resolutionSource, resolutionSource);

          Map<String, Resolved> resolvedThings =
              resolutionResult.resolved().collect(Resolved.collectByKey);

          List<Resolve> updatedLowerBoundResolves =
              rangeResolver.resolves().map(res -> resolveLowerBound(resolvedThings, res));

          RangeResolver updated = rangeResolver.withResolves(updatedLowerBoundResolves);

          writeRangeResolveSource(updated, new FileWriter(deps));
          commitChangesToDependencies("Updated lower bounds of ranges.", deps);
        } catch (Exception e) {
          throw new MojoExecutionException(e.getMessage(), e);
        }
      } else {
        getLog().warn("Skipping lower bound range updating - deps file is missing.");
      }
    }
  }

  private Resolve resolveLowerBound(Map<String, Resolved> resolvedThings, Resolve res) {
    if (resolvedThings.containsKey(res.key())) {
      return updateLowerBound(res, resolvedThings.get(res.key()).constrainedRange().upper());
    } else {
      return res;
    }
  }

  private Resolve updateLowerBound(Resolve res, String version) {
    try {
      return res.withRange(res.range().withLower(version));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          String.format("Unable to resolve: %s - %s", res.key(), e.getMessage()), e);
    }
  }
}
