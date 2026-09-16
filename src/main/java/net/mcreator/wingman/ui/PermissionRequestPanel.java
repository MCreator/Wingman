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

import net.mcreator.ui.component.CollapsiblePanel;
import net.mcreator.ui.component.util.ComponentUtils;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.laf.themes.Theme;
import net.mcreator.wingman.data.StreamListener;
import net.mcreator.wingman.data.StreamListener.PermissionDecision;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

final class PermissionRequestPanel extends JPanel {

	private static final int ARC = 6;

	// Decisions offered to the user, in the order their buttons appear
	private static final List<PermissionDecision> BUTTON_ORDER = List.of(PermissionDecision.ALLOW_ONCE,
			PermissionDecision.ALLOW_ALWAYS, PermissionDecision.REJECT_ONCE, PermissionDecision.REJECT_ALWAYS);

	private final StreamListener.PermissionRequest request;
	private final CompletableFuture<PermissionDecision> decision;

	/**
	 * @param request  Permission request to show
	 * @param decision Future completed with the decision of the user
	 * @param onDecide Called on the EDT with this panel and the decision the user chose, the caller completes the
	 *                 future once the panel is removed
	 */
	PermissionRequestPanel(StreamListener.PermissionRequest request, CompletableFuture<PermissionDecision> decision,
			BiConsumer<PermissionRequestPanel, PermissionDecision> onDecide) {
		super(new BorderLayout());
		this.request = request;
		this.decision = decision;

		setOpaque(false);
		setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

		// Card with the accent outline, the outer panel only provides the margins of the row
		JPanel card = new JPanel(new BorderLayout(0, 6)) {
			@Override protected void paintComponent(Graphics graphics) {
				Graphics2D g2 = (Graphics2D) graphics.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				int width = Math.max(0, getWidth() - 1);
				int height = Math.max(0, getHeight() - 1);
				g2.setColor(Theme.current().getAltBackgroundColor());
				g2.fillRoundRect(0, 0, width, height, ARC, ARC);
				g2.setColor(Theme.current().getForegroundColor());
				g2.drawRoundRect(0, 0, width, height, ARC, ARC);
				g2.dispose();
				super.paintComponent(graphics);
			}
		};
		card.setOpaque(false);
		card.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
		add(card, BorderLayout.CENTER);

		JPanel header = new JPanel(new BorderLayout(0, 2));
		header.setOpaque(false);

		String toolName = ChatComponents.toolDisplayName(request.toolName());
		JTextArea title = ChatComponents.wrappingText(L10N.t("wingman.permission.title", toolName), 13,
				Theme.current().getForegroundColor());
		header.add(title, BorderLayout.NORTH);

		String description = request.title() != null ? ChatComponents.toolDisplayName(request.title()) : null;
		if (description != null && !description.isBlank() && !description.equals(toolName)) {
			header.add(ChatComponents.wrappingText(description, 12, Theme.current().getAltForegroundColor()),
					BorderLayout.CENTER);
		}
		card.add(header, BorderLayout.NORTH);

		String arguments = request.arguments();
		if (arguments != null && !arguments.isBlank() && !arguments.equals("{}") && !arguments.equals("null")) {
			JTextArea argumentsText = ChatComponents.wrappingText(arguments, 11,
					Theme.current().getAltForegroundColor());
			argumentsText.setFont(new Font(Font.MONOSPACED, Font.PLAIN, argumentsText.getFont().getSize()));

			JScrollPane scroll = new JScrollPane(argumentsText);
			scroll.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
			scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
			scroll.setOpaque(false);
			scroll.getViewport().setOpaque(false);
			scroll.setPreferredSize(new Dimension(100, 80));

			CollapsiblePanel collapsible = new CollapsiblePanel(L10N.t("wingman.permission.details"), scroll, false);
			collapsible.setBorder(BorderFactory.createEmptyBorder());
			collapsible.setSmallArrows(true);
			collapsible.toggleVisibility(false);
			card.add(collapsible, BorderLayout.CENTER);
		}

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		buttons.setOpaque(false);
		for (PermissionDecision offered : BUTTON_ORDER) {
			if (!request.offeredDecisions().contains(offered)) {
				continue;
			}
			JButton button = new JButton(buttonLabel(offered));
			button.setFocusPainted(false);
			ComponentUtils.deriveFont(button, 12);
			if (offered == PermissionDecision.ALLOW_ONCE) {
				button.putClientProperty("JButton.buttonType", "default");
			}
			button.addActionListener(_ -> onDecide.accept(this, offered));
			buttons.add(button);
		}
		card.add(buttons, BorderLayout.SOUTH);
	}

	StreamListener.PermissionRequest request() {
		return request;
	}

	CompletableFuture<PermissionDecision> decision() {
		return decision;
	}

	private static String buttonLabel(PermissionDecision decision) {
		return switch (decision) {
			case ALLOW_ONCE -> L10N.t("wingman.permission.allow");
			case ALLOW_ALWAYS -> L10N.t("wingman.permission.allow_always");
			case REJECT_ONCE -> L10N.t("wingman.permission.reject");
			case REJECT_ALWAYS -> L10N.t("wingman.permission.reject_always");
			case CANCELLED -> throw new IllegalArgumentException("Cancelled is not offered to the user");
		};
	}

	@Override public Dimension getMaximumSize() {
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}

}
