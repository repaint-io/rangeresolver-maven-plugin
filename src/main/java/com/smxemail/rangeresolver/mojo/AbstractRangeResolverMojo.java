package com.smxemail.rangeresolver.mojo;

import com.google.common.base.MoreObjects;
import com.smxemail.rangeresolver.api.Resolved;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.scm.ScmException;
import org.apache.maven.scm.ScmFile;
import org.apache.maven.scm.ScmFileSet;
import org.apache.maven.scm.command.add.AddScmResult;
import org.apache.maven.scm.command.checkin.CheckInScmResult;
import org.apache.maven.scm.command.status.StatusScmResult;
import org.apache.maven.scm.manager.ScmManager;
import org.apache.maven.scm.provider.ScmProviderRepository;
import org.apache.maven.scm.repository.ScmRepository;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public abstract class AbstractRangeResolverMojo extends AbstractMojo {

  @Component ScmManager scmManager;

  @Component MavenProjectHelper projectHelper;

  @Parameter(required = true, readonly = true, property = "project")
  MavenProject project;

  @Parameter boolean pushChanges = false;

  @Parameter(defaultValue = "${session}", readonly = true)
  MavenSession mavenSession;

  protected File resolveDepsFile() {
    return resolveDepsFile(mavenSession);
  }

  public static File resolveDepsFile(MavenSession mavenSession) {
    File pomFile = mavenSession.getRequest().getPom();
    String pomBaseName = pomFile.getName().substring(0, pomFile.getName().lastIndexOf(".") + 1);
    return new File(pomBaseName + "deps");
  }

  protected Map<String, Resolved> mapResolvedThingsByKey(List<Resolved> resolvedThings) {
    Collector<Resolved, ?, Map<String, Resolved>> collector = Collectors.toMap(Resolved::key, Function.identity());
    return resolvedThings.stream().collect(collector);
  }

  protected void commitChangesToDependencies(File deps, String message) throws MojoExecutionException {
    if (!projectIsReleasing()) {
      getLog().info("Project is not being released, skipping commit: " + message);
    } else {
      try {
        String connectionUrl = MoreObjects.firstNonNull(project.getScm().getConnection(), project.getScm().getDeveloperConnection());
        ScmRepository repository = scmManager.makeScmRepository(connectionUrl);
        ScmProviderRepository providerRepo = repository.getProviderRepository();
        providerRepo.setPushChanges(pushChanges);

        File baseDir = deps.getAbsoluteFile().getParentFile();
        ScmFileSet scmFileSet = new ScmFileSet(baseDir, deps.getName());
        StatusScmResult statusScmResult = scmManager.status(repository, scmFileSet);

        if (containsDepChange(statusScmResult, deps)) {

          getLog().info(String.format("Committing deps changes to %s", connectionUrl));
          AddScmResult addResult = scmManager.add(repository, scmFileSet);
          if (!addResult.isSuccess()) {
            throw new MojoExecutionException(String.format("Unable to add pom.deps to repository %s", connectionUrl));
          } else {
            CheckInScmResult checkinResult = scmManager.checkIn(repository, scmFileSet, message);
            if (!checkinResult.isSuccess()) {
              throw new MojoExecutionException(String.format("Unable to commit pom.deps changes to repository %s", connectionUrl));
            }
          }
        } else {
          getLog().info("No changes in dependencies - skipping commit.");
        }
      } catch (IOException | ScmException e) {
        throw new MojoExecutionException(e.getMessage(), e);
      }
    }
  }

  private boolean projectIsReleasing() {
    return new File(project.getBasedir(), "release.properties").exists();
  }

  private boolean containsDepChange(StatusScmResult statusScmResult, File deps) {

    for (ScmFile scmFile : statusScmResult.getChangedFiles()) {
      if (scmFile.getPath().contains(deps.getName())) {
        return true;
      }
    }
    return false;
  }
}
