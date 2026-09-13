/*
 * MCreator (https://mcreator.net/)
 * Copyright (C) 2012-2020, Pylo
 * Copyright (C) 2020-2026, Pylo, opensource contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.mcreator.wingman.util;

import net.mcreator.io.OS;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Locates executables on this device the way a shell would, so it can be checked whether a tool is installed
 * before trying to start it.
 */
public final class ExecutableFinder {

	private static final List<String> WINDOWS_EXTENSIONS = List.of(".exe", ".cmd", ".bat", ".com");

	// Common locations of user-installed tools that are often missing from PATH of applications started from a launcher
	private static final List<String> EXTRA_UNIX_DIRECTORIES = List.of("/usr/local/bin", "/opt/homebrew/bin",
			System.getProperty("user.home") + "/.local/bin");

	// PATH of this process with a few common tool locations added
	private static List<Path> searchPath() {
		List<Path> directories = new ArrayList<>();
		String path = System.getenv("PATH");
		if (path != null) {
			for (String entry : path.split(File.pathSeparator)) {
				if (!entry.isBlank())
					toPath(entry).ifPresent(directories::add);
			}
		}
		if (OS.getOS() != OS.WINDOWS) {
			for (String extra : EXTRA_UNIX_DIRECTORIES) {
				Path directory = Path.of(extra);
				if (!directories.contains(directory) && Files.isDirectory(directory))
					directories.add(directory);
			}
		}
		return directories;
	}

	public static String searchPathVariable() {
		return searchPath().stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator));
	}

	/**
	 * Finds an executable by name in the search path, or checks the given path directly. On Windows, the usual
	 * extensions of executables and scripts are tried too.
	 */
	public static Optional<Path> find(String executable) {
		if (executable == null || executable.isBlank())
			return Optional.empty();

		Optional<Path> given = toPath(executable.trim());
		if (given.isEmpty())
			return Optional.empty();

		Path path = given.get();
		if (path.getParent() != null)
			return findIn(path.getParent(), path.getFileName().toString());

		for (Path directory : searchPath()) {
			Optional<Path> found = findIn(directory, executable.trim());
			if (found.isPresent())
				return found;
		}
		return Optional.empty();
	}

	private static Optional<Path> findIn(Path directory, String name) {
		List<String> candidates = new ArrayList<>();
		if (OS.getOS() == OS.WINDOWS) {
			// A bare name can not be started on Windows unless it already has an extension
			if (name.contains("."))
				candidates.add(name);
			WINDOWS_EXTENSIONS.forEach(extension -> candidates.add(name + extension));
		} else {
			candidates.add(name);
		}

		for (String candidate : candidates) {
			Path file = directory.resolve(candidate);
			if (Files.isRegularFile(file) && (OS.getOS() == OS.WINDOWS || Files.isExecutable(file)))
				return Optional.of(file);
		}
		return Optional.empty();
	}

	private static Optional<Path> toPath(String path) {
		try {
			return Optional.of(Path.of(path));
		} catch (InvalidPathException e) {
			return Optional.empty();
		}
	}

}
