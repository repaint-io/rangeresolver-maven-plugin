package com.smxemail.rangeresolver.mojo;

import com.smxemail.rangeresolver.api.Resolved;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.scm.manager.ScmManager;

import java.io.File;
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
}
