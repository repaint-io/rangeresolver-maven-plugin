package com.smxemail.rangeresolver.mojo;

import com.smxemail.rangeresolver.api.RangeResolver;
import com.smxemail.rangeresolver.api.Resolved;
import com.smxemail.rangeresolver.lang.RangeResolverProcessor;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mojo(name = "verify", defaultPhase = LifecyclePhase.INITIALIZE)
public class VerifyDependenciesMojo extends AbstractRangeResolverMojo {

  @Parameter(property = "strictDependency")
  boolean strictDependency = true;

  public void execute() throws MojoExecutionException {

    File deps = resolveDepsFile(mavenSession);
    if (deps.exists()) {
      getLog().info("Verifying all dependencies are resolved from " + deps.getPath());

      try {
        String resolutionSource = getSourceReference(mavenSession);

        RangeResolver rangeResolver = RangeResolverProcessor.parseRangeResolver(resolutionSource, new FileReader(deps));

        Predicate<Dependency> isUndeclaredDependency = declaredDependency(rangeResolver).negate();

        MavenProject mavenProject = mavenSession.getCurrentProject();

        Set<Dependency> undeclaredDependencies = new TreeSet<>(Comparator.comparing(this::keyForDependency));
        DependencyManagement dependencyManagement = mavenProject.getDependencyManagement();
        if (dependencyManagement != null) {
          undeclaredDependencies.addAll(filterDependencies(isUndeclaredDependency, dependencyManagement.getDependencies()));
        }
        undeclaredDependencies.addAll(filterDependencies(isUndeclaredDependency, mavenProject.getDependencies()));

        if (!undeclaredDependencies.isEmpty()) {
          getLog().error(String.format("Undeclared dependencies found in %s", gav(mavenProject)));
          for (Dependency undeclaredDependency : undeclaredDependencies) {
            getLog().error(String.format("Undeclared dependency: %s", gav(undeclaredDependency)));
          }
          if (strictDependency) {
            throw new MojoExecutionException("Undeclared dependencies in " + deps.getPath());
          }
        }

      } catch (IOException e) {
        throw new MojoExecutionException(e.getMessage(), e);
      }
    } else {
      if (strictDependency) {
        throw new MojoExecutionException(String.format("%s file missing.", deps.getPath()));
      } else {
        getLog().warn("Skipping property resolution - deps file is missing.");
      }
    }
  }

  private Set<Dependency> filterDependencies(Predicate<Dependency> isDeclaredDependency, List<Dependency> dependencies) {
    return dependencies.stream().filter(isDeclaredDependency).collect(Collectors.toSet());
  }

  private Predicate<Dependency> declaredDependency(RangeResolver rangeResolver) {
    Set<String> keys = Stream.concat(rangeResolver.resolved().stream(), rangeResolver.locked().stream()).map(Resolved::key).collect(Collectors.toSet());
    return dependency -> keys.contains(keyForDependency(dependency));
  }

  private String keyForDependency(Dependency dependency) {
    return String.format("%s:%s", dependency.getGroupId(), dependency.getArtifactId());
  }
}
