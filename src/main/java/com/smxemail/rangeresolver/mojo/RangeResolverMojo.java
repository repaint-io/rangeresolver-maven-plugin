package com.smxemail.rangeresolver.mojo;

import com.smxemail.rangeresolver.api.RangeResolver;
import com.smxemail.rangeresolver.api.ResolutionResult;
import com.smxemail.rangeresolver.api.Resolved;
import com.smxemail.rangeresolver.lang.RangeResolverProcessor;
import com.smxemail.rangeresolver.lang.RepositoryResolver;
import io.vavr.collection.List;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
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
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.smxemail.rangeresolver.lang.RepositoryWriter.writeRangeResolveSource;

@Mojo(name = "resolve-deps", defaultPhase = LifecyclePhase.INITIALIZE, inheritByDefault = false)
public class RangeResolverMojo extends AbstractRangeResolverMojo {

  public static final Namespace NAMESPACE =
      Namespace.getNamespace("http://maven.apache.org/POM/4.0.0");

  public static final String BUILD = "build";

  public static final String PLUGINS = "plugins";

  public static final String PLUGIN_MANAGEMENT = "pluginManagement";

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

  @Parameter(property = "filterUnusedDependencies")
  public boolean filterUnusedDependencies = false;

  private static final Pattern exclusionScopes =
      Pattern.compile("(compile|runtime)", Pattern.CASE_INSENSITIVE);

  @Override
  public void execute() throws MojoExecutionException {
    if (project.isExecutionRoot()) {
      File deps = resolveDepsFile();
      if (deps.exists()) {
        getLog().info("Updating resolved ranges...");
        try {
          boolean updateSnapshots = mavenSession.getRequest().isUpdateSnapshots();
          String localRepoDir = mavenSession.getLocalRepository().getBasedir();
          String resolutionSource = gav(project).replaceAll("-SNAPSHOT", "");
          RangeResolver rangeResolver =
              RangeResolverProcessor.parseRangeResolver(
                  resolutionSource, new FileReader(deps), allowUnlocked, allowSnapshots);

          RepositoryResolver repositoryResolver =
              RepositoryResolver.createRepositoryResolver(
                  getLog()::info,
                  getLog()::warn,
                  localRepoDir,
                  allowUnlocked,
                  allowSnapshots,
                  updateSnapshots);

          ResolutionResult resolvedThings =
              repositoryResolver.resolveRanges(rangeResolver, resolutionSource, resolutionSource);

          Predicate<Resolved> excludeReactorProjects = buildProjectKeysPredicate().negate();

          List<Resolved> resolvedExcludingReactorProjects =
              resolvedThings.resolved().filter(excludeReactorProjects);

          List<Resolved> lockedExcludingReactorProjects =
              resolvedThings.locked().filter(excludeReactorProjects);

          if (filterUnusedDependencies) {
            Predicate<Resolved> excludeUnusedDependencies = buildProjectDependenciesPredicate();

            resolvedExcludingReactorProjects =
                resolvedExcludingReactorProjects.filter(excludeUnusedDependencies);
            lockedExcludingReactorProjects =
                lockedExcludingReactorProjects.filter(excludeUnusedDependencies);
          }

          writeRangeResolveSource(
              rangeResolver
                  .withResolved(resolvedExcludingReactorProjects)
                  .withLocked(lockedExcludingReactorProjects),
              new FileWriter(deps));

          File baseDir = deps.getAbsoluteFile().getParentFile();

          Set<File> pomFiles = findPomFiles(baseDir);

          for (File file : pomFiles) {
            rewritePom(rangeResolver, file);
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

  private Set<File> findPomFiles(File baseDir) throws IOException {
    try (Stream<Path> stream =
        Files.find(
            baseDir.toPath(),
            10,
            (path, attr) -> path.toFile().getName().equals("pom.xml") && attr.isRegularFile())) {
      return stream.map(Path::toFile).collect(Collectors.toSet());
    }
  }

  private Predicate<Resolved> buildProjectDependenciesPredicate() {
    Set<String> usedDependencyKeys =
        mavenSession
            .getAllProjects()
            .stream()
            .flatMap(
                project -> {
                  List<String> keys = resolveDependencyKeys(List.ofAll(project.getDependencies()));
                  keys = keys.appendAll(resolvePluginKeys(List.ofAll(project.getBuildPlugins())));

                  DependencyManagement dependencyManagement = project.getDependencyManagement();
                  if (dependencyManagement != null) {
                    keys =
                        keys.appendAll(
                            resolveDependencyKeys(
                                List.ofAll(dependencyManagement.getDependencies())));
                  }

                  PluginManagement pluginManagement = project.getPluginManagement();
                  if (pluginManagement != null) {
                    keys =
                        keys.appendAll(
                            resolvePluginKeys(List.ofAll(pluginManagement.getPlugins())));
                  }

                  return keys.toJavaStream();
                })
            .collect(Collectors.toSet());

    return r -> usedDependencyKeys.contains(r.key());
  }

  private List<String> resolveDependencyKeys(List<Dependency> dependencies) {
    return dependencies.map(d -> keyFor(d.getGroupId(), d.getArtifactId()));
  }

  private List<String> resolvePluginKeys(List<Plugin> plugins) {
    return plugins.flatMap(
        plugin ->
            resolveDependencyKeys(List.ofAll(plugin.getDependencies()))
                .append(keyFor(plugin.getGroupId(), plugin.getArtifactId())));
  }

  private Predicate<Resolved> buildProjectKeysPredicate() {
    Set<String> reactorKeys =
        mavenSession.getAllProjects().stream().map(this::key).collect(Collectors.toSet());
    return r -> reactorKeys.contains(r.key());
  }

  private void rewritePom(RangeResolver updated, File pomFile) {

    try {
      Set<String> reactorKeys = resolveReactorKeys();

      Map<String, Resolved> resolvedItems = new HashMap<>();
      resolvedItems.putAll(updated.resolved().collect(Resolved.collectByKey));
      resolvedItems.putAll(updated.locked().collect(Resolved.collectByKey));

      Document pomDocument = new SAXBuilder().build(pomFile);
      Element projectElem = pomDocument.getRootElement();

      rewritePomBuild(resolvedItems, reactorKeys, projectElem);
      rewritePomDependencies(resolvedItems, reactorKeys, projectElem);

      // Remove the pom.deps.sha1 property
      Element properties = projectElem.getChild(PROPERTIES, NAMESPACE);
      if (properties != null) {
        Element sha1Element = properties.getChild(DEPENDENCIES_SHA1, NAMESPACE);
        if (sha1Element != null) {
          sha1Element.detach();
        }
      }

      Element parent = projectElem.getChild(PARENT, NAMESPACE);
      if (parent != null) {
        Resolved resolved = resolvedItems.get(keyFor(parent));
        Element version = parent.getChild(VERSION, NAMESPACE);
        if (resolved != null && version != null) {
          version.setText(String.format("%s", resolved.version()));
        }
      }

      Element profiles = projectElem.getChild(PROFILES, NAMESPACE);
      if (profiles != null) {
        List<Element> profileList = List.ofAll(profiles.getChildren(PROFILE, NAMESPACE));
        for (Element profile : profileList) {
          rewritePomBuild(resolvedItems, reactorKeys, profile);
          rewriteDependencyManagement(resolvedItems, reactorKeys, profile);
          rewritePomDependencies(resolvedItems, reactorKeys, profile);
        }
      }

      rewriteDependencyManagement(resolvedItems, reactorKeys, projectElem);

      File newPomFile = new File(pomFile.getAbsolutePath());
      File originalPomFile = new File(pomFile.getAbsolutePath() + ".orig");
      if (originalPomFile.exists()) {
        originalPomFile.delete();
      }
      pomFile.renameTo(originalPomFile);

      String relativePomFileName =
          newPomFile.getPath().substring(project.getBasedir().getPath().length() + 1);
      getLog().info("Rewriting dependencies in " + relativePomFileName);

      Format format =
          Format.getPrettyFormat()
              .setLineSeparator(LineSeparator.SYSTEM)
              .setExpandEmptyElements(false);

      new XMLOutputter(format).output(pomDocument, new FileWriter(newPomFile));

    } catch (IOException | JDOMException e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }

  private void rewritePomBuild(
      Map<String, Resolved> resolvedItems, Set<String> reactorKeys, Element projectElem) {
    Element build = projectElem.getChild(BUILD, NAMESPACE);
    if (build != null) {
      rewritePomPlugins(resolvedItems, reactorKeys, build);
      rewriteTiles(resolvedItems, build);
      Element pluginManagement = projectElem.getChild(PLUGIN_MANAGEMENT, NAMESPACE);
      if (pluginManagement != null) {
        rewritePomPlugins(resolvedItems, reactorKeys, pluginManagement);
      }
    }
  }

  private void rewritePomPlugins(
      Map<String, Resolved> resolvedItems, Set<String> reactorKeys, Element build) {
    Element plugins = build.getChild(PLUGINS, NAMESPACE);
    if (plugins != null) {
      for (Element plugin : plugins.getChildren()) {
        String pluginKey = keyFor(plugin);
        if (!reactorKeys.contains(pluginKey) && resolvedItems.containsKey(pluginKey)) {
          Resolved resolved = resolvedItems.get(pluginKey);

          // Replace version
          Element version = plugin.getChild(VERSION, NAMESPACE);
          if (version != null) {
            version.setText(String.format("%s", resolved.version()));
          }

          // Replace plugin dependencies
          rewritePomDependencies(resolvedItems, reactorKeys, plugin);
        }
      }
    }
  }

  private void rewriteTiles(Map<String, Resolved> resolvedItems, Element build) {
    Element plugins = build.getChild(PLUGINS, NAMESPACE);
    if (plugins != null) {
      for (Element plugin : plugins.getChildren()) {
        String pluginKey = keyFor(plugin);
        if ("io.repaint.maven:tiles-maven-plugin".equals(pluginKey)) {
          Element configuration = plugin.getChild("configuration", NAMESPACE);
          if (configuration != null) {
            Element tiles = configuration.getChild("tiles", NAMESPACE);
            if (tiles != null) {
              for (Element tile : tiles.getChildren("tile", NAMESPACE)) {
                String gav = tile.getText();
                String tileKey = gav.substring(0, gav.lastIndexOf(":"));
                Resolved resolved = resolvedItems.get(tileKey);
                if (resolved != null) {
                  tile.setText(String.format("%s:[%s]", tileKey, resolved.version()));
                }
              }
            }
          }
        }
      }
    }
  }

  private void rewriteDependencyManagement(
      Map<String, Resolved> resolvedItems, Set<String> reactorKeys, Element projectElem) {
    Element dependencyManagement = projectElem.getChild(DEPENDENCY_MANAGEMENT, NAMESPACE);
    if (dependencyManagement != null) {
      rewritePomDependencies(resolvedItems, reactorKeys, dependencyManagement);
    }
  }

  private void rewritePomDependencies(
      Map<String, Resolved> resolvedItems, Set<String> reactorKeys, Element projectElem) {

    Element dependencies = projectElem.getChild(DEPENDENCIES, NAMESPACE);
    if (dependencies != null) {
      for (Element dependency : dependencies.getChildren()) {

        String depKey = keyFor(dependency);
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
          }
        }
      }
    }
  }

  private Set<String> resolveReactorKeys() {
    // get keys for projects in the reactor so that we can skip re-resolving them
    return mavenSession
        .getAllProjects()
        .stream()
        .map(project -> keyFor(project.getGroupId(), project.getArtifactId()))
        .collect(Collectors.toSet());
  }

  private String keyFor(Element dependency) {
    return keyFor(
        dependency.getChildText(GROUP_ID, NAMESPACE),
        dependency.getChildText(ARTIFACT_ID, NAMESPACE));
  }

  private String keyFor(String groupId, String artifactId) {
    return String.format(KEY_FORMAT, groupId, artifactId);
  }
}
