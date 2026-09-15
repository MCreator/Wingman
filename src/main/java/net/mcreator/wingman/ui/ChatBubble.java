package net.mcreator.wingman.ui;

import net.mcreator.ui.component.util.ComponentUtils;
import net.mcreator.wingman.data.ChatMessage;
import net.mcreator.wingman.util.MarkdownRenderer;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

final class ChatBubble extends JPanel {

	private final ChatMessage.Role role;

	private final JTextArea plainBody;
	private final JEditorPane htmlBody;
	private final JPanel contentHolder;

	private int maxWidth = 0;

	ChatBubble(ChatMessage message) {
		setLayout(new java.awt.BorderLayout(0, 6));
		setOpaque(false);
		setAlignmentY(Component.TOP_ALIGNMENT);

		role = message.role();
		Color fillColor = role.filledBubble() ? role.bubbleBackground() : UIManager.getColor("Panel.background");
		if (fillColor == null) {
			fillColor = role.bubbleBackground();
		}
		Color borderColor = role.filledBubble() ? null : role.bubbleBackground();
		Color fg = role.bubbleForeground();

		plainBody = new JTextArea(message.initialText());
		plainBody.setWrapStyleWord(true);
		plainBody.setLineWrap(true);
		plainBody.setEditable(false);
		plainBody.setFocusable(true);
		plainBody.setOpaque(false);
		plainBody.setForeground(fg);
		plainBody.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		plainBody.setCaretColor(new Color(0, 0, 0, 0));
		ComponentUtils.deriveFont(plainBody, 13);
		DefaultCaret caret = (DefaultCaret) plainBody.getCaret();
		caret.setVisible(false);
		caret.setSelectionVisible(true);

		htmlBody = new JEditorPane();
		htmlBody.setEditorKit(new WrappingHtmlEditorKit()); // text/html kit that wraps long words and <pre> blocks
		htmlBody.setEditable(false);
		htmlBody.setFocusable(true);
		htmlBody.setOpaque(false);
		htmlBody.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
		htmlBody.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		htmlBody.setForeground(fg);
		htmlBody.setFont(plainBody.getFont());
		ComponentUtils.deriveFont(htmlBody, 13);

		JPanel holder = role.constrainedBubbleWidth() ?
				new RoundedPanel(fillColor, borderColor, 12) :
				new JPanel(new BorderLayout());
		this.contentHolder = holder;
		holder.setOpaque(false);
		holder.setBorder(role.constrainedBubbleWidth() ?
				BorderFactory.createEmptyBorder(10, 12, 10, 12) :
				BorderFactory.createEmptyBorder(2, 0, 2, 0));
		if (role == ChatMessage.Role.USER) {
			holder.add(plainBody, BorderLayout.CENTER);
		} else {
			holder.add(htmlBody, BorderLayout.CENTER);
			setHtmlContent(message.initialText());
		}
		add(holder, BorderLayout.CENTER);

		setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
	}

	void setText(String text) {
		if (role == ChatMessage.Role.USER) {
			plainBody.setText(text);
		} else {
			setHtmlContent(text);
		}
		revalidate();
		repaint();
	}

	private void setHtmlContent(String text) {
		boolean showText = !text.isBlank();
		htmlBody.setVisible(showText);
		if (showText) {
			htmlBody.setText(MarkdownRenderer.toHtmlDocument(text, role.bubbleForeground(), plainBody.getFont()));
			htmlBody.setCaretPosition(0);
			htmlBody.setPreferredSize(null);
			contentHolder.revalidate();
		}
	}

	void setMaxWidth(int width) {
		if (!role.constrainedBubbleWidth()) {
			return;
		}
		int newWidth = Math.max(32, width);
		if (newWidth != this.maxWidth) {
			this.maxWidth = newWidth;
			revalidate();
			repaint();
		}
	}

	@Override public Dimension getMaximumSize() {
		if (!role.constrainedBubbleWidth()) {
			return new Dimension(Integer.MAX_VALUE, super.getMaximumSize().height);
		}
		Dimension d = super.getMaximumSize();
		// Constrain to maxWidth so BoxLayout can clamp growth
		return new Dimension(maxWidth, d.height);
	}

	@Override public Dimension getPreferredSize() {
		Dimension d = super.getPreferredSize();
		if (!role.constrainedBubbleWidth()) {
			return d;
		}
		// Make the bubble consume the available width provided by the container
		// so rows appear full-width regardless of short text content.
		d.width = maxWidth;
		return d;
	}

	private static final class RoundedPanel extends JPanel {
		private final Color backgroundColor;
		private final Color borderColor;
		private final int arc;

		private RoundedPanel(Color backgroundColor, Color borderColor, int arc) {
			super(new BorderLayout());
			this.backgroundColor = backgroundColor;
			this.borderColor = borderColor;
			this.arc = arc;
			setOpaque(false);
		}

		@Override protected void paintComponent(Graphics graphics) {
			Graphics2D g2 = (Graphics2D) graphics.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

			int width = getWidth();
			int height = getHeight();
			if (width <= 0 || height <= 0) {
				g2.dispose();
				super.paintComponent(graphics);
				return;
			}

			float fillWidth = width - 1f;
			float fillHeight = height - 1f;
			g2.setColor(backgroundColor);
			g2.fill(new RoundRectangle2D.Float(0f, 0f, fillWidth, fillHeight, arc, arc));
			if (borderColor != null) {
				g2.setColor(borderColor);
				g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, width - 1f, height - 1f, arc, arc));
			}
			g2.dispose();
			super.paintComponent(graphics);
		}
	}

}
