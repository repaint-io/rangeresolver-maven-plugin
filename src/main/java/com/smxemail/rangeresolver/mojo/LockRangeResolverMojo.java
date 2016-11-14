package com.smxemail.rangeresolver.mojo;

import com.smxemail.rangeresolver.api.Allow;
import com.smxemail.rangeresolver.api.Allowable;
import com.smxemail.rangeresolver.api.RangeResolver;
import com.smxemail.rangeresolver.api.Resolved;
import com.smxemail.rangeresolver.lang.RangeResolverProcessor;
import com.smxemail.rangeresolver.lang.RepositoryResolver;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.smxemail.rangeresolver.lang.RepositoryWriter.writeRangeResolveSource;

@Mojo(name = "lock-deps", defaultPhase = LifecyclePhase.INITIALIZE, inheritByDefault = false)
public class LockRangeResolverMojo extends AbstractRangeResolverMojo {

  public void execute() throws MojoExecutionException {
    if (project.isExecutionRoot()) {
      File deps = resolveDepsFile();
      if (deps.exists()) {
        try {
          getLog().info("Locking resolved ranges...");
          String resolutionSource = gav(project);
          RangeResolver rangeResolver = RangeResolverProcessor.parseRangeResolver(resolutionSource, new FileReader(deps));
          List<Pattern> unlocked =
              rangeResolver.allows().stream().filter(allow -> allow.allowable() == Allowable.UNLOCKED).map(Allow::pattern).collect(Collectors.toList());

          List<Resolved> allResolved = new ArrayList<>();
          allResolved.addAll(rangeResolver.locked());
          allResolved.addAll(rangeResolver.resolved());
          RangeResolver updated = rangeResolver.withResolved().withLocked(RepositoryResolver.sortResolvedThings(allResolved));

          updated = RepositoryResolver.unlockDependencies(updated, unlocked);

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
