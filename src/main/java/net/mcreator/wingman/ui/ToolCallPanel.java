package net.mcreator.wingman.ui;

import net.mcreator.ui.component.SquareLoaderIcon;
import net.mcreator.ui.component.util.ComponentUtils;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.laf.themes.Theme;
import net.mcreator.wingman.data.ChatMessage;
import net.mcreator.wingman.data.StreamListener;

import javax.swing.*;
import java.awt.*;

final class ToolCallPanel extends JPanel {

	private String toolName;
	private final JLabel loader;
	private final JLabel label;

	private ToolCallPanel(String toolName, boolean running) {
		super(new BorderLayout());
		this.toolName = toolName;
		setOpaque(false);
		setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 10));

		JPanel content = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		content.setOpaque(false);

		loader = new JLabel(new SquareLoaderIcon(this, 4, 1, Theme.current().getAltForegroundColor()));
		loader.setVisible(running);
		label = new JLabel(labelText(running));
		label.setForeground(Theme.current().getAltForegroundColor());
		ComponentUtils.deriveFont(label, 13);

		content.add(loader);
		content.add(label);
		add(content, BorderLayout.CENTER);
	}

	static ToolCallPanel running(StreamListener.ToolRequestEvent requestEvent) {
		return new ToolCallPanel(requestEvent.name(), true);
	}

	static ToolCallPanel fromHistory(ChatMessage message) {
		return new ToolCallPanel(message.toolName(), false);
	}

	void setToolName(String toolName) {
		this.toolName = toolName;
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
