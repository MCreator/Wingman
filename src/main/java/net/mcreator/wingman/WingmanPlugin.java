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

package net.mcreator.wingman;

import net.mcreator.plugin.JavaPlugin;
import net.mcreator.plugin.Plugin;
import net.mcreator.plugin.events.workspace.MCreatorClosedEvent;
import net.mcreator.plugin.events.workspace.MCreatorLoadedEvent;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.init.UIRES;
import net.mcreator.wingman.ui.WingmanPanel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WingmanPlugin extends JavaPlugin {

	private static final Logger LOG = LogManager.getLogger("Wingman");

	public static final String DOCK_WINGMAN = "wingman";

	// One Wingman panel per open MCreator window, closed together with the window
	private final Map<MCreator, WingmanPanel> panels = new ConcurrentHashMap<>();

	public WingmanPlugin(Plugin plugin) {
		super(plugin);

		// The dock is added synchronously so it is present when MCreator calculates the frame size right after this event
		addListener(MCreatorLoadedEvent.class, event -> {
			MCreator mcreator = event.getMCreator();
			WingmanPanel wingmanPanel = new WingmanPanel(mcreator);
			panels.put(mcreator, wingmanPanel);
			// TODO: wingman icon
			mcreator.getRightDockRegion()
					.addDock(DOCK_WINGMAN, 280, L10N.t("dock.wingman"), UIRES.get("16px.dock_folder"), wingmanPanel);
		});

		addListener(MCreatorClosedEvent.class, event -> {
			WingmanPanel wingmanPanel = panels.remove(event.getMCreator());
			if (wingmanPanel != null)
				wingmanPanel.close();
		});

		LOG.info("Wingman plugin was loaded");
	}

}
