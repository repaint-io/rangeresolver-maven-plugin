package com.smxemail.rangeresolver.mojo;

import com.smxemail.rangeresolver.api.RangeResolver;
import com.smxemail.rangeresolver.api.Resolved;
import com.smxemail.rangeresolver.lang.RangeResolverProcessor;
import okio.Okio;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
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

import static java.util.Objects.requireNonNull;

@Mojo(name = "verify", defaultPhase = LifecyclePhase.INITIALIZE)
public class VerifyDependenciesMojo extends AbstractRangeResolverMojo {

  @Parameter(property = "strictDependency", defaultValue = "true")
  boolean strictDependency = true;

  public void execute() throws MojoExecutionException {
    MavenProject currentProject = mavenSession.getCurrentProject();
    File deps = resolveDepsFile();
    if (deps.exists()) {

      verifySha1(deps);

      try {
        String resolutionSource = getSourceReference(mavenSession);

        RangeResolver rangeResolver =
            RangeResolverProcessor.parseRangeResolver(resolutionSource, new FileReader(deps));
        Predicate<Dependency> isUndeclaredDependency = declaredDependency(rangeResolver).negate();
        Set<Dependency> undeclaredDependencies =
            getDependencies(currentProject, isUndeclaredDependency);

        processVerification(deps, currentProject, undeclaredDependencies);

      } catch (IOException e) {
        throw new MojoExecutionException(e.getMessage(), e);
      }
    } else {
      processVerification(deps, currentProject, getDependencies(currentProject, dep -> true));
    }
  }

  private void verifySha1(File deps) throws MojoExecutionException {
    try {
      String sha1 = Okio.buffer(Okio.source(deps)).readByteString().sha1().hex();
      if (!sha1.equals(project.getProperties().get(RangeResolverMojo.DEPENDENCIES_SHA1))) {
        getLog()
            .warn(
                "pom.deps sha1 is out of date, you may wish to rerun the rangeresolve:resolve-deps goal!");
      }
    } catch (IOException e) {
      throw new MojoExecutionException(e.getMessage());
    }
  }

  private Set<Dependency> getDependencies(
      MavenProject mavenProject, Predicate<Dependency> isUndeclaredDependency) {
    Set<Dependency> undeclaredDependencies =
        new TreeSet<>(Comparator.comparing(this::keyForDependency));
    DependencyManagement dependencyManagement = mavenProject.getDependencyManagement();
    if (dependencyManagement != null) {
      undeclaredDependencies.addAll(
          filterDependencies(isUndeclaredDependency, dependencyManagement.getDependencies()));
    }
    undeclaredDependencies.addAll(
        filterDependencies(isUndeclaredDependency, mavenProject.getDependencies()));
    return undeclaredDependencies;
  }

  private void processVerification(
      File deps, MavenProject mavenProject, Set<Dependency> undeclaredDependencies)
      throws MojoExecutionException {
    if (!undeclaredDependencies.isEmpty()) {
      getLog().error(String.format("Undeclared dependencies found in %s", gav(mavenProject)));
      for (Dependency undeclaredDependency : undeclaredDependencies) {
        getLog().error(String.format("Undeclared dependency: %s", gav(undeclaredDependency)));
      }

      getLog()
          .warn(
              String.format(
                  "Add the following script to the file %s:\n\n%s\n",
                  deps.getPath(), generateResolveSourceForDepenendencies(undeclaredDependencies)));

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
    Set<String> keys =
        Stream.concat(rangeResolver.resolved().stream(), rangeResolver.locked().stream())
            .map(Resolved::key)
            .collect(Collectors.toSet());
    return dependency -> keys.contains(keyForDependency(dependency));
  }

  private String keyForDependency(Dependency dependency) {
    return String.format("%s:%s", dependency.getGroupId(), dependency.getArtifactId());
  }

  private String generateResolveSourceForDepenendencies(Set<Dependency> dependencies) {
    StringBuilder sb = new StringBuilder();
    for (Dependency dependency : dependencies) {
      sb.append(
          String.format(
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
