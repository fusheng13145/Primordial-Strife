package com.strife.conventions;

import com.diffplug.gradle.spotless.SpotlessExtension;
import com.diffplug.gradle.spotless.SpotlessPlugin;
import java.nio.file.Path;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.tasks.TaskProvider;

/**
 * Shared conventions for all strife modules: formatting (docs/02 §5) and the
 * module import-boundary check (docs/03 §2).
 */
public class ConventionsPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.setVersion(project.property("mod_version").toString());
        project.setGroup(project.property("mod_group").toString());
        project.getRepositories().mavenCentral();

        project.getPlugins().apply(SpotlessPlugin.class);
        project.getPlugins().withType(SpotlessPlugin.class, p -> {
            SpotlessExtension spotless = project.getExtensions().getByType(SpotlessExtension.class);
            spotless.java(java -> {
                java.target("src/**/*.java");
                java.googleJavaFormat(project.property("googlejavaformat_version").toString())
                        .aosp();
            });
            spotless.format("gradle", f -> {
                f.target("**/*.gradle", "**/gradle.properties");
                f.trimTrailingWhitespace();
                f.endWithNewline();
            });
        });

        TaskProvider<DefaultTask> checkImports =
                project.getTasks().register("checkImports", DefaultTask.class, task -> {
                    task.setGroup("verification");
                    task.setDescription("Enforces docs/03 §2 package dependency rules on com.strife.* sources.");
                    task.doLast((Task t) -> {
                        // No short-circuit on a missing src/main/java: checkSourceTree distinguishes
                        // "module has no code" from "code-free module grew code" (docs/02 §3).
                        Path srcRoot = project.getProjectDir().toPath().resolve("src/main/java");
                        List<String> violations =
                                ModuleDependencyRules.checkSourceTree(project.getName(), srcRoot);
                        violations.forEach(v -> t.getLogger().error("dependency rule violation: {}", v));
                        if (!violations.isEmpty()) {
                            throw new IllegalStateException(
                                    violations.size() + " module dependency violation(s), see docs/03 §2");
                        }
                    });
                });
        project.getPlugins()
                .withType(
                        BasePlugin.class,
                        p -> project.getTasks().named("check", t -> t.dependsOn(checkImports)));
    }
}
