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

package net.mcreator.wingman.acp.provider.impl;

import net.mcreator.wingman.acp.provider.AgentCommand;
import net.mcreator.wingman.acp.provider.StdioAcpProvider;
import net.mcreator.wingman.util.ExecutableFinder;

public final class CursorProvider extends StdioAcpProvider {

	public CursorProvider() {
		super("cursor", "Cursor", "Cursor's coding agent", "agent login");
	}

	@Override public AgentCommand command() {
		// The Cursor CLI is installed as cursor-agent, newer versions also register the shorter agent command
		return command(ExecutableFinder.find("cursor-agent").isPresent() ? "cursor-agent" : "agent", "acp");
	}

}
