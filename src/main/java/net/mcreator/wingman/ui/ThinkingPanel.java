package net.mcreator.wingman.ui;

import net.mcreator.ui.component.CollapsiblePanel;
import net.mcreator.ui.component.util.ComponentUtils;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.laf.themes.Theme;

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

	private Long thinkingStartedAtMs;
	private Integer thinkingDurationSeconds;

	ThinkingPanel() {
		super(new BorderLayout());
		setOpaque(false);

		Color mutedText = mutedThinkingTextColor();

		body = new JTextArea();
		body.setEditable(false);
		body.setFocusable(true);
		body.setLineWrap(true);
		body.setWrapStyleWord(true);
		body.setOpaque(false);
		body.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		body.setForeground(mutedText);
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

		collapsible = new CollapsiblePanel(L10N.t("wingman.thinking"), scroll, false);
		collapsible.setBorder(BorderFactory.createEmptyBorder());
		collapsible.setSmallArrows(true);
		add(collapsible, BorderLayout.CENTER);
		setVisible(false);

		addComponentListener(new ComponentAdapter() {
			@Override public void componentResized(ComponentEvent e) {
				if (isVisible()) {
					updateScrollSize();
				}
			}
		});
	}

	void update(String thinkingText, boolean inProgress, Integer rememberedDurationSeconds) {
		if (rememberedDurationSeconds != null) {
			thinkingDurationSeconds = rememberedDurationSeconds;
			thinkingStartedAtMs = null;
		} else if (inProgress) {
			if (thinkingStartedAtMs == null) {
				thinkingStartedAtMs = System.currentTimeMillis();
			}
		} else if (thinkingStartedAtMs != null) {
			thinkingDurationSeconds = toSeconds(System.currentTimeMillis() - thinkingStartedAtMs);
			thinkingStartedAtMs = null;
		}

		String text = thinkingText == null ? "" : thinkingText;
		body.setText(text);
		boolean hasThinking = inProgress || !text.isBlank();
		setVisible(hasThinking);
		if (!hasThinking) {
			return;
		}

		collapsible.setTitle(titleForState(inProgress));
		collapsible.toggleVisibility(inProgress);
		updateScrollSize();
		if (inProgress) {
			body.setCaretPosition(body.getDocument().getLength());
			SwingUtilities.invokeLater(() -> {
				JScrollBar bar = scroll.getVerticalScrollBar();
				bar.setValue(bar.getMaximum());
			});
		}
		revalidate();
		repaint();
	}

	Integer getThinkingDurationSeconds() {
		return thinkingDurationSeconds;
	}

	private String titleForState(boolean inProgress) {
		if (inProgress) {
			return L10N.t("wingman.thinking");
		}
		if (thinkingDurationSeconds != null) {
			return formatThoughtDuration(thinkingDurationSeconds);
		}
		return body.getText().isBlank() ? L10N.t("wingman.thinking") : L10N.t("wingman.thought");
	}

	private static String formatThoughtDuration(int seconds) {
		return seconds == 1 ? L10N.t("wingman.thought_for_second") : L10N.t("wingman.thought_for_seconds", seconds);
	}

	private static int toSeconds(long durationMs) {
		return Math.max(1, (int) Math.ceil(durationMs / 1000.0));
	}

	private void updateScrollSize() {
		int width = getWidth();
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
		if (!isVisible()) {
			return new Dimension(Integer.MAX_VALUE, 0);
		}
		Dimension preferred = getPreferredSize();
		return new Dimension(Integer.MAX_VALUE, preferred.height);
	}
}
