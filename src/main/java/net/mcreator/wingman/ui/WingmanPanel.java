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

package net.mcreator.wingman.ui;

import net.mcreator.ui.MCreator;
import net.mcreator.wingman.acp.AcpChatService;
import net.mcreator.wingman.acp.provider.AcpProviders;
import net.mcreator.wingman.mcp.WingmanMcpServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public class WingmanPanel extends JPanel {

	private static final Logger LOG = LogManager.getLogger(WingmanPanel.class);

	// MCP server exposing this window to the agent, null if it could not be started so the agent works without it
	@Nullable private final WingmanMcpServer mcpServer;
	private final AcpChatService assistantService;

	public WingmanPanel(MCreator mcreator) {
		super(new BorderLayout());

		this.mcpServer = startMcpServer(mcreator);
		this.assistantService = new AcpChatService(mcreator, AcpProviders::selected,
				mcpServer != null ? mcpServer.acpSchemaMCPDefinition() : null);

		ChatsPanel chatsPanel = new ChatsPanel(mcreator, assistantService);
		add(chatsPanel, BorderLayout.CENTER);
	}

	/**
	 * Called when Wingman preferences were saved, so the next message goes to the newly selected agent.
	 */
	public void preferencesChanged() {
		assistantService.disconnect();
	}

	public void close() {
		assistantService.close();
		if (mcpServer != null)
			mcpServer.close();
	}

	@Nullable private static WingmanMcpServer startMcpServer(MCreator mcreator) {
		try {
			return new WingmanMcpServer(mcreator);
		} catch (IOException | RuntimeException e) {
			LOG.warn("Failed to start MCP server for Wingman, the agent will not have MCreator tools", e);
			return null;
		}
	}

}
