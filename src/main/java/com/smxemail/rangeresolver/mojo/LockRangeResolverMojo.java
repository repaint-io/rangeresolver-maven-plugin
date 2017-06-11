package com.smxemail.rangeresolver.mojo;

import com.smxemail.rangeresolver.api.RangeResolver;
import com.smxemail.rangeresolver.api.Resolved;
import com.smxemail.rangeresolver.lang.RangeResolverProcessor;
import com.smxemail.rangeresolver.lang.RepositoryResolver;
import javaslang.collection.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import static com.smxemail.rangeresolver.lang.RepositoryWriter.writeRangeResolveSource;
import static javaslang.API.List;

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
          RangeResolver rangeResolver =
              RangeResolverProcessor.parseRangeResolver(resolutionSource, new FileReader(deps));

          List<Resolved> allResolved = rangeResolver.locked().appendAll(rangeResolver.resolved());

          RangeResolver updated =
              rangeResolver
                  .withResolved(List())
                  .withLocked(RepositoryResolver.sortResolvedThings(allResolved));

          writeRangeResolveSource(updated, new FileWriter(deps));
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
