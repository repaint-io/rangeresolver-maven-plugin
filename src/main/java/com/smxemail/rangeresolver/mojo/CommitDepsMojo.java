package com.smxemail.rangeresolver.mojo;

import com.google.common.base.MoreObjects;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.scm.ScmException;
import org.apache.maven.scm.ScmFileSet;
import org.apache.maven.scm.command.add.AddScmResult;
import org.apache.maven.scm.command.checkin.CheckInScmResult;
import org.apache.maven.scm.repository.ScmRepository;

import java.io.File;
import java.io.IOException;

@Mojo(name = "commit-deps", defaultPhase = LifecyclePhase.INITIALIZE, inheritByDefault = false)
public class CommitDepsMojo extends AbstractRangeResolverMojo {

  public void execute() throws MojoExecutionException {
    if (project.isExecutionRoot()) {
      File deps = resolveDepsFile();
      if (deps.exists()) {
        addDepsFileToWorkingSet(deps);
      } else {
        getLog().warn("Skipping bill-of-materials commit - deps file is missing.");
      }
    }
  }

  private void addDepsFileToWorkingSet(File deps) throws MojoExecutionException {
    try {
      String connectionUrl = MoreObjects.firstNonNull(project.getScm().getConnection(), project.getScm().getDeveloperConnection());
      ScmRepository repository = scmManager.makeScmRepository(connectionUrl);
      File baseDir = deps.getAbsoluteFile().getParentFile();
      ScmFileSet scmFileSet = new ScmFileSet(baseDir, deps.getName());

      if (!scmManager.status(repository, scmFileSet).getChangedFiles().isEmpty()) {

        getLog().info(String.format("Committing deps changes to %s", connectionUrl));
        AddScmResult addResult = scmManager.add(repository, scmFileSet);
        if (!addResult.isSuccess()) {
          throw new MojoExecutionException(String.format("Unable to add pom.deps to repository %s", connectionUrl));
        } else {
          CheckInScmResult checkinResult = scmManager.checkIn(repository, scmFileSet, "Updated dependency ranges.");
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
