package io.repaint.rangeresolver.mojo;

import dev.zazr.collection.List;

import io.repaint.rangeresolver.api.RangeResolver;
import io.repaint.rangeresolver.api.ResolutionResult;
import io.repaint.rangeresolver.api.Resolve;
import io.repaint.rangeresolver.api.Resolved;
import io.repaint.rangeresolver.lang.RepositoryResolver;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;

import static io.repaint.rangeresolver.lang.RangeResolverProcessor.parseRangeResolver;
import static io.repaint.rangeresolver.lang.RepositoryResolver.createRepositoryResolver;
import static io.repaint.rangeresolver.lang.RepositoryWriter.writeRangeResolveSource;

import static java.nio.charset.StandardCharsets.UTF_8;

@Mojo(name = "update-lower-bounds", defaultPhase = LifecyclePhase.INITIALIZE, inheritByDefault = false)
public class UpdateLowerMojo extends AbstractRangeResolverMojo {
    @Component
    private RepositorySystem repositorySystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    RepositorySystemSession repositorySystemSession;

    @Override
    public void execute() throws MojoExecutionException {
        if (project.isExecutionRoot()) {
            File deps = resolveDepsFile();
            if (deps.exists()) {
                getLog().info("Updating lower bounds...");
                try {
                    boolean updateSnapshots = mavenSession.getRequest().isUpdateSnapshots();
                    String resolutionSource = gav(project).replaceAll("-SNAPSHOT", "");
                    RangeResolver rangeResolver = parseRangeResolver(
                            resolutionSource,
                            Files.newBufferedReader(deps.toPath(), UTF_8),
                            allowUnlocked,
                            allowSnapshots);
                    RepositoryResolver repositoryResolver = withCooldownOverride(createRepositoryResolver(
                            repositorySystem,
                            repositorySystemSession,
                            getLog()::info,
                            getLog()::warn,
                            allowUnlocked,
                            allowSnapshots,
                            updateSnapshots));

                    ResolutionResult resolutionResult =
                            repositoryResolver.resolveRanges(rangeResolver, resolutionSource, resolutionSource);

                    Map<String, Resolved> resolvedThings =
                            resolutionResult.resolved().collect(Resolved.collectByKey);

                    List<Resolve> updatedLowerBoundResolves =
                            rangeResolver.resolves().map(res -> resolveLowerBound(resolvedThings, res));

                    RangeResolver updated = rangeResolver.withResolves(updatedLowerBoundResolves);

                    writeRangeResolveSource(updated, Files.newBufferedWriter(deps.toPath(), UTF_8));
                    commitChangesToDependencies("Updated lower bounds of ranges.", deps);
                } catch (Exception e) {
                    throw new MojoExecutionException(e.getMessage(), e);
                }
            } else {
                getLog().warn("Skipping lower bound range updating - deps file is missing.");
            }
        }
    }

    private Resolve resolveLowerBound(Map<String, Resolved> resolvedThings, Resolve res) {
        if (resolvedThings.containsKey(res.key())) {
            return updateLowerBound(
                    res, resolvedThings.get(res.key()).constrainedRange().upper());
        } else {
            return res;
        }
    }

    private Resolve updateLowerBound(Resolve res, String version) {
        try {
            return res.withRange(res.range().withLower(version));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unable to resolve: %s - %s".formatted(res.key(), e.getMessage()), e);
        }
    }
}
