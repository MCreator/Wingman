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

import javax.swing.*;
import java.awt.*;

final class PromptComposerPanel extends JPanel {

	private static final int ARC = 6;

	private final Color backgroundColor;
	private final Color unfocusedBorderColor;
	private final Color focusedBorderColor;
	private boolean focused;

	PromptComposerPanel(Color backgroundColor, Color unfocusedBorderColor, Color focusedBorderColor) {
		this.backgroundColor = backgroundColor;
		this.unfocusedBorderColor = unfocusedBorderColor;
		this.focusedBorderColor = focusedBorderColor;
		setOpaque(false);
	}

	void setFocused(boolean focused) {
		this.focused = focused;
		repaint();
	}

	@Override protected void paintComponent(Graphics graphics) {
		Graphics2D g2 = (Graphics2D) graphics.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int inset = 1;
		int width = Math.max(0, getWidth() - (inset * 2) - 1);
		int height = Math.max(0, getHeight() - (inset * 2) - 1);
		g2.setColor(backgroundColor);
		g2.fillRoundRect(inset, inset, width, height, ARC, ARC);
		g2.setColor(focused ? focusedBorderColor : unfocusedBorderColor);
		g2.drawRoundRect(inset, inset, width, height, ARC, ARC);
		g2.dispose();
		super.paintComponent(graphics);
	}

}
