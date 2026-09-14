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

import net.mcreator.ui.init.L10N;
import net.mcreator.wingman.acp.provider.AgentCommand;
import net.mcreator.wingman.acp.provider.StdioAcpProvider;
import net.mcreator.wingman.preferences.WingmanPreferences;

/**
 * Any agent speaking ACP over stdio, started with the command line the user entered in preferences.
 */
public final class CustomAcpProvider extends StdioAcpProvider {

	public CustomAcpProvider() {
		// Providers are created before MCreator loads translations, so the localized texts are resolved on access
		super("custom", "Custom ACP agent", "", null);
	}

	@Override public String name() {
		return L10N.t("wingman.provider.custom");
	}

	@Override public String description() {
		return L10N.t("wingman.provider.custom.description");
	}

	@Override public AgentCommand command() {
		return AgentCommand.parse(WingmanPreferences.get().customCommand.get());
	}

}
