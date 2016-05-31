package com.smxemail.rangeresolver.mojo;

import com.smxemail.rangeresolver.api.RangeResolver;
import com.smxemail.rangeresolver.api.Resolved;
import com.smxemail.rangeresolver.lang.RangeResolverProcessor;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

import static com.smxemail.rangeresolver.lang.RepositoryWriter.writeRangeResolveSource;

@Mojo(name = "unlock-deps", defaultPhase = LifecyclePhase.INITIALIZE, inheritByDefault = false)
public class UnlockRangeResolverMojo extends AbstractRangeResolverMojo {

  public void execute() throws MojoExecutionException {
    if (project.isExecutionRoot()) {
      File deps = resolveDepsFile();
      if (deps.exists()) {
        getLog().info("Unlocking resolved ranges...");
        try {
          String resolutionSource = project.getGroupId() + ":" + project.getArtifactId() + ":" + project.getVersion();
          RangeResolver rangeResolver = RangeResolverProcessor.parseRangeResolver(resolutionSource, new FileReader(deps));

          List<Resolved> allResolved = new ArrayList<>();
          allResolved.addAll(rangeResolver.locked());
          allResolved.addAll(rangeResolver.resolved());

          RangeResolver updated = rangeResolver.withResolved(allResolved).withLocked();

          writeRangeResolveSource(updated, new FileWriter(deps));
          commitChangesToDependencies(deps, "Unlocked dependency ranges.");
        } catch (Exception e) {
          throw new MojoExecutionException(e.getMessage(), e);
        }
      } else {
        getLog().warn("Skipping unlocking of ranges - deps file is missing.");
      }
    }
  }
}
