package com.smxemail.rangeresolver.mojo;

import com.google.common.base.MoreObjects;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.scm.ScmException;
import org.apache.maven.scm.ScmFileSet;
import org.apache.maven.scm.command.add.AddScmResult;
import org.apache.maven.scm.command.checkin.CheckInScmResult;
import org.apache.maven.scm.command.status.StatusScmResult;
import org.apache.maven.scm.manager.ScmManager;
import org.apache.maven.scm.provider.ScmProviderRepository;
import org.apache.maven.scm.repository.ScmRepository;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class AbstractRangeResolverMojo extends AbstractMojo {

  @Component ScmManager scmManager;

  @Component MavenProjectHelper projectHelper;

  @Parameter(required = true, readonly = true, property = "project")
  MavenProject project;

  @Parameter boolean pushChanges = false;

  @Parameter(defaultValue = "${session}", readonly = true)
  MavenSession mavenSession;

  @Parameter(required = true, defaultValue = "${project.build.outputDirectory}")
  File outputDirectory;

  protected File resolveDepsFile() {
    return resolveDepsFile(mavenSession);
  }

  public static File resolveDepsFile(MavenSession mavenSession) {
    File pomFile = mavenSession.getRequest().getPom();
    String pomBaseName = pomFile.getName().substring(0, pomFile.getName().lastIndexOf(".") + 1);
    return new File(pomBaseName + "deps").getAbsoluteFile();
  }

  protected void commitChangesToDependencies(String message, File file) throws MojoExecutionException {
    commitChangesToDependencies(message, file.getParentFile(), Collections.singleton(file));
  }

  protected void commitChangesToDependencies(String message, File baseDir, Set<File> files) throws MojoExecutionException {
    if (!projectIsReleasing()) {
      getLog().info("Project is not being released, skipping commit: " + message);
    } else {
      try {
        String connectionUrl = MoreObjects.firstNonNull(project.getScm().getConnection(), project.getScm().getDeveloperConnection());
        ScmRepository repository = scmManager.makeScmRepository(connectionUrl);
        ScmProviderRepository providerRepo = repository.getProviderRepository();
        providerRepo.setPushChanges(pushChanges);

        ScmFileSet scmFileSet = new ScmFileSet(baseDir, new ArrayList(files));
        StatusScmResult statusScmResult = scmManager.status(repository, scmFileSet);

        if (containsDepChange(statusScmResult, files)) {
          getLog().info(String.format("Committing dependency changes to %s", connectionUrl));
          AddScmResult addResult = scmManager.add(repository, scmFileSet);
          if (!addResult.isSuccess()) {
            throw new MojoExecutionException(String.format("Unable to add dependency changes to repository %s", connectionUrl));
          } else {
            CheckInScmResult checkinResult = scmManager.checkIn(repository, scmFileSet, message);
            if (!checkinResult.isSuccess()) {
              throw new MojoExecutionException(String.format("Unable to commit dependency changes to repository %s", connectionUrl));
            }
          }
        } else {
          getLog().info("No changes in dependencies - skipping commit.");
        }
      } catch (ScmException e) {
        throw new MojoExecutionException(e.getMessage(), e);
      }
    }
  }

  private boolean projectIsReleasing() {
    return new File(project.getBasedir(), "release.properties").exists();
  }

  private boolean containsDepChange(StatusScmResult statusScmResult, Set<File> files) {

    Set<String> fileNames = files.stream().map(f -> f.getName()).collect(Collectors.toSet());
    Set<String> scmPaths = statusScmResult.getChangedFiles().stream().map(f -> f.getPath()).collect(Collectors.toSet());

    return !Collections.disjoint(fileNames, scmPaths);
  }
}
