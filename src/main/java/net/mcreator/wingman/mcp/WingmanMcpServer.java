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

package net.mcreator.wingman.mcp;

import com.agentclientprotocol.sdk.spec.AcpSchema;
import net.mcreator.io.mcp.transport.HttpMcpTransport;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.mcp.MCreatorMcp;
import net.mcreator.util.NetworkUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

public final class WingmanMcpServer implements Closeable {

	private static final Logger LOG = LogManager.getLogger(WingmanMcpServer.class);

	// Name the MCP server is registered with on the agent, referenced in the session instructions
	public static final String NAME = "mcreator";

	private final MCreatorMcp mcp;
	private final int port;

	public WingmanMcpServer(MCreator mcreator) throws IOException {
		this.port = NetworkUtils.findAvailablePort();
		if (port == -1)
			throw new IOException("No available port for the MCreator MCP server");

		this.mcp = new MCreatorMcp(new HttpMcpTransport(port), () -> mcreator);
		try {
			mcp.start();
		} catch (IOException | RuntimeException e) {
			mcp.close();
			throw e;
		}
		LOG.info("Wingman MCP server for workspace {} started at {}", mcreator.getWorkspace().getWorkspaceFolder(),
				url());
	}

	public String url() {
		return "http://127.0.0.1:" + port + "/mcp";
	}

	public AcpSchema.McpServer acpSchemaMCPDefinition() {
		return new AcpSchema.McpServerHttp(NAME, url(), List.of());
	}

	@Override public void close() {
		try {
			mcp.close();
		} catch (IOException | RuntimeException e) {
			LOG.warn("Failed to stop Wingman MCP server", e);
		}
	}

}
