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

@Mojo(name = "lock-deps", defaultPhase = LifecyclePhase.INITIALIZE, inheritByDefault = false)
public class LockRangeResolverMojo extends AbstractRangeResolverMojo {

  public void execute() throws MojoExecutionException {
    if (project.isExecutionRoot()) {
      File deps = resolveDepsFile();
      if (deps.exists()) {
        try {
          getLog().info("Locking resolved ranges...");
          String resolutionSource = project.getGroupId() + ":" + project.getArtifactId() + ":" + project.getVersion();
          RangeResolver rangeResolver = RangeResolverProcessor.parseRangeResolver(resolutionSource, new FileReader(deps));

          List<Resolved> allResolved = new ArrayList<>();
          allResolved.addAll(rangeResolver.locked());
          allResolved.addAll(rangeResolver.resolved());
          RangeResolver updated = rangeResolver.withResolved().withLocked(allResolved);

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
