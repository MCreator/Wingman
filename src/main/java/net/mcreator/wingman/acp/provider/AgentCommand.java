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

package net.mcreator.wingman.acp.provider;

import net.mcreator.wingman.util.ExecutableFinder;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Command line starting an agent in ACP stdio mode.
 *
 * @param executable Name of the executable as expected on PATH, or a path to it
 * @param args       Arguments passed to the executable
 */
public record AgentCommand(String executable, List<String> args) {

	/**
	 * @return The command of a whitespace separated command line, with an empty executable if the line is blank
	 */
	public static AgentCommand parse(String commandLine) {
		List<String> tokens = commandLine == null || commandLine.isBlank() ?
				List.of() :
				Arrays.asList(commandLine.trim().split("\\s+"));
		return tokens.isEmpty() ?
				new AgentCommand("", List.of()) :
				new AgentCommand(tokens.getFirst(), List.copyOf(tokens.subList(1, tokens.size())));
	}

	public Optional<Path> resolveExecutable() {
		return ExecutableFinder.find(executable);
	}

	@Override @Nonnull public String toString() {
		return (executable + " " + String.join(" ", args)).trim();
	}

}
