package com.smxemail.rangeresolver.mojo;

import io.vavr.collection.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import com.smxemail.rangeresolver.api.RangeResolver;
import com.smxemail.rangeresolver.api.Resolved;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import static io.vavr.API.List;

import static com.smxemail.rangeresolver.lang.RangeResolverProcessor.parseRangeResolver;
import static com.smxemail.rangeresolver.lang.RepositoryResolver.sortResolvedThings;
import static com.smxemail.rangeresolver.lang.RepositoryWriter.writeRangeResolveSource;

@Mojo(name = "unlock-deps", defaultPhase = LifecyclePhase.INITIALIZE, inheritByDefault = false)
public class UnlockRangeResolverMojo extends AbstractRangeResolverMojo {
  @Override
  public void execute() throws MojoExecutionException {
    if (project.isExecutionRoot()) {
      File deps = resolveDepsFile();
      if (deps.exists()) {
        try {
          getLog().info("Unlocking resolved ranges...");
          String resolutionSource = gav(project).replaceAll("-SNAPSHOT", "");
          RangeResolver rangeResolver =
              parseRangeResolver(resolutionSource, new FileReader(deps), allowUnlocked, allowSnapshots);

          List<Resolved> allResolved = rangeResolver.locked().appendAll(rangeResolver.resolved());

          RangeResolver updated = rangeResolver
              .withResolved(sortResolvedThings(allResolved))
              .withLocked(List());

          writeRangeResolveSource(updated, new FileWriter(deps));
          commitChangesToDependencies("Unlocked dependency ranges.", deps);
        } catch (Exception e) {
          throw new MojoExecutionException(e.getMessage(), e);
        }
      } else {
        getLog().warn("Skipping unlocking of ranges - deps file is missing.");
      }
    }
  }
}
