package com.smxemail.rangeresolver.mojo;

import com.google.common.base.MoreObjects;

import io.vavr.collection.List;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
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
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.smxemail.rangeresolver.api.Resolved;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public abstract class AbstractRangeResolverMojo extends AbstractMojo {
    public static final String GAV_FORMAT = "%s:%s:%s";

    public static final String WILDCARD = "*";

    public static final String KEY_FORMAT = "%s:%s";

    @Component
    ScmManager scmManager;

    @Component
    MavenProjectHelper projectHelper;

    @Parameter(required = true, readonly = true, property = "project")
    MavenProject project;

    @Parameter
    boolean commitChanges = false;

    @Parameter
    boolean pushChanges = false;

    @Parameter(property = "allowUnlocked")
    String allowUnlocked = "";

    @Parameter(property = "allowSnapshots")
    String allowSnapshots = "";

    @Parameter(defaultValue = "${session}", readonly = true)
    MavenSession mavenSession;

    @Parameter(required = true, defaultValue = "${project.build.outputDirectory}")
    File outputDirectory;

    @Parameter(property = "filterUnusedDependencies")
    public boolean filterUnusedDependencies = false;

    @Parameter(property = "allowDeprecatedDependencies")
    public boolean allowDeprecatedDependencies = true;

    @Parameter(property = "allowBlacklistedDependencies")
    public boolean allowBlacklistedDependencies = true;

    protected File resolveDepsFile() {
        return resolveDepsFile(mavenSession);
    }

    public static File resolveDepsFile(MavenSession mavenSession) {
        File pomFile = mavenSession.getTopLevelProject().getFile().getAbsoluteFile();
        String pomBaseName = pomFile.getName().substring(0, pomFile.getName().lastIndexOf(".") + 1);
        return new File(pomBaseName + "deps").getAbsoluteFile();
    }

    protected void commitChangesToDependencies(String message, File file) throws MojoExecutionException {
        commitChangesToDependencies(message, file.getParentFile(), Collections.singleton(file));
    }

    protected void commitChangesToDependencies(String message, File baseDir, Set<File> files)
            throws MojoExecutionException {
        if (!projectDepsCommitable()) {
            getLog().info("Project is not being released, or commits are disabled, skipping commit: " + message);
        } else {
            try {
                String connectionUrl = MoreObjects.firstNonNull(
                        project.getScm().getConnection(), project.getScm().getDeveloperConnection());
                ScmRepository repository = scmManager.makeScmRepository(connectionUrl);
                ScmProviderRepository providerRepo = repository.getProviderRepository();
                providerRepo.setPushChanges(pushChanges);

                ScmFileSet scmFileSet = new ScmFileSet(baseDir, new ArrayList(files));
                StatusScmResult statusScmResult = scmManager.status(repository, scmFileSet);

                if (containsDepChange(statusScmResult, files)) {
                    getLog().info(String.format("Committing dependency changes to %s", connectionUrl));
                    AddScmResult addResult = scmManager.add(repository, scmFileSet);
                    if (!addResult.isSuccess()) {
                        throw new MojoExecutionException(
                                String.format("Unable to add dependency changes to repository %s", connectionUrl));
                    } else {
                        CheckInScmResult checkinResult = scmManager.checkIn(repository, scmFileSet, message);
                        if (!checkinResult.isSuccess()) {
                            throw new MojoExecutionException(String.format(
                                    "Unable to commit dependency changes to repository %s", connectionUrl));
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

    private boolean projectDepsCommitable() {
        return commitChanges && projectIsReleasing();
    }

    private boolean projectIsReleasing() {
        return new File(project.getBasedir(), "release.properties").exists();
    }

    protected void throwBlacklistedException(List<Resolved> blacklistedExcludingReactorProjects)
            throws MojoExecutionException {
        throw new MojoExecutionException(String.format(
                "Blacklisted dependencies found: %s - add <allowBlacklistedDependencies>true</allowBlacklistedDependencies> to your pom.xml's "
                        + "properties to bypass.",
                blacklistedExcludingReactorProjects.mkString(", ")));
    }

    protected void throwDeprecationException(List<Resolved> deprecatedExcludingReactorProjects)
            throws MojoExecutionException {
        throw new MojoExecutionException(String.format(
                "Disallowed deprecated dependencies found: %s - add <allowDeprecatedDependencies>true</allowDeprecatedDependencies> to your pom.xml's "
                        + "properties to bypass.",
                deprecatedExcludingReactorProjects.mkString(", ")));
    }

    private boolean containsDepChange(StatusScmResult statusScmResult, Set<File> files) {
        Set<String> fileNames = files.stream().map(f -> f.getName()).collect(Collectors.toSet());
        Set<String> scmPaths =
                statusScmResult.getChangedFiles().stream().map(f -> f.getPath()).collect(Collectors.toSet());

        return !Collections.disjoint(fileNames, scmPaths);
    }

    protected String gav(Dependency undeclaredDependency) {
        return String.format(
                GAV_FORMAT,
                undeclaredDependency.getGroupId(),
                undeclaredDependency.getArtifactId(),
                undeclaredDependency.getVersion());
    }

    protected String gav(MavenProject mavenProject) {
        return String.format(
                GAV_FORMAT, mavenProject.getGroupId(), mavenProject.getArtifactId(), mavenProject.getVersion());
    }

    protected String key(MavenProject mavenProject) {
        return String.format(KEY_FORMAT, mavenProject.getGroupId(), mavenProject.getArtifactId());
    }

    protected String getSourceReference(MavenSession session) {
        try {
            File pomFile = session.getTopLevelProject().getFile();
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(pomFile);
            String groupId = doc.getElementsByTagName("groupId").item(0).getTextContent();
            String artifactId = doc.getElementsByTagName("artifactId").item(0).getTextContent();
            String version = doc.getElementsByTagName("version").item(0).getTextContent();
            return String.format(GAV_FORMAT, groupId, artifactId, version);
        } catch (SAXException | ParserConfigurationException | IOException e) {
            return "";
        }
    }
}
