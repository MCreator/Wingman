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

import net.mcreator.ui.component.util.ComponentUtils;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;

final class ChatComponents {

	static String toolDisplayName(String toolName) {
		return toolName.replaceAll("mcreator.*?: ", "");
	}

	static JTextArea wrappingText(String text, int fontSize, Color foreground) {
		JTextArea area = new JTextArea(text);
		area.setEditable(false);
		area.setFocusable(true);
		area.setOpaque(false);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setBorder(BorderFactory.createEmptyBorder());
		area.setForeground(foreground);
		area.setCaretColor(new Color(0, 0, 0, 0));
		ComponentUtils.deriveFont(area, fontSize);
		DefaultCaret caret = (DefaultCaret) area.getCaret();
		caret.setVisible(false);
		caret.setSelectionVisible(true);
		return area;
	}

}
