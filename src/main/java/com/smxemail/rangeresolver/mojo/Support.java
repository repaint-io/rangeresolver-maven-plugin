package com.smxemail.rangeresolver.mojo;

import com.smxemail.rangeresolver.api.Resolved;
import org.apache.maven.execution.MavenSession;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class Support {

  public static File resolveDepsFile(MavenSession session) {
    File pomFile = session.getRequest().getPom();
    String pomBaseName = pomFile.getName().substring(0, pomFile.getName().lastIndexOf(".") + 1);
    return new File(pomBaseName + "deps");
  }

  public static Map<String, Resolved> mapResolvedThingsByKey(List<Resolved> resolvedThings) {
    Collector<Resolved, ?, Map<String, Resolved>> collector = Collectors.toMap(Resolved::key, Function.identity());
    return resolvedThings.stream().collect(collector);
  }
}
