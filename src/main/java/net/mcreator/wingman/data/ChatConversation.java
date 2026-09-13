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

package net.mcreator.wingman.data;

import net.mcreator.ui.init.L10N;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ChatConversation {

	private static final int TITLE_MAX_LENGTH = 48;
	public static final String DEFAULT_TITLE = L10N.t("wingman.conversation.new");

	private String title;
	private long updatedAt;
	@Nullable private String sessionId; // optional, set by services that keep conversation state on an external agent

	private final List<ChatMessage> messages = new ArrayList<>();

	private transient String id;

	private transient ChatHistoryStorage storage;
	private transient final Object lock;

	@SuppressWarnings("unused") private ChatConversation() { // for deserialization only
		lock = new Object();
	}

	ChatConversation(ChatHistoryStorage storage, String id, String title, long updatedAt) {
		this.storage = storage;
		this.id = id;
		this.title = title;
		this.updatedAt = updatedAt;
		this.lock = new Object();
	}

	void initializeAfterDeserialization(String id, ChatHistoryStorage storage) {
		this.storage = storage;
		this.id = id;
	}

	public void addMessage(ChatMessage message) {
		synchronized (lock) {
			messages.add(message);
			if (message.role() == ChatMessage.Role.USER && DEFAULT_TITLE.equals(title)) {
				title = deriveTitle(message.text());
			}
			updatedAt = System.currentTimeMillis();
		}
		persist();
	}

	public void updateLastAssistantThinkingDuration(int thinkingDurationSeconds) {
		boolean updated = false;
		synchronized (lock) {
			for (int i = messages.size() - 1; i >= 0; i--) {
				ChatMessage message = messages.get(i);
				if (message.role() == ChatMessage.Role.ASSISTANT) {
					messages.set(i, ChatMessage.assistant(message.initialText(), thinkingDurationSeconds));
					updatedAt = System.currentTimeMillis();
					updated = true;
					break;
				}
			}
		}
		if (updated) {
			persist();
		}
	}

	public String id() {
		return id;
	}

	public String title() {
		synchronized (lock) {
			return title;
		}
	}

	@Nullable public String sessionId() {
		synchronized (lock) {
			return sessionId;
		}
	}

	public void setSessionId(@Nullable String sessionId) {
		synchronized (lock) {
			this.sessionId = sessionId;
		}
		persist();
	}

	public List<ChatMessage> messages() {
		synchronized (lock) {
			return List.copyOf(sanitizeOrphanedToolCalls(messages));
		}
	}

	public boolean isEmpty() {
		synchronized (lock) {
			return messages.isEmpty();
		}
	}

	public Summary toSummary() {
		synchronized (lock) {
			return new Summary(id, title, updatedAt);
		}
	}

	public record Summary(String id, String title, long updatedAt) {}

	private void persist() {
		if (storage != null) {
			storage.save(this);
		}
	}

	private static String deriveTitle(String text) {
		if (text == null) {
			return DEFAULT_TITLE;
		}
		String normalized = text.trim().replaceAll("\\s+", " ");
		if (normalized.isEmpty()) {
			return DEFAULT_TITLE;
		}
		if (normalized.length() <= TITLE_MAX_LENGTH) {
			return normalized;
		}
		return normalized.substring(0, TITLE_MAX_LENGTH - 1).trim() + "…";
	}

	private static List<ChatMessage> sanitizeOrphanedToolCalls(List<ChatMessage> messages) {
		Set<String> toolResults = new HashSet<>();

		for (ChatMessage message : messages) {
			if (message.role() == ChatMessage.Role.TOOL_RESULT && message.toolCallId() != null) {
				toolResults.add(message.toolCallId());
			}
		}

		List<ChatMessage> sanitized = new ArrayList<>(messages.size());

		for (ChatMessage message : messages) {
			if (message.role() == ChatMessage.Role.TOOL_REQUEST || message.role() == ChatMessage.Role.TOOL_RESULT) {
				if (message.toolCallId() != null && toolResults.contains(message.toolCallId())) {
					sanitized.add(message);
				}
			} else {
				sanitized.add(message);
			}
		}

		return sanitized;
	}

}
