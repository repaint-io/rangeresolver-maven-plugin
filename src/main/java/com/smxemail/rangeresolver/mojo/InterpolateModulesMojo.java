package com.smxemail.rangeresolver.mojo;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.shared.filtering.MavenFileFilter;
import org.apache.maven.shared.filtering.MavenFilteringException;

import java.io.File;
import java.util.Collections;
import java.util.regex.Pattern;

@Mojo(name = "interpolate-modules", requiresDirectInvocation = true)
public class InterpolateModulesMojo extends AbstractRangeResolverMojo {

  private static final Pattern IGNORED_DIRS = Pattern.compile("(\\.git|target|src)$");

  @Component MavenFileFilter mavenFileFilter;

  public void execute() throws MojoExecutionException {
    String rootPath = project.getFile().getParentFile().getPath();
    interpolateIntellijModuleFiles(rootPath, new File(rootPath));
  }

  private void interpolateIntellijModuleFiles(String root, File path) throws MojoExecutionException {
    if (path.isDirectory()) {
      File[] moduleFiles = path.listFiles(pathname -> pathname.getName().endsWith(".iml"));
      for (File moduleFile : moduleFiles) {
        interpolateIdeaModuleFile(root, moduleFile);
      }

      File[] allFiles = path.listFiles();
      for (File file : allFiles) {
        if (file.isDirectory() && !IGNORED_DIRS.matcher(file.getName()).matches()) {
          interpolateIntellijModuleFiles(root, file);
        }
      }
    }
  }

  /**
   * Interpolate an IntelliJ .iml module file with property replacements.
   *
   * @param root project root, for generating relative paths
   * @param moduleFile An IntelliJ .iml file
   */
  private void interpolateIdeaModuleFile(String root, File moduleFile) {
    String relativeModuleFileName = moduleFile.getPath().substring(root.length() + 1);
    getLog().info(String.format("Interpolating IntelliJ Module: %s", relativeModuleFileName));
    File originalImlFile = new File(moduleFile.getAbsolutePath() + ".orig");

    // if original .iml file doesn't exists, rename the existing one for re-interpolation
    if (!originalImlFile.exists()) {
      moduleFile.renameTo(originalImlFile);
    }

    try {
      mavenFileFilter.copyFile(originalImlFile, moduleFile, true, project, Collections.emptyList(), true, "UTF-8", mavenSession);
    } catch (MavenFilteringException e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }
}
