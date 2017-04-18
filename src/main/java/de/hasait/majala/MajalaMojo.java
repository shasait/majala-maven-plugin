/*
 * Copyright (C) 2017 by Sebastian Hasait (sebastian at hasait dot de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.hasait.majala;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.base.Strings;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResult;

/**
 *
 */
@Mojo(name = "majala", requiresProject = false, threadSafe = true)
public class MajalaMojo extends AbstractMojo {

	private static void assertNonNull(Object object, String message) {
		if (object == null) {
			throw new IllegalArgumentException(message);
		}
	}

	@Parameter(property = "majala.java", defaultValue = "java")
	private String java;
	@Parameter(property = "majala.coords")
	private String mainGAV;
	@Parameter(property = "majala.groupId")
	private String mainGroupId;
	@Parameter(property = "majala.artifactId")
	private String mainArtifactId;
	@Parameter(property = "majala.extension", defaultValue = "jar")
	private String mainExtension;
	@Parameter(property = "majala.version")
	private String mainVersion;
	@Parameter(property = "majala.mainClass", required = true)
	private String mainClass;
	@Parameter(property = "majala.arg1")
	private String arg1;
	@Parameter(property = "majala.arg2")
	private String arg2;
	@Parameter(property = "majala.arg3")
	private String arg3;
	@Parameter(property = "majala.arg4")
	private String arg4;
	@Parameter(property = "majala.arg5")
	private String arg5;
	@Parameter(property = "majala.args")
	private String args;
	@Component
	private RepositorySystem repoSystem;
	@Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
	private RepositorySystemSession repoSession;

	public void execute() throws MojoExecutionException, MojoFailureException {
		final Artifact artifact;
		if (mainGAV != null) {
			artifact = new DefaultArtifact(mainGAV);
		} else {
			final String message = "Specify coords OR groupId, artifactId, extension, version";
			assertNonNull(mainGroupId, message);
			assertNonNull(mainArtifactId, message);
			assertNonNull(mainExtension, message);
			assertNonNull(mainVersion, message);
			artifact = new DefaultArtifact(mainGroupId, mainArtifactId, mainExtension, mainVersion);
		}
		assertNonNull(mainClass, "mainClass");

		final CollectRequest collectRequest = new CollectRequest();
		Dependency rootNode = new Dependency(artifact, "runtime");
		collectRequest.setRoot(rootNode);

		final CollectResult collectResult;
		try {
			collectResult = repoSystem.collectDependencies(repoSession, collectRequest);
		} catch (final Exception e) {
			throw new MojoExecutionException("Collecting dependencies failed", e);
		}

		getLog().info("CollectResult...");
		collectResult.getRoot().accept(new DependencyVisitor() {
			private int depth = 0;

			public boolean visitEnter(final DependencyNode node) {
				depth++;
				getLog().info(Strings.repeat("-", depth) + " " + node.getArtifact() + " ");
				return true;
			}

			public boolean visitLeave(final DependencyNode node) {
				depth--;
				return true;
			}
		});

		final DependencyRequest dependencyRequest = new DependencyRequest();
		dependencyRequest.setRoot(collectResult.getRoot());

		final DependencyResult dependencyResult;
		try {
			dependencyResult = repoSystem.resolveDependencies(repoSession, dependencyRequest);
		} catch (final Exception pE) {
			throw new MojoExecutionException("Resolving dependencies failed", pE);
		}

		getLog().info("DependencyResult...");
		final Set<File> dependencyFiles = new HashSet<File>();
		for (final ArtifactResult artifactResult : dependencyResult.getArtifactResults()) {
			final File file = artifactResult.getArtifact().getFile();
			dependencyFiles.add(file);
			getLog().info("- " + file);
		}

		final List<String> commandParts = new ArrayList<String>();
		commandParts.add(java);
		commandParts.add("-cp");

		final StringBuilder classPathBuilder = new StringBuilder();
		boolean firstClassPathEntry = true;
		for (final File file : dependencyFiles) {
			if (firstClassPathEntry) {
				firstClassPathEntry = false;
			} else {
				classPathBuilder.append(File.pathSeparatorChar);
			}
			classPathBuilder.append(file.getAbsolutePath());
		}
		commandParts.add(classPathBuilder.toString());

		commandParts.add(mainClass);

		if (arg1 != null) {
			commandParts.add(arg1);
		}
		if (arg2 != null) {
			commandParts.add(arg2);
		}
		if (arg3 != null) {
			commandParts.add(arg3);
		}
		if (arg4 != null) {
			commandParts.add(arg4);
		}
		if (arg5 != null) {
			commandParts.add(arg5);
		}
		if (args != null) {
			final String[] argsArray = args.split(" ");
			for (final String arg : argsArray) {
				commandParts.add(arg);
			}
		}

		final ProcessBuilder pb = new ProcessBuilder(commandParts);
		pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
		pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
		pb.redirectError(ProcessBuilder.Redirect.INHERIT);

		final Process process;
		try {
			process = pb.start();
		} catch (final Exception e) {
			throw new MojoExecutionException("Launching java failed", e);
		}

		getLog().info("Java launched");

		try {
			process.waitFor();
		} catch (InterruptedException e) {
			// ignore
			getLog().warn("Interrupted while waiting for java");
		}
	}

}
