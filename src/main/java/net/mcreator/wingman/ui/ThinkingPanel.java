package net.mcreator.wingman.ui;

import net.mcreator.ui.component.CollapsiblePanel;
import net.mcreator.ui.component.util.ComponentUtils;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.laf.themes.Theme;
import net.mcreator.wingman.data.ChatMessage;

import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

final class ThinkingPanel extends JPanel {

	private static final int MAX_VISIBLE_HEIGHT = 160;
	private static final int MIN_VISIBLE_HEIGHT = 48;

	private final CollapsiblePanel collapsible;
	private final JTextArea body;
	private final JScrollPane scroll;

	ThinkingPanel(ChatMessage message, boolean inProgress) {
		super(new BorderLayout());
		setOpaque(false);
		setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));

		body = new JTextArea(message.text());
		body.setEditable(false);
		body.setFocusable(true);
		body.setLineWrap(true);
		body.setWrapStyleWord(true);
		body.setOpaque(false);
		body.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		body.setForeground(mutedThinkingTextColor());
		body.setCaretColor(new Color(0, 0, 0, 0));
		ComponentUtils.deriveFont(body, 12);
		DefaultCaret caret = (DefaultCaret) body.getCaret();
		caret.setVisible(false);
		caret.setSelectionVisible(true);

		scroll = new JScrollPane(body);
		scroll.setBorder(BorderFactory.createEmptyBorder(4, 2, 2, 2));
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
		scroll.getViewport().setOpaque(false);
		scroll.setOpaque(false);
		scroll.getViewport().setBackground(new Color(0, 0, 0, 16));

		collapsible = new CollapsiblePanel(title(inProgress, message.thinkingDurationSeconds()), scroll, false);
		collapsible.setBorder(BorderFactory.createEmptyBorder());
		collapsible.setSmallArrows(true);
		collapsible.toggleVisibility(inProgress);
		add(collapsible, BorderLayout.CENTER);

		addComponentListener(new ComponentAdapter() {
			@Override public void componentResized(ComponentEvent e) {
				updateScrollSize();
			}
		});
	}

	void update(String thinkingText) {
		body.setText(thinkingText);
		updateScrollSize();
		body.setCaretPosition(body.getDocument().getLength());
		SwingUtilities.invokeLater(() -> {
			JScrollBar bar = scroll.getVerticalScrollBar();
			bar.setValue(bar.getMaximum());
		});
		revalidate();
		repaint();
	}

	void finish(ChatMessage message) {
		body.setText(message.text());
		collapsible.setTitle(title(false, message.thinkingDurationSeconds()));
		collapsible.toggleVisibility(false);
		updateScrollSize();
		revalidate();
		repaint();
	}

	private static String title(boolean inProgress, @Nullable Integer thinkingDurationSeconds) {
		if (inProgress) {
			return L10N.t("wingman.thinking");
		}
		if (thinkingDurationSeconds == null) {
			return L10N.t("wingman.thought");
		}
		return thinkingDurationSeconds == 1 ?
				L10N.t("wingman.thought_for_second") :
				L10N.t("wingman.thought_for_seconds", thinkingDurationSeconds);
	}

	private void updateScrollSize() {
		Insets insets = getInsets();
		int width = getWidth() - insets.left - insets.right;
		if (width <= 0) {
			return;
		}
		body.setSize(new Dimension(width, Short.MAX_VALUE));
		Dimension textSize = body.getPreferredSize();
		int height = Math.clamp(textSize.height + 8, MIN_VISIBLE_HEIGHT, MAX_VISIBLE_HEIGHT);
		scroll.setPreferredSize(new Dimension(width, height));
		scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
	}

	private static Color mutedThinkingTextColor() {
		Color foreground = Theme.current().getAltForegroundColor();
		Color background = Theme.current().getAltBackgroundColor();
		float blend = 0.35f;
		return new Color(Math.round(foreground.getRed() * blend + background.getRed() * (1f - blend)),
				Math.round(foreground.getGreen() * blend + background.getGreen() * (1f - blend)),
				Math.round(foreground.getBlue() * blend + background.getBlue() * (1f - blend)));
	}

	@Override public Dimension getMaximumSize() {
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}

}
