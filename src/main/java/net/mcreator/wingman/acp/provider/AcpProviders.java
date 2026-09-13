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

import net.mcreator.wingman.acp.provider.impl.CursorProvider;
import net.mcreator.wingman.acp.provider.impl.CustomAcpProvider;
import net.mcreator.wingman.acp.provider.impl.GitHubCopilotProvider;
import net.mcreator.wingman.preferences.WingmanPreferences;

import java.util.List;
import java.util.Optional;

public final class AcpProviders {

	public static final AcpProvider CUSTOM = new CustomAcpProvider();

	public static final List<AcpProvider> ALL = List.of(new CursorProvider(), new GitHubCopilotProvider(), CUSTOM);

	public static Optional<AcpProvider> byId(String id) {
		return ALL.stream().filter(provider -> provider.id().equals(id)).findFirst();
	}

	public static AcpProvider selected() {
		return byId(WingmanPreferences.get().provider.get()).orElseGet(() -> ALL.stream()
				.filter(provider -> provider instanceof StdioAcpProvider stdio && stdio.installedExecutable()
						.isPresent()).findFirst().orElse(ALL.getFirst()));
	}

}
