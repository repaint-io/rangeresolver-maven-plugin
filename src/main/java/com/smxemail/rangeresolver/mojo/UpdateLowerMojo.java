package com.smxemail.rangeresolver.mojo;

import io.vavr.collection.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;

import com.smxemail.rangeresolver.api.RangeResolver;
import com.smxemail.rangeresolver.api.ResolutionResult;
import com.smxemail.rangeresolver.api.Resolve;
import com.smxemail.rangeresolver.api.Resolved;
import com.smxemail.rangeresolver.lang.RangeResolverProcessor;
import com.smxemail.rangeresolver.lang.RepositoryResolver;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Map;

import static com.smxemail.rangeresolver.lang.RepositoryWriter.writeRangeResolveSource;

@Mojo(name = "update-lower-bounds", defaultPhase = LifecyclePhase.INITIALIZE, inheritByDefault = false)
public class UpdateLowerMojo extends AbstractRangeResolverMojo {
  @Component
  private RepositorySystem repositorySystem;

  @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
  RepositorySystemSession repositorySystemSession;

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
              RangeResolverProcessor.parseRangeResolver(resolutionSource, new FileReader(deps), allowUnlocked, allowSnapshots);
          RepositoryResolver repositoryResolver = RepositoryResolver.createRepositoryResolver(
              repositorySystem,
              repositorySystemSession,
              getLog()::info,
              getLog()::warn,
              allowUnlocked,
              allowSnapshots,
              updateSnapshots);

          ResolutionResult resolutionResult = repositoryResolver.resolveRanges(rangeResolver, resolutionSource, resolutionSource);

          Map<String, Resolved> resolvedThings = resolutionResult.resolved().collect(Resolved.collectByKey);

          List<Resolve> updatedLowerBoundResolves = rangeResolver.resolves().map(res -> resolveLowerBound(resolvedThings, res));

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
      throw new IllegalArgumentException(String.format("Unable to resolve: %s - %s", res.key(), e.getMessage()), e);
    }
  }
}
