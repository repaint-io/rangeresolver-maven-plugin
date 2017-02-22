package com.smxemail.rangeresolver.mojo;

import com.smxemail.rangeresolver.api.RangeResolver;
import com.smxemail.rangeresolver.lang.RangeResolverProcessor;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import static com.smxemail.rangeresolver.lang.RepositoryResolver.unlockDependencies;
import static com.smxemail.rangeresolver.lang.RepositoryWriter.writeRangeResolveSource;

@Mojo(name = "unlock-deps", defaultPhase = LifecyclePhase.INITIALIZE, inheritByDefault = false)
public class UnlockRangeResolverMojo extends AbstractRangeResolverMojo {

  public void execute() throws MojoExecutionException {
    if (project.isExecutionRoot()) {
      File deps = resolveDepsFile();
      if (deps.exists()) {
        getLog().info("Unlocking resolved ranges...");
        try {
          String resolutionSource = gav(project);
          RangeResolver rangeResolver =
              RangeResolverProcessor.parseRangeResolver(resolutionSource, new FileReader(deps));

          RangeResolver updated = unlockDependencies(rangeResolver, unlockPatterns(rangeResolver));

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
