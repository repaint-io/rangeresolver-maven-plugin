package com.smxemail.rangeresolver.mojo;

import com.smxemail.rangeresolver.api.RangeResolver;
import com.smxemail.rangeresolver.api.ResolutionResult;
import com.smxemail.rangeresolver.api.Resolved;
import com.smxemail.rangeresolver.lang.RangeResolverProcessor;
import com.smxemail.rangeresolver.lang.RepositoryResolver;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import static com.smxemail.rangeresolver.mojo.AbstractRangeResolverMojo.resolveDepsFile;

/**
 * @deprecated use the {@link ResolvePropertiesMojo} in future.
 */
@Component(role = AbstractMavenLifecycleParticipant.class, hint = "RangeResolverLifecycleParticipant")
@Deprecated
public class RangeResolverLifecycleParticipant extends AbstractMavenLifecycleParticipant {

  @Requirement Logger logger;

  @Override
  public void afterSessionStart(MavenSession session) throws MavenExecutionException {

    String localRepoDir = session.getLocalRepository().getBasedir();
    File deps = resolveDepsFile(session);

    logger.info("Resolving ranges in the lifecycle...");

    try {
      boolean updateSnapshots = session.getRequest().isUpdateSnapshots();

      String resolutionSource = getSourceReference(session);

      RepositoryResolver repositoryResolver = RepositoryResolver.createRepositoryResolver(logger::info, logger::warn, localRepoDir, updateSnapshots);

      RangeResolver rangeResolver = RangeResolverProcessor.parseRangeResolver(resolutionSource, new FileReader(deps));

      if (rangeResolver.resolved().isEmpty() && rangeResolver.locked().isEmpty()) {
        logger.info("No resolution found - performing in-place resolve...");

        ResolutionResult resolvedThings = repositoryResolver.resolveRanges(rangeResolver, deps.getPath());

        for (Resolved thing : resolvedThings.resolved()) {
          session.getUserProperties().setProperty(thing.propertyName(), thing.version());
        }
      }

      for (Resolved resolved : rangeResolver.resolved()) {
        session.getUserProperties().setProperty(resolved.propertyName(), resolved.version());
      }
      for (Resolved resolved : rangeResolver.locked()) {
        session.getUserProperties().setProperty(resolved.propertyName(), resolved.version());
      }

      super.afterSessionStart(session);

    } catch (Exception e) {
      throw new MavenExecutionException(e.getMessage(), e);
    }
  }

  private String getSourceReference(MavenSession session) throws ParserConfigurationException, SAXException, IOException {
    File pomFile = session.getRequest().getPom();
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    DocumentBuilder db = dbf.newDocumentBuilder();
    Document doc = db.parse(pomFile);
    String groupId = doc.getElementsByTagName("groupId").item(0).getTextContent();
    String artifactId = doc.getElementsByTagName("artifactId").item(0).getTextContent();
    String version = doc.getElementsByTagName("version").item(0).getTextContent();
    return String.format("%s:%s:%s", groupId, artifactId, version);
  }
}
