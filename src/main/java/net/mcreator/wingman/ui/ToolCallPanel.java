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

import net.mcreator.ui.component.SquareLoaderIcon;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.laf.themes.Theme;
import net.mcreator.wingman.data.ChatMessage;
import net.mcreator.wingman.data.StreamListener;

import javax.swing.*;
import java.awt.*;

final class ToolCallPanel extends JPanel {

	private String toolName;
	private final JLabel loader;
	private final JTextArea label;

	private ToolCallPanel(String toolName, boolean running) {
		super(new BorderLayout(6, 0));

		setOpaque(false);
		setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 10));

		loader = new JLabel(new SquareLoaderIcon(this, 4, 1, Theme.current().getAltForegroundColor()));
		loader.setVisible(running);
		loader.setVerticalAlignment(SwingConstants.TOP);

		label = ChatComponents.wrappingText(labelText(running), 13, Theme.current().getAltForegroundColor());

		JPanel loaderHolder = new JPanel(new BorderLayout());
		loaderHolder.setOpaque(false);
		loaderHolder.add(loader, BorderLayout.NORTH);
		add(loaderHolder, BorderLayout.WEST);
		add(label, BorderLayout.CENTER);

		setToolName(toolName);
	}

	static ToolCallPanel running(StreamListener.ToolRequestEvent requestEvent) {
		return new ToolCallPanel(requestEvent.name(), true);
	}

	static ToolCallPanel fromHistory(ChatMessage message) {
		return new ToolCallPanel(message.toolName(), false);
	}

	void setToolName(String toolName) {
		this.toolName = ChatComponents.toolDisplayName(toolName);
		label.setText(labelText(loader.isVisible()));
		revalidate();
		repaint();
	}

	void markCompleted() {
		loader.setVisible(false);
		label.setText(labelText(false));
		revalidate();
		repaint();
	}

	private String labelText(boolean running) {
		return running ? L10N.t("wingman.tool.running", toolName) : L10N.t("wingman.tool.called", toolName);
	}

	@Override public Dimension getMaximumSize() {
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}

}
