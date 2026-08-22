package com.smxemail.rangeresolver.mojo;

import io.vavr.collection.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import com.smxemail.rangeresolver.api.RangeResolver;
import com.smxemail.rangeresolver.api.Resolved;

import java.io.File;
import java.nio.file.Files;

import static io.vavr.API.List;

import static com.smxemail.rangeresolver.lang.RangeResolverProcessor.parseRangeResolver;
import static com.smxemail.rangeresolver.lang.RepositoryResolver.sortResolvedThings;
import static com.smxemail.rangeresolver.lang.RepositoryWriter.writeRangeResolveSource;

import static java.nio.charset.StandardCharsets.UTF_8;

@Mojo(name = "lock-deps", defaultPhase = LifecyclePhase.INITIALIZE, inheritByDefault = false)
public class LockRangeResolverMojo extends AbstractRangeResolverMojo {
  @Override
  public void execute() throws MojoExecutionException {
    if (project.isExecutionRoot()) {
      File deps = resolveDepsFile();
      if (deps.exists()) {
        try {
          getLog().info("Locking resolved ranges...");
          String resolutionSource = gav(project).replaceAll("-SNAPSHOT", "");
          RangeResolver rangeResolver = parseRangeResolver(
              resolutionSource,
              Files.newBufferedReader(deps.toPath(), UTF_8),
              allowUnlocked,
              allowSnapshots);

          List<Resolved> allResolved = rangeResolver.locked().appendAll(rangeResolver.resolved());

          RangeResolver updated =
              rangeResolver.withResolved(List()).withLocked(sortResolvedThings(allResolved));

          writeRangeResolveSource(updated, Files.newBufferedWriter(deps.toPath(), UTF_8));
          commitChangesToDependencies("Locked dependency ranges.", deps);
        } catch (Exception e) {
          throw new MojoExecutionException(e.getMessage(), e);
        }
      } else {
        getLog().warn("Skipping locking of ranges - deps file is missing.");
      }
    }
  }
}
