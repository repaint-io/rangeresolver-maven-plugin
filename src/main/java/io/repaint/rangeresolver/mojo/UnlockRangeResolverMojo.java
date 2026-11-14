package io.repaint.rangeresolver.mojo;

import dev.zazr.collection.List;

import io.repaint.rangeresolver.api.RangeResolver;
import io.repaint.rangeresolver.api.Resolved;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import java.io.File;
import java.nio.file.Files;

import static io.repaint.rangeresolver.lang.RangeResolverProcessor.parseRangeResolver;
import static io.repaint.rangeresolver.lang.RepositoryResolver.sortResolvedThings;
import static io.repaint.rangeresolver.lang.RepositoryWriter.writeRangeResolveSource;

import static java.nio.charset.StandardCharsets.UTF_8;

@Mojo(name = "unlock-deps", defaultPhase = LifecyclePhase.INITIALIZE, inheritByDefault = false)
public class UnlockRangeResolverMojo extends AbstractRangeResolverMojo {
    @Override
    public void execute() throws MojoExecutionException {
        if (project.isExecutionRoot()) {
            File deps = resolveDepsFile();
            if (deps.exists()) {
                try {
                    getLog().info("Unlocking resolved ranges...");
                    String resolutionSource = gav(project).replaceAll("-SNAPSHOT", "");
                    RangeResolver rangeResolver = parseRangeResolver(
                            resolutionSource,
                            Files.newBufferedReader(deps.toPath(), UTF_8),
                            allowUnlocked,
                            allowSnapshots);

                    List<Resolved> allResolved = rangeResolver.locked().appendAll(rangeResolver.resolved());

                    RangeResolver updated = rangeResolver
                            .withResolved(sortResolvedThings(allResolved))
                            .withLocked(List.empty());

                    writeRangeResolveSource(updated, Files.newBufferedWriter(deps.toPath(), UTF_8));
                    commitChangesToDependencies("Unlocked dependency ranges.", deps);
                } catch (Exception e) {
                    throw new MojoExecutionException(e.getMessage(), e);
                }
            } else {
                getLog().warn("Skipping unlocking of ranges - deps file is missing.");
            }
        }
    }
}
