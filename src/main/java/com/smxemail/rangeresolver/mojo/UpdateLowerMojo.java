package com.smxemail.rangeresolver.mojo;

import com.smxemail.rangeresolver.api.RangeResolver;
import com.smxemail.rangeresolver.api.ResolutionResult;
import com.smxemail.rangeresolver.api.Resolve;
import com.smxemail.rangeresolver.api.Resolved;
import com.smxemail.rangeresolver.lang.RangeResolverProcessor;
import com.smxemail.rangeresolver.lang.RepositoryResolver;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.smxemail.rangeresolver.lang.RepositoryWriter.writeRangeResolveSource;

@Mojo(name = "update-lower-bounds", defaultPhase = LifecyclePhase.INITIALIZE, inheritByDefault = false)
public class UpdateLowerMojo extends AbstractRangeResolverMojo {

  public void execute() throws MojoExecutionException {
    if (project.isExecutionRoot()) {
      File deps = resolveDepsFile();
      if (deps.exists()) {
        getLog().info("Updating lower bounds...");
        try {
          boolean updateSnapshots = mavenSession.getRequest().isUpdateSnapshots();
          String localRepoDir = mavenSession.getLocalRepository().getBasedir();
          String resolutionSource = project.getGroupId() + ":" + project.getArtifactId() + ":" + project.getVersion();
          RangeResolver rangeResolver = RangeResolverProcessor.parseRangeResolver(resolutionSource, new FileReader(deps));
          RepositoryResolver repositoryResolver = RepositoryResolver.createRepositoryResolver(getLog()::info, getLog()::warn, localRepoDir, updateSnapshots);

          ResolutionResult resolutionResult = repositoryResolver.resolveRanges(rangeResolver, deps.getPath());

          Map<String, Resolved> resolvedThings = resolutionResult.resolved().stream().collect(Resolved.collectByKey);

          List<Resolve> updatedLowerBounds = rangeResolver.resolves().stream().map(res -> resolveLowerBound(resolvedThings, res)).collect(Collectors.toList());

          RangeResolver updated = rangeResolver.withResolves(updatedLowerBounds);
          writeRangeResolveSource(updated, new FileWriter(deps));
          commitChangesToDependencies(deps, "Updated lower bounds of ranges.");
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
      return updateLowerBound(res, resolvedThings.get(res.key()).version());
    } else {
      return res;
    }
  }

  private Resolve updateLowerBound(Resolve res, String version) {
    return res.withRange(res.range().withLower(version));
  }
}
