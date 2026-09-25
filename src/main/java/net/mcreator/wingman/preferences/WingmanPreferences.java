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

package net.mcreator.wingman.preferences;

import net.mcreator.preferences.PreferencesSection;
import net.mcreator.preferences.entries.StringEntry;

/**
 * Preferences of Wingman. The section is hidden from the generic preferences UI, its values are edited on the
 * Wingman page ({@link WingmanPreferencesPanel}) instead.
 */
public final class WingmanPreferences extends PreferencesSection {

	private static final String IDENTIFIER = "wingman";

	private static WingmanPreferences instance;

	public final StringEntry provider;
	public final StringEntry customCommand;

	private WingmanPreferences() {
		super(IDENTIFIER);
		provider = addEntry(new StringEntry("provider", ""));
		customCommand = addEntry(new StringEntry("customCommand", ""));
	}

	public static void init() {
		instance = new WingmanPreferences();
	}

	public static WingmanPreferences get() {
		return instance;
	}

	@Override public boolean isVisible() {
		return false;
	}

	@Override public String getSectionKey() {
		return IDENTIFIER;
	}

}
