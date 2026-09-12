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

final class ContextUsageBar extends JComponent {

	private static final int HEIGHT = 2;

	private final Color fillColor;
	private float usage;

	ContextUsageBar(Color fillColor) {
		this.fillColor = fillColor;
		setOpaque(false);
	}

	void setUsage(float usage) {
		this.usage = Math.clamp(usage, 0f, 1f);
		repaint();
	}

	@Override public Dimension getPreferredSize() {
		return new Dimension(0, HEIGHT);
	}

	@Override public Dimension getMinimumSize() {
		return getPreferredSize();
	}

	@Override protected void paintComponent(Graphics graphics) {
		super.paintComponent(graphics);
		if (usage < 0.01)
			return;
		Graphics2D g2 = (Graphics2D) graphics.create();
		int fillWidth = Math.max(HEIGHT, Math.round(getWidth() * usage));
		g2.setColor(fillColor);
		g2.fillRect(0, 0, fillWidth, getHeight());
		g2.dispose();
	}

}
