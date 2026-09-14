package net.mcreator.wingman.ui;

import net.mcreator.ui.MCreator;
import net.mcreator.ui.component.JScrollablePopupMenu;
import net.mcreator.ui.component.ScrollWheelPassLayer;
import net.mcreator.ui.component.ScrollablePanel;
import net.mcreator.ui.component.util.ComponentUtils;
import net.mcreator.ui.component.util.PanelUtils;
import net.mcreator.ui.dialogs.preferences.PreferencesDialog;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.init.UIRES;
import net.mcreator.ui.laf.themes.Theme;
import net.mcreator.util.StringUtils;
import net.mcreator.wingman.data.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

public final class ChatsPanel extends JPanel {

	private static final Logger LOG = LogManager.getLogger(ChatsPanel.class);

	private static final int SIDE_GAP_WIDE = 36;
	private static final int SIDE_GAP_NARROW = 8;

	private static final String CARD_MESSAGES = "messages";
	private static final String CARD_INIT = "init";

	private static final String INPUT_PLACEHOLDER = L10N.t("wingman.input.placeholder");

	private final ScrollablePanel messagesPanel = new ScrollablePanel();
	private final JScrollPane messagesScroll;
	private final CardLayout contentCardLayout = new CardLayout();
	private final JPanel contentCards = new JPanel(contentCardLayout);

	private final JTextArea inputArea;

	private final JButton sendButton = new JButton(UIRES.get("wingman.send"));

	private final AssistantService assistantService;
	private final ChatHistoryStorage historyStorage;

	private ChatConversation activeConversation;

	private final JButton conversationsButton;
	private final Timer resizeDebounceTimer;
	private final ContextUsageBar contextUsageBar;
	private final Map<String, ToolCallPanel> activeToolPanels = new LinkedHashMap<>();
	// Conversations that already got a local history checkpoint since this MCreator instance was opened
	private final Set<String> checkpointedConversations = new HashSet<>();

	private int lastAppliedBubbleWidth = -1;
	private boolean requestInProgress;
	private boolean cancelRequested;

	private final MCreator mcreator;

	public ChatsPanel(MCreator mcreator, AssistantService assistantService) {
		super(new BorderLayout(0, 5));
		this.mcreator = mcreator;
		this.assistantService = assistantService;
		this.historyStorage = assistantService.getHistoryStorage();
		this.activeConversation = historyStorage.newConversation();

		setBackground(Theme.current().getSecondAltBackgroundColor());

		this.resizeDebounceTimer = new Timer(50, _ -> applyBubbleWidthIfNeeded());
		this.resizeDebounceTimer.setRepeats(false);

		conversationsButton = new JButton(UIRES.get("more"));
		conversationsButton.setToolTipText(L10N.t("wingman.conversations"));
		conversationsButton.addActionListener(_ -> showConversationsMenu());

		JButton settingsButton = new JButton(UIRES.get("settings"));
		settingsButton.setToolTipText(L10N.t("wingman.settings"));
		settingsButton.addActionListener(_ -> new PreferencesDialog(mcreator, L10N.t("wingman.preferences.page")));

		JToolBar headerActions = new JToolBar();
		headerActions.setFloatable(false);
		headerActions.setOpaque(false);
		headerActions.add(Box.createHorizontalGlue());
		headerActions.add(settingsButton);
		headerActions.add(conversationsButton);
		add(headerActions, BorderLayout.NORTH);

		messagesPanel.setLayout(new BoxLayout(messagesPanel, BoxLayout.Y_AXIS));
		messagesPanel.setOpaque(false);
		messagesScroll = new JScrollPane(messagesPanel);
		messagesScroll.setBorder(BorderFactory.createEmptyBorder());
		messagesScroll.getViewport().setBackground(UIManager.getColor("Panel.background"));
		messagesScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		messagesScroll.getVerticalScrollBar().setUnitIncrement(26);
		messagesScroll.setOpaque(false);
		messagesScroll.getViewport().setOpaque(false);

		contextUsageBar = new ContextUsageBar(Theme.current().getInterfaceAccentColor());

		inputArea = new JTextArea(2, 28) {
			@Override protected void paintComponent(Graphics g) {
				super.paintComponent(g);
				if (getText().isEmpty()) {
					Graphics2D g2 = (Graphics2D) g;
					g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
					g2.setColor(Theme.current().getAltForegroundColor());
					Insets insets = getInsets();
					FontMetrics fm = g2.getFontMetrics(getFont());
					int x = insets.left + getMargin().left;
					int y = insets.top + getMargin().top + fm.getAscent();
					g2.setFont(getFont());
					g2.drawString(INPUT_PLACEHOLDER, x, y - 2);
				}
			}
		};

		// Reduce repaint artifacts during resize
		if (messagesScroll.getViewport() != null) {
			messagesScroll.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);

			// Track viewport width and update bubble widths responsively
			messagesScroll.getViewport().addComponentListener(new ComponentAdapter() {
				@Override public void componentResized(ComponentEvent e) {
					resizeDebounceTimer.restart();
				}
			});
		}
		contentCards.setOpaque(false);
		contentCards.add(new JLayer<>(messagesScroll, new ScrollWheelPassLayer()), CARD_MESSAGES);
		JLabel guideLabel = L10N.label("wingman.start_guide");
		guideLabel.setMinimumSize(new Dimension(250, 150));
		guideLabel.setForeground(Theme.current().getAltForegroundColor());
		ComponentUtils.deriveFont(guideLabel, 14);
		contentCards.add(PanelUtils.totalCenterInPanel(guideLabel), CARD_INIT);
		add(contentCards, BorderLayout.CENTER);
		messagesPanel.add(Box.createVerticalGlue());

		add(buildInputPanel(), BorderLayout.SOUTH);

		SwingUtilities.invokeLater(() -> {
			applyBubbleWidthIfNeeded();
			refreshContextUsage();
			updateContentView();
		});
	}

	private void updateContentView() {
		contentCardLayout.show(contentCards, hasVisibleMessages() ? CARD_MESSAGES : CARD_INIT);
	}

	private boolean hasVisibleMessages() {
		for (Component component : messagesPanel.getComponents()) {
			if (!(component instanceof Box.Filler)) {
				return true;
			}
		}
		return false;
	}

	private void refreshContextUsage() {
		contextUsageBar.setUsage(assistantService.getContextUsage(activeConversation));
	}

	private void showConversationsMenu() {
		if (isBusy())
			return;

		JScrollablePopupMenu popup = new JScrollablePopupMenu();
		popup.setBackground(Theme.current().getSecondAltBackgroundColor());
		popup.setMaximumVisibleRows(18);

		JMenuItem newConversationItem = new JMenuItem(L10N.t("wingman.conversation.new"), UIRES.get("16px.add"));
		newConversationItem.addActionListener(_ -> startNewConversation());
		popup.add(newConversationItem);

		List<ChatConversation.Summary> conversations = historyStorage.listSummaries();
		if (!conversations.isEmpty()) {
			popup.addSeparator();
			String activeConversationId = activeConversation.id();
			for (ChatConversation.Summary conversation : conversations) {
				popup.add(buildConversationMenuRow(popup, conversation, activeConversationId));
			}
		}

		popup.pack();
		Dimension popupSize = popup.getPreferredSize();
		popup.show(conversationsButton, conversationsButton.getWidth() - popupSize.width,
				conversationsButton.getHeight());
	}

	private JPanel buildConversationMenuRow(JPopupMenu popup, ChatConversation.Summary conversation,
			String activeConversationId) {
		Color tint = Theme.current().getInterfaceAccentColor();
		String tintColor = String.format("#%02x%02x%02x", tint.getRed(), tint.getGreen(), tint.getBlue());

		boolean isActive = conversation.id().equals(activeConversationId);

		JPanel row = new JPanel(new BorderLayout(0, 0));
		row.setOpaque(false);

		String title = conversation.title();
		JButton openButton = new JButton(StringUtils.abbreviateString(title, 36));
		openButton.setHorizontalAlignment(SwingConstants.LEFT);
		openButton.setBorderPainted(false);
		if (isActive) {
			openButton.setFont(openButton.getFont().deriveFont(Font.BOLD));
		}
		openButton.setBackground(Theme.current().getSecondAltBackgroundColor());
		openButton.putClientProperty("FlatLaf.style", "hoverBackground: " + tintColor);
		openButton.addActionListener(_ -> {
			popup.setVisible(false);
			switchToConversation(conversation.id());
		});
		row.add(openButton, BorderLayout.CENTER);

		JButton deleteButton = new JButton(UIRES.get("close_small"));
		deleteButton.setBorderPainted(false);
		deleteButton.setFocusPainted(false);
		deleteButton.setBackground(Theme.current().getSecondAltBackgroundColor());
		deleteButton.putClientProperty("FlatLaf.style", "hoverBackground: " + tintColor);
		deleteButton.addActionListener(_ -> {
			popup.setVisible(false);
			deleteConversation(conversation.id(), conversation.title());
		});
		row.add(deleteButton, BorderLayout.EAST);

		return row;
	}

	private void startNewConversation() {
		if (isBusy()) {
			return;
		}
		activeConversation = historyStorage.newConversation();
		reloadActiveConversationView();
	}

	private void switchToConversation(String conversationId) {
		if (isBusy() || conversationId.equals(activeConversation.id())) {
			return;
		}
		ChatConversation conversation = historyStorage.load(conversationId);
		if (conversation != null) {
			activeConversation = conversation;
		}
		reloadActiveConversationView();
	}

	private void deleteConversation(String conversationId, String conversationTitle) {
		if (isBusy()) {
			return;
		}

		int choice = JOptionPane.showConfirmDialog(mcreator,
				L10N.t("wingman.conversation.delete.message", conversationTitle),
				L10N.t("wingman.conversation.delete.title"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (choice != JOptionPane.YES_OPTION) {
			return;
		}

		historyStorage.delete(conversationId);
		if (activeConversation.id().equals(conversationId)) {
			activeConversation = historyStorage.newConversation();
		}
		reloadActiveConversationView();
	}

	private void reloadActiveConversationView() {
		clearMessagesPanel();
		List<ChatMessage> history = activeConversation.messages();
		for (int i = 0; i < history.size(); i++) {
			appendHistoryMessage(history.get(i), i, history);
		}
		inputArea.setText("");
		refreshContextUsage();
		updateContentView();
		SwingUtilities.invokeLater(() -> {
			applyBubbleWidthIfNeeded();
			if (!history.isEmpty()) {
				scrollToBottom();
			}
		});
	}

	private void clearMessagesPanel() {
		activeToolPanels.clear();
		messagesPanel.removeAll();
		messagesPanel.add(Box.createVerticalGlue());
		messagesPanel.revalidate();
		messagesPanel.repaint();
		lastAppliedBubbleWidth = -1;
		updateContentView();
	}

	private boolean isBusy() {
		return requestInProgress;
	}

	private void setRequestInProgress(boolean inProgress) {
		requestInProgress = inProgress;
		sendButton.setIcon(UIRES.get(inProgress ? "wingman.stop" : "wingman.send"));
		sendButton.setToolTipText(L10N.t(inProgress ? "wingman.stop" : "wingman.send"));
		sendButton.setEnabled(true);
		conversationsButton.setEnabled(!inProgress);
	}

	private void cancelCurrentRequest() {
		if (!requestInProgress || cancelRequested) {
			return;
		}
		cancelRequested = true;
		sendButton.setEnabled(false);
		assistantService.cancel(activeConversation);
	}

	private JPanel buildInputPanel() {
		JPanel panel = new JPanel(new BorderLayout(0, 0));
		panel.setBorder(BorderFactory.createEmptyBorder(5, 3, 3, 3));
		panel.setOpaque(false);

		Color borderColor = UIManager.getColor("Component.borderColor");
		Color composerBackground = UIManager.getColor("TextArea.background");

		PromptComposerPanel composer = new PromptComposerPanel(composerBackground, borderColor,
				Theme.current().getInterfaceAccentColor());
		composer.setLayout(new BorderLayout(0, 0));
		composer.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 8));

		inputArea.setLineWrap(true);
		inputArea.setWrapStyleWord(true);
		inputArea.setOpaque(false);
		inputArea.setBorder(BorderFactory.createEmptyBorder());
		inputArea.setMargin(new Insets(2, 0, 0, 0));
		inputArea.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "send-message");
		inputArea.getInputMap().put(KeyStroke.getKeyStroke("shift ENTER"), "insert-break");
		inputArea.getActionMap().put("send-message", new AbstractAction() {
			@Override public void actionPerformed(ActionEvent e) {
				sendCurrentMessage();
			}
		});
		inputArea.getActionMap().put("insert-break", new AbstractAction() {
			@Override public void actionPerformed(ActionEvent e) {
				inputArea.replaceSelection("\n");
			}
		});
		inputArea.addFocusListener(new FocusAdapter() {
			@Override public void focusGained(FocusEvent e) {
				composer.setFocused(true);
			}

			@Override public void focusLost(FocusEvent e) {
				composer.setFocused(false);
			}
		});
		ComponentUtils.deriveFont(inputArea, 13);
		inputArea.getDocument().addDocumentListener(new DocumentListener() {
			@Override public void insertUpdate(DocumentEvent e) {
				inputArea.repaint();
			}

			@Override public void removeUpdate(DocumentEvent e) {
				inputArea.repaint();
			}

			@Override public void changedUpdate(DocumentEvent e) {
				inputArea.repaint();
			}
		});

		sendButton.addActionListener(_ -> {
			if (requestInProgress)
				cancelCurrentRequest();
			else
				sendCurrentMessage();
		});
		sendButton.setToolTipText(L10N.t("wingman.send"));
		sendButton.putClientProperty("JButton.buttonType", "roundRect");
		sendButton.setMargin(new Insets(1, 1, 1, 1));

		JScrollPane inputScroll = new JScrollPane(inputArea);
		inputScroll.setBorder(BorderFactory.createEmptyBorder());
		inputScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		inputScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		inputScroll.getViewport().setOpaque(false);
		inputScroll.setOpaque(false);

		composer.add(inputScroll, BorderLayout.CENTER);

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		actions.setOpaque(false);
		actions.add(sendButton);
		composer.add(actions, BorderLayout.SOUTH);

		JPanel inputWrapper = new JPanel(new BorderLayout(0, 0));
		inputWrapper.setOpaque(false);
		inputWrapper.add(composer, BorderLayout.CENTER);
		inputWrapper.add(contextUsageBar, BorderLayout.SOUTH);
		panel.add(inputWrapper, BorderLayout.CENTER);

		return panel;
	}

	private void sendCurrentMessage() {
		if (isBusy()) {
			return;
		}
		String userText = inputArea.getText().trim();
		if (userText.isEmpty()) {
			return;
		}
		inputArea.setText("");
		if (checkpointedConversations.add(activeConversation.id())) {
			// Snapshot the workspace once per conversation and MCreator session before the agent starts working
			mcreator.getWorkspace().getHistoryManager().importantCheckpoint("wingman_conversation");
		}
		appendMessage(ChatMessage.user(userText));
		cancelRequested = false;
		setRequestInProgress(true);
		refreshContextUsage();
		ThinkingHandle thinking = showThinkingIndicator();
		AtomicReference<StreamedAssistantRow> assistantRowRef = new AtomicReference<>();

		StreamListener listener = new StreamListener() {
			@Override public void onPartialResponse(String responseText) {
				if (responseText == null || responseText.isBlank())
					return;

				SwingUtilities.invokeLater(() -> {
					thinking.stop();
					if (assistantRowRef.get() == null) {
						assistantRowRef.set(appendAssistantBubble());
					}
					assistantRowRef.get().bubble()
							.setText(responseText, responseText.toLowerCase().contains("</thinking>"));
					messagesPanel.revalidate();
					messagesPanel.repaint();
					scrollToBottom();
				});
			}

			@Override public void onToolRequest(StreamListener.ToolRequestEvent request) {
				SwingUtilities.invokeLater(() -> {
					thinking.stop();
					showRunningTool(request);
				});
			}

			@Override public void onToolResult(StreamListener.ToolResultEvent result) {
				SwingUtilities.invokeLater(() -> {
					finishToolCall(result);
					refreshContextUsage();
				});
			}

			@Override public void onCompleteResponse(String finalText) {
				SwingUtilities.invokeLater(() -> {
					thinking.stop();
					hideAllRunningTools();
					if (finalText != null && !finalText.isBlank()) {
						if (assistantRowRef.get() == null) {
							assistantRowRef.set(appendAssistantBubble());
						}
						ChatBubble bubble = assistantRowRef.get().bubble();
						bubble.setText(finalText, true);
						Integer thinkingDurationSeconds = bubble.getThinkingDurationSeconds();
						if (thinkingDurationSeconds != null) {
							activeConversation.updateLastAssistantThinkingDuration(thinkingDurationSeconds);
						}
						messagesPanel.revalidate();
						messagesPanel.repaint();
					}
					if (cancelRequested) {
						ChatMessage cancelledMessage = ChatMessage.system(L10N.t("wingman.request.cancelled"));
						activeConversation.addMessage(cancelledMessage);
						appendSystemError(cancelledMessage);
					}
					setRequestInProgress(false);
					refreshContextUsage();
					scrollToBottom();
				});
			}

			@Override public void onError(String errorMessage) {
				SwingUtilities.invokeLater(() -> {
					thinking.stop();
					hideAllRunningTools();
					appendSystemError(ChatMessage.requestFailed(errorMessage));
					setRequestInProgress(false);
					refreshContextUsage();
				});
			}

			@Override public PermissionDecision onPermissionRequest(PermissionRequest request) {
				return PermissionDecision.ALLOW_ONCE; // TODO: implement approval system, and read-only mode
			}
		};

		CompletableFuture.runAsync(() -> assistantService.stream(activeConversation, userText, listener))
				.exceptionally(error -> {
					// A service that throws instead of reporting through the listener must not leave the UI waiting
					Throwable cause =
							error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
					LOG.error("Assistant request failed", cause);
					listener.onError(cause.toString());
					return null;
				});
	}

	private void appendMessage(ChatMessage message) {
		if (message.role() == ChatMessage.Role.SYSTEM)
			appendSystemError(message);
		else
			appendMessageBubble(new ChatBubble(message), message.role());
	}

	private void appendHistoryMessage(ChatMessage message, int index, List<ChatMessage> history) {
		switch (message.role()) {
		case SYSTEM -> appendSystemError(message);
		case TOOL_REQUEST -> {
			if (!hasMatchingToolResultAfter(index, message.toolCallId(), history)) {
				appendToolCallPanel(message);
			}
		}
		case TOOL_RESULT -> appendToolCallPanel(message);
		default -> appendMessageBubble(new ChatBubble(message), message.role());
		}
	}

	private void appendSystemError(ChatMessage message) {
		String errorText = systemErrorText(message);
		if (errorText == null) {
			return;
		}
		appendActivityEvent(errorText);
	}

	private static String systemErrorText(ChatMessage message) {
		if (message.role() != ChatMessage.Role.SYSTEM) {
			return null;
		}
		String text = message.text();
		if (text == null || text.isBlank()) {
			return null;
		}
		return text;
	}

	private boolean hasMatchingToolResultAfter(int requestIndex, String toolCallId, List<ChatMessage> history) {
		if (toolCallId == null) {
			return false;
		}
		for (int i = requestIndex + 1; i < history.size(); i++) {
			ChatMessage historyMessage = history.get(i);
			if (historyMessage.role() == ChatMessage.Role.TOOL_RESULT && toolCallId.equals(
					historyMessage.toolCallId())) {
				return true;
			}
		}
		return false;
	}

	private void appendToolCallPanel(ChatMessage message) {
		int index = Math.max(0, messagesPanel.getComponentCount() - 1);
		messagesPanel.add(ToolCallPanel.fromHistory(message), index);
		messagesPanel.revalidate();
		messagesPanel.repaint();
		updateContentView();
		SwingUtilities.invokeLater(this::scrollToBottom);
	}

	private JPanel appendMessageBubble(ChatBubble bubble, ChatMessage.Role role) {
		JPanel row = new JPanel() {
			@Override public Dimension getMaximumSize() {
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setOpaque(false);

		if (role.rightAligned()) {
			row.add(Box.createHorizontalStrut(SIDE_GAP_WIDE));
			row.add(Box.createHorizontalGlue());
			row.add(bubble);
			row.add(Box.createHorizontalStrut(SIDE_GAP_NARROW));
		} else if (role.constrainedBubbleWidth()) {
			row.add(Box.createHorizontalStrut(SIDE_GAP_NARROW));
			row.add(bubble);
			row.add(Box.createHorizontalGlue());
			row.add(Box.createHorizontalStrut(SIDE_GAP_WIDE));
		} else {
			row.add(Box.createHorizontalStrut(SIDE_GAP_NARROW));
			row.add(bubble);
			row.add(Box.createHorizontalStrut(SIDE_GAP_NARROW));
		}

		if (role.constrainedBubbleWidth()) {
			// Set initial responsive width for constrained bubbles
			int vpw = messagesScroll.getViewport() != null ? messagesScroll.getViewport().getWidth() : getWidth();
			int target = computeTargetBubbleWidth(vpw);
			bubble.setMaxWidth(target);
		}

		int index = Math.max(0, messagesPanel.getComponentCount() - 1);
		messagesPanel.add(row, index);
		messagesPanel.add(Box.createVerticalStrut(4), index + 1);
		messagesPanel.revalidate();
		messagesPanel.repaint();
		updateContentView();
		SwingUtilities.invokeLater(this::scrollToBottom);
		return row;
	}

	private StreamedAssistantRow appendAssistantBubble() {
		var bubble = new ChatBubble(ChatMessage.assistant(""));
		JPanel row = appendMessageBubble(bubble, ChatMessage.Role.ASSISTANT);
		return new StreamedAssistantRow(bubble, row);
	}

	private void showRunningTool(StreamListener.ToolRequestEvent request) {
		if (activeToolPanels.containsKey(request.id())) {
			return;
		}

		ToolCallPanel panel = ToolCallPanel.running(request);
		int index = Math.max(0, messagesPanel.getComponentCount() - 1);
		messagesPanel.add(panel, index);
		activeToolPanels.put(request.id(), panel);
		messagesPanel.revalidate();
		messagesPanel.repaint();
		updateContentView();
		SwingUtilities.invokeLater(this::scrollToBottom);
	}

	private void finishToolCall(StreamListener.ToolResultEvent result) {
		ToolCallPanel panel = activeToolPanels.remove(result.id());
		if (panel == null) {
			return;
		}
		panel.markCompleted();
		messagesPanel.revalidate();
		messagesPanel.repaint();
	}

	private void hideAllRunningTools() {
		if (activeToolPanels.isEmpty()) {
			return;
		}
		for (ToolCallPanel panel : activeToolPanels.values()) {
			messagesPanel.remove(panel);
		}
		activeToolPanels.clear();
		messagesPanel.revalidate();
		messagesPanel.repaint();
		updateContentView();
	}

	private void appendActivityEvent(String eventText) {
		var text = new JTextArea(eventText);
		text.setEditable(false);
		text.setFocusable(true);
		text.setOpaque(false);
		text.setLineWrap(true);
		text.setWrapStyleWord(true);
		text.setBorder(BorderFactory.createEmptyBorder());
		text.setForeground(Theme.current().getAltForegroundColor());
		text.setCaretColor(new Color(0, 0, 0, 0));
		ComponentUtils.deriveFont(text, 13);

		DefaultCaret caret = (DefaultCaret) text.getCaret();
		caret.setVisible(false);
		caret.setSelectionVisible(true);

		JPanel row = new JPanel(new BorderLayout()) {
			@Override public Dimension getMaximumSize() {
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		row.setOpaque(false);
		row.setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 10));
		row.add(text, BorderLayout.CENTER);

		int index = Math.max(0, messagesPanel.getComponentCount() - 1);
		messagesPanel.add(row, index);
		messagesPanel.revalidate();
		messagesPanel.repaint();
		updateContentView();
		SwingUtilities.invokeLater(this::scrollToBottom);
	}

	private int computeTargetBubbleWidth(int viewportWidth) {
		// Make bubbles take the available width inside the row
		// Subtract row side gaps and a small internal padding of the bubble
		int sideGaps = SIDE_GAP_WIDE + SIDE_GAP_NARROW;
		int target = viewportWidth - sideGaps;
		// Keep a reasonable minimum so layout stays stable on very narrow viewports
		target = Math.max(220, target);
		return target;
	}

	private void applyBubbleWidthIfNeeded() {
		int vpw = messagesScroll.getViewport() != null ? messagesScroll.getViewport().getWidth() : getWidth();
		int target = computeTargetBubbleWidth(vpw);
		if (target == lastAppliedBubbleWidth) {
			return;
		}
		lastAppliedBubbleWidth = target;

		boolean changed = false;
		for (Component c : messagesPanel.getComponents()) {
			if (c instanceof JPanel row) {
				for (Component child : row.getComponents()) {
					if (child instanceof ChatBubble bubble) {
						bubble.setMaxWidth(target);
						changed = true;
					}
				}
			}
		}
		if (changed) {
			messagesPanel.revalidate();
			messagesPanel.repaint();
		}
	}

	private ThinkingHandle showThinkingIndicator() {
		JLabel label = L10N.label("wingman.working");
		label.setForeground(Theme.current().getInterfaceAccentColor());
		ComponentUtils.deriveFont(label, 13);

		JPanel row = new JPanel(new BorderLayout()) {
			@Override public Dimension getMaximumSize() {
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		row.setOpaque(false);
		row.setBorder(BorderFactory.createEmptyBorder(2, 10, 4, 10));
		row.add(label, BorderLayout.CENTER);

		int index = Math.max(0, messagesPanel.getComponentCount() - 1);
		messagesPanel.add(row, index);
		messagesPanel.revalidate();
		messagesPanel.repaint();
		updateContentView();
		scrollToBottom();

		Timer timer = new Timer(350, _ -> {
			String text = label.getText();
			if (text.endsWith("...")) {
				label.setText(L10N.t("wingman.working"));
			} else {
				label.setText(text + ".");
			}
		});
		timer.start();
		return new ThinkingHandle(row, timer);
	}

	private void scrollToBottom() {
		JScrollBar bar = messagesScroll.getVerticalScrollBar();
		bar.setValue(bar.getMaximum());
	}

	private record StreamedAssistantRow(ChatBubble bubble, JPanel row) {
	}

	private final class ThinkingHandle {
		private final JPanel row;
		private final Timer timer;
		private boolean stopped;

		private ThinkingHandle(JPanel row, Timer timer) {
			this.row = row;
			this.timer = timer;
		}

		private void stop() {
			if (stopped) {
				return;
			}
			stopped = true;
			timer.stop();
			messagesPanel.remove(row);
			messagesPanel.revalidate();
			messagesPanel.repaint();
			updateContentView();
		}
	}

}
