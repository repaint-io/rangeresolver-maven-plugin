package com.smxemail.rangeresolver.mojo;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import com.smxemail.rangeresolver.api.RangeResolver;
import com.smxemail.rangeresolver.api.Resolved;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.smxemail.rangeresolver.lang.RangeResolverProcessor.parseRangeResolver;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

@Mojo(name = "verify", defaultPhase = LifecyclePhase.INITIALIZE)
public class VerifyDependenciesMojo extends AbstractRangeResolverMojo {
  @Parameter(property = "strictDependency", defaultValue = "true")
  boolean strictDependency = true;

  @Override
  public void execute() throws MojoExecutionException {
    MavenProject currentProject = mavenSession.getCurrentProject();
    File deps = resolveDepsFile();
    if (deps.exists()) {
      try {
        String resolutionSource = getSourceReference(mavenSession);

        RangeResolver rangeResolver = parseRangeResolver(
            resolutionSource, Files.newBufferedReader(deps.toPath(), UTF_8), allowUnlocked, allowSnapshots);
        Predicate<Dependency> isUndeclared =
            declaredDependency(rangeResolver).negate();
        Set<Dependency> undeclared = getDependencies(currentProject, isUndeclared);

        processVerification(deps, currentProject, undeclared);

      } catch (IOException e) {
        throw new MojoExecutionException(e.getMessage(), e);
      }
    } else {
      processVerification(deps, currentProject, getDependencies(currentProject, dep -> true));
    }
  }

  private Set<Dependency> getDependencies(MavenProject mavenProject, Predicate<Dependency> isUndeclared) {
    Set<Dependency> undeclared = new TreeSet<>(Comparator.comparing(this::keyForDependency));
    DependencyManagement dependencyManagement = mavenProject.getDependencyManagement();
    if (dependencyManagement != null) {
      undeclared.addAll(filterDependencies(isUndeclared, dependencyManagement.getDependencies()));
    }
    undeclared.addAll(filterDependencies(isUndeclared, mavenProject.getDependencies()));
    return undeclared;
  }

  private void processVerification(File deps, MavenProject mavenProject, Set<Dependency> undeclared)
      throws MojoExecutionException {
    if (!undeclared.isEmpty()) {
      getLog().error(String.format("Undeclared dependencies found in %s", gav(mavenProject)));
      for (Dependency dependency : undeclared) {
        getLog().error(String.format("Undeclared dependency: %s", gav(dependency)));
      }

      getLog().warn(String.format(
          "Add the following script to the file %s:\n\n%s\n", deps.getPath(), generateSource(undeclared)));

      if (strictDependency) {
        throw new MojoExecutionException("Undeclared dependencies in " + deps.getPath());
      }
    }
  }

  private Set<Dependency> filterDependencies(
      Predicate<Dependency> isDeclaredDependency, List<Dependency> dependencies) {
    return dependencies.stream().filter(isDeclaredDependency).collect(Collectors.toSet());
  }

  private Predicate<Dependency> declaredDependency(RangeResolver rangeResolver) {
    Set<String> keys = rangeResolver
        .resolved()
        .appendAll(rangeResolver.locked())
        .map(Resolved::key)
        .collect(Collectors.toSet());
    return dependency -> keys.contains(keyForDependency(dependency));
  }

  private String keyForDependency(Dependency dependency) {
    return String.format("%s:%s", dependency.getGroupId(), dependency.getArtifactId());
  }

  private String generateSource(Set<Dependency> dependencies) {
    StringBuilder sb = new StringBuilder();
    for (Dependency dependency : dependencies) {
      sb.append(String.format(
          "     resolve highest %s:%s;\n",
          keyForDependency(dependency), expandVersionToUpperRange(dependency.getVersion())));
    }
    return sb.toString();
  }

  static String expandVersionToUpperRange(String version) {
    if (requireNonNull(version, "Version MUST not be null").matches("^[\\[\\(].*$")) {
      return version;
    } else {
      ArtifactVersion artifactVersion = new DefaultArtifactVersion(version);
      String nextMajorVersionNumber = artifactVersion.getMajorVersion() + 1 + ".0.0";
      return String.format("[%s,%s)", version, nextMajorVersionNumber);
    }
  }
}
