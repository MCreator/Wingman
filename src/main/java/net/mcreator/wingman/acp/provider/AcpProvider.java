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

import com.agentclientprotocol.sdk.spec.AcpClientTransport;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import net.mcreator.ui.laf.themes.Theme;

import javax.annotation.Nullable;
import javax.swing.*;
import java.net.URL;

public abstract class AcpProvider {

	private static final int ICON_SIZE = 32;

	private final String id;
	private final String name;
	private final String description;
	@Nullable private final String loginCommand;

	private Icon icon;

	/**
	 * @param loginCommand Command the user runs in a terminal to log in to the agent, if the agent has one
	 */
	protected AcpProvider(String id, String name, String description, @Nullable String loginCommand) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.loginCommand = loginCommand;
	}

	public String id() {
		return id;
	}

	public String name() {
		return name;
	}

	public String description() {
		return description;
	}

	@Nullable public String loginCommand() {
		return loginCommand;
	}

	/**
	 * Creates a new transport connecting to the agent. Transports are single use, one is created per connection.
	 *
	 * @throws IllegalStateException if the agent is not available on this device, with a message for the user
	 */
	public abstract AcpClientTransport transport();

	public synchronized Icon icon() {
		if (icon == null) {
			URL url = AcpProvider.class.getResource("/net/mcreator/wingman/provider/" + id + ".svg");
			FlatSVGIcon svgIcon = new FlatSVGIcon(url).derive(ICON_SIZE, ICON_SIZE);
			// Provider icons are monochrome, so they are painted in the foreground color of the current theme
			svgIcon.setColorFilter(new FlatSVGIcon.ColorFilter(_ -> Theme.current().getForegroundColor()));
			icon = svgIcon;
		}
		return icon;
	}

	@Override public String toString() {
		return name;
	}

}
