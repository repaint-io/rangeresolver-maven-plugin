package com.smxemail.rangeresolver.mojo;

import com.smxemail.rangeresolver.api.RangeResolver;
import com.smxemail.rangeresolver.api.ResolutionResult;
import com.smxemail.rangeresolver.api.Resolved;
import com.smxemail.rangeresolver.lang.RangeResolverProcessor;
import com.smxemail.rangeresolver.lang.RepositoryResolver;
import okio.Okio;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.jdom2.Comment;
import org.jdom2.Content;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.LineSeparator;
import org.jdom2.output.XMLOutputter;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.smxemail.rangeresolver.lang.RepositoryWriter.writeRangeResolveSource;

@Mojo(name = "resolve-deps", defaultPhase = LifecyclePhase.INITIALIZE, inheritByDefault = false)
public class RangeResolverMojo extends AbstractRangeResolverMojo {

  public static final Namespace NAMESPACE = Namespace.getNamespace("http://maven.apache.org/POM/4.0.0");

  public static final String DEPENDENCIES = "dependencies";

  public static final String GROUP_ID = "groupId";

  public static final String ARTIFACT_ID = "artifactId";

  public static final String VERSION = "version";

  public static final String DEPENDENCY_MANAGEMENT = "dependencyManagement";

  public static final String PARENT = "parent";

  public static final String PROFILES = "profiles";

  public static final String PROPERTIES = "properties";

  public static final String DEPENDENCIES_SHA1 = "pom.deps.sha1";

  public static final String PROFILE = "profile";

  public static final String EXCLUSIONS = "exclusions";

  public static final String EXCLUSION = "exclusion";

  public static final String RESOLVED_FROM = " resolved from ";

  public static final String SCOPE = "scope";

  private static final Pattern exclusionScopes = Pattern.compile("(compile|runtime)", Pattern.CASE_INSENSITIVE);

  public void execute() throws MojoExecutionException {
    if (project.isExecutionRoot()) {
      File deps = resolveDepsFile();
      if (deps.exists()) {
        getLog().info("Updating resolved ranges...");
        try {
          boolean updateSnapshots = mavenSession.getRequest().isUpdateSnapshots();
          String localRepoDir = mavenSession.getLocalRepository().getBasedir();
          String resolutionSource = gav(project);
          RangeResolver rangeResolver = RangeResolverProcessor.parseRangeResolver(resolutionSource, new FileReader(deps));
          RepositoryResolver repositoryResolver = RepositoryResolver.createRepositoryResolver(getLog()::info, getLog()::warn, localRepoDir, updateSnapshots);

          ResolutionResult resolvedThings = repositoryResolver.resolveRanges(rangeResolver, resolutionSource);

          Set<String> reactorKeys = mavenSession.getAllProjects().stream().map(this::key).collect(Collectors.toSet());
          Predicate<Resolved> exludeReactorProjects = r -> !reactorKeys.contains(r.key());

          List<Resolved> resolvedExcludingReactorProjects = resolvedThings.resolved().stream().filter(exludeReactorProjects).collect(Collectors.toList());

          RangeResolver updated = rangeResolver.withResolved(resolvedExcludingReactorProjects);

          writeRangeResolveSource(updated, new FileWriter(deps));

          String sha1 = Okio.buffer(Okio.source(deps)).readByteString().sha1().hex();

          File baseDir = deps.getAbsoluteFile().getParentFile();

          Set<File> pomFiles =
              Files.find(baseDir.toPath(), 10, (path, attr) -> path.toFile().getName().equals("pom.xml") && attr.isRegularFile())
                  .map(Path::toFile)
                  .collect(Collectors.toSet());

          for (File file : pomFiles) {
            rewritePom(updated, sha1, file);
          }

          Set<File> allFiles = new HashSet<>(pomFiles);
          allFiles.add(deps);

          commitChangesToDependencies("Updated dependency ranges.", baseDir, allFiles);

        } catch (Exception e) {
          throw new MojoExecutionException(e.getMessage(), e);
        }
      } else {
        getLog().warn("Skipping range resolution - deps file is missing.");
      }
    }
  }

  private void rewritePom(RangeResolver updated, String sha1, File pomFile) {

    try {
      Document pomDocument = new SAXBuilder().build(pomFile);

      Map<String, Resolved> resolvedItems = new HashMap<>();
      resolvedItems.putAll(updated.resolved().stream().collect(Resolved.collectByKey));
      resolvedItems.putAll(updated.locked().stream().collect(Resolved.collectByKey));

      Element projectElem = pomDocument.getRootElement();
      rewritePomDependencies(resolvedItems, projectElem);

      // Update sha1 of deps in pom
      Element properties = projectElem.getChild(PROPERTIES, NAMESPACE);
      if (properties != null) {
        Element sha1Element = properties.getChild(DEPENDENCIES_SHA1);
        if (sha1Element != null) {
          sha1Element.setText(sha1);
        } else {
          properties.addContent(new Element(DEPENDENCIES_SHA1, NAMESPACE).setText(sha1));
        }
      } else {
        properties = new Element(PROPERTIES, NAMESPACE);
        properties.addContent(new Element(DEPENDENCIES_SHA1, NAMESPACE).setText(sha1));
        projectElem.addContent(properties);
      }

      Element parent = projectElem.getChild(PARENT, NAMESPACE);
      if (parent != null) {
        Resolved resolved = resolvedItems.get(keyForMavenDependency(parent));
        Element version = parent.getChild(VERSION, NAMESPACE);
        if (resolved != null && version != null) {
          version.setText(String.format("%s", resolved.version()));
        }
      }

      Element profiles = projectElem.getChild(PROFILES, NAMESPACE);
      if (profiles != null) {
        List<Element> profileList = profiles.getChildren(PROFILE, NAMESPACE);
        for (Element profile : profileList) {
          rewriteDependencyManagement(resolvedItems, profile);
          rewritePomDependencies(resolvedItems, profile);
        }
      }

      rewriteDependencyManagement(resolvedItems, projectElem);

      File newPomFile = new File(pomFile.getAbsolutePath());
      File originalPomFile = new File(pomFile.getAbsolutePath() + ".orig");
      if (originalPomFile.exists()) {
        originalPomFile.delete();
      }
      pomFile.renameTo(originalPomFile);

      String relativePomFileName = newPomFile.getPath().substring(project.getBasedir().getPath().length() + 1);
      getLog().info("Rewriting dependencies in " + relativePomFileName);

      Format format = Format.getPrettyFormat().setLineSeparator(LineSeparator.SYSTEM);
      new XMLOutputter(format).output(pomDocument, new FileWriter(newPomFile));

    } catch (IOException | JDOMException e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }

  private void rewriteDependencyManagement(Map<String, Resolved> resolvedItems, Element projectElem) {
    Element dependencyManagement = projectElem.getChild(DEPENDENCY_MANAGEMENT, NAMESPACE);
    if (dependencyManagement != null) {
      rewritePomDependencies(resolvedItems, dependencyManagement);
    }
  }

  private void rewritePomDependencies(Map<String, Resolved> resolvedItems, Element projectElem) {

    // get keys for projects in the reactor so that we can skip re-resolving them
    Set<String> reactorKeys =
        mavenSession
            .getAllProjects()
            .stream()
            .map(project -> String.format(KEY_FORMAT, project.getGroupId(), project.getArtifactId()))
            .collect(Collectors.toSet());

    Element dependencies = projectElem.getChild(DEPENDENCIES, NAMESPACE);
    if (dependencies != null) {
      for (Element dependency : dependencies.getChildren()) {

        String depKey = keyForMavenDependency(dependency);
        if (!reactorKeys.contains(depKey) && resolvedItems.containsKey(depKey)) {
          Resolved resolved = resolvedItems.get(depKey);

          // Replace version
          Element version = dependency.getChild(VERSION, NAMESPACE);
          if (version != null) {
            version.setText(String.format("[%s]", resolved.version()));

            Element scope = dependency.getChild(SCOPE, NAMESPACE);

            if (scope == null || exclusionScopes.matcher(scope.getText()).matches()) {

              // Exclude transitive dependencies from compile time scoped items.
              dependency.removeChild(EXCLUSIONS, NAMESPACE);
              Element exclusions = new Element(EXCLUSIONS, NAMESPACE);
              Element exclusion = new Element(EXCLUSION, NAMESPACE);
              exclusion.addContent(new Element(GROUP_ID, NAMESPACE).setText(WILDCARD));
              exclusion.addContent(new Element(ARTIFACT_ID, NAMESPACE).setText(WILDCARD));
              exclusions.addContent(exclusion);
              dependency.addContent(exclusions);
            }

            // yank resolved comment
            // TODO No longer adding the comment, so eventually can remove the yank
            Comment comment = yankResolvedComment(dependency).orElse(new Comment(""));
            //            comment.setText(String.format("%s%s ", RESOLVED_FROM, resolved.resolvedFrom()));
            //            dependency.addContent(comment);
          }
        }
      }
    }
  }

  private Optional<Comment> yankResolvedComment(Element element) {
    List<Content> contents = element.getContent();
    for (Content content : contents) {
      if (content instanceof Comment) {
        Comment comment = (Comment) content;
        if (comment.getText().startsWith(RESOLVED_FROM)) {
          comment.detach();
          return Optional.of(comment);
        }
      }
    }
    return Optional.empty();
  }

  private String keyForMavenDependency(Element dependency) {
    return String.format(KEY_FORMAT, dependency.getChildText(GROUP_ID, NAMESPACE), dependency.getChildText(ARTIFACT_ID, NAMESPACE));
  }
}
