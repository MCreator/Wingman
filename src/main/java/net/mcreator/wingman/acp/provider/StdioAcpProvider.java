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

import com.agentclientprotocol.sdk.client.transport.AgentParameters;
import com.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;
import com.agentclientprotocol.sdk.json.JacksonAcpJsonMapperSupplier;
import com.agentclientprotocol.sdk.spec.AcpClientTransport;
import net.mcreator.io.OS;
import net.mcreator.ui.init.L10N;
import net.mcreator.wingman.util.ExecutableFinder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.*;

public abstract class StdioAcpProvider extends AcpProvider {

	private static final Logger LOG = LogManager.getLogger(StdioAcpProvider.class);

	protected StdioAcpProvider(String id, String name, String description, @Nullable String loginCommand) {
		super(id, name, description, loginCommand);
	}

	public abstract AgentCommand command();

	public Optional<Path> installedExecutable() {
		return command().resolveExecutable();
	}

	@Override public AcpClientTransport transport() {
		AgentCommand command = command();
		Path executable = command.resolveExecutable()
				.orElseThrow(() -> new IllegalStateException(L10N.t("wingman.provider.not_installed.message", name())));

		List<String> commandLine = new ArrayList<>();
		String fileName = executable.getFileName().toString().toLowerCase(Locale.ROOT);
		if (OS.getOS() == OS.WINDOWS && (fileName.endsWith(".cmd") || fileName.endsWith(".bat"))) {
			// Batch scripts, e.g. the shims of npm installed tools, need the command interpreter
			commandLine.add("cmd.exe");
			commandLine.add("/c");
		}
		commandLine.add(executable.toString());
		commandLine.addAll(command.args());

		// The SDK passes only a few environment variables to the agent by default, but agents expect the whole
		// environment like in a shell (e.g., for tokens or proxies), with PATH covering the tools Wingman finds
		Map<String, String> environment = new HashMap<>(System.getenv());
		environment.put("PATH", ExecutableFinder.searchPathVariable());

		LOG.info("Starting agent {}: {}", name(), commandLine);
		AgentParameters parameters = AgentParameters.builder(commandLine.getFirst())
				.args(commandLine.subList(1, commandLine.size())).env(environment).build();
		StdioAcpClientTransport transport = new StdioAcpClientTransport(parameters,
				new JacksonAcpJsonMapperSupplier().get());
		transport.setStdErrorHandler(line -> LOG.debug("[{}] {}", id(), line));
		transport.setExceptionHandler(e -> LOG.warn("ACP transport error", e));
		return transport;
	}

	protected static AgentCommand command(String executable, String... args) {
		return new AgentCommand(executable, List.of(args));
	}

}
