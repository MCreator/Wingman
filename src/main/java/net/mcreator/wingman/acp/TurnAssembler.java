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

package net.mcreator.wingman.acp;

import com.agentclientprotocol.sdk.spec.AcpSchema;
import com.google.gson.Gson;
import net.mcreator.wingman.data.ChatMessage;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Assembles the session updates of agent turns into messages. Consecutive thought chunks form one thought message and
 * consecutive text chunks one assistant message; such a message is reported while it grows and finished when a chunk
 * of another type or a new tool call arrives, so the messages of a turn are reported in the order the agent produced
 * them. Each tool call is reported once when it starts and once when it finishes. A tool call is named after the
 * title the agent reports for it. Agents may report a placeholder title before they have parsed the call input (e.g.
 * Cursor reports MCP calls as "MCP: tool" while their arguments are still being streamed) and the real one only
 * later, e.g. with the permission request, so a later title renames the call.
 */
abstract class TurnAssembler {

	private static final Gson GSON = new Gson();

	private final Map<String, String> toolNames = new HashMap<>();

	// Kinds of the tool calls seen so far, as a permission request for a call may come without one
	private final Map<String, AcpSchema.ToolKind> toolKinds = new HashMap<>();
	private final Set<String> finishedTools = new HashSet<>();

	// The thought or assistant message currently being streamed, if any
	@Nullable private ChatMessage.Role messageRole;
	private final StringBuilder messageText = new StringBuilder();
	private long messageStartedAtMs;

	void handle(AcpSchema.SessionUpdate update) {
		switch (update) {
		case AcpSchema.AgentThoughtChunk chunk -> appendText(chunk.content(), ChatMessage.Role.THOUGHT);
		case AcpSchema.AgentMessageChunk chunk -> appendText(chunk.content(), ChatMessage.Role.ASSISTANT);
		case AcpSchema.ToolCall call ->
				onToolCall(call.toolCallId(), call.title(), call.kind(), call.status(), call.content(), call.rawInput(),
						call.rawOutput());
		case AcpSchema.ToolCallUpdateNotification call ->
				onToolCall(call.toolCallId(), call.title(), call.kind(), call.status(), call.content(), call.rawInput(),
						call.rawOutput());
		default -> {
			// Plans, mode changes, and available commands are not shown in the UI
		}
		}
	}

	/**
	 * Called with the message being streamed whenever its text grows
	 */
	abstract void onPartialMessage(ChatMessage message);

	/**
	 * Called with the final form of a message reported partially before
	 */
	abstract void onMessageFinished(ChatMessage message);

	/**
	 * @param kind Kind of the tool as reported by the agent, may be null
	 */
	abstract void onToolRequest(String id, String name, @Nullable AcpSchema.ToolKind kind, String arguments);

	/**
	 * Called when a tool call already reported as requested gets a different title from the agent
	 */
	abstract void onToolRenamed(String id, String name);

	/**
	 * @param kind Kind of the tool as reported by the agent, may be null
	 */
	abstract void onToolResult(String id, String name, @Nullable AcpSchema.ToolKind kind, String result);

	/**
	 * Finishes the message being streamed, if any. Messages without text are dropped without being reported.
	 */
	void finishMessage() {
		if (messageRole == null) {
			return;
		}
		ChatMessage message = currentMessage(true);
		messageRole = null;
		messageText.setLength(0);
		if (!message.text().isBlank()) {
			onMessageFinished(message);
		}
	}

	private void appendText(@Nullable AcpSchema.ContentBlock content, ChatMessage.Role role) {
		if (!(content instanceof AcpSchema.TextContent text) || text.text() == null || text.text().isEmpty()) {
			return;
		}
		if (messageRole != role) {
			finishMessage();
			messageRole = role;
			messageStartedAtMs = System.currentTimeMillis();
		}
		messageText.append(text.text());
		if (!messageText.toString().isBlank()) {
			onPartialMessage(currentMessage(false));
		}
	}

	private ChatMessage currentMessage(boolean finished) {
		String text = finished ? messageText.toString().strip() : messageText.toString();
		if (messageRole == ChatMessage.Role.THOUGHT) {
			long elapsedMs = System.currentTimeMillis() - messageStartedAtMs;
			return ChatMessage.thought(text, finished ? thinkingDurationSeconds(elapsedMs) : null);
		}
		return ChatMessage.assistant(text);
	}

	private static int thinkingDurationSeconds(long durationMs) {
		return Math.max(1, (int) Math.ceil(durationMs / 1000.0));
	}

	private void onToolCall(@Nullable String id, @Nullable String title, @Nullable AcpSchema.ToolKind kind,
			@Nullable AcpSchema.ToolCallStatus status, @Nullable List<AcpSchema.ToolCallContent> content,
			@Nullable Object rawInput, @Nullable Object rawOutput) {
		if (id == null) {
			return;
		}
		String name = toolName(id, title, kind);
		if (kind != null) {
			toolKinds.putIfAbsent(id, kind);
		}
		AcpSchema.ToolKind knownKind = toolKind(id, kind);
		if (!toolNames.containsKey(id)) {
			finishMessage(); // so the text streamed after this call is shown after it
			toolNames.put(id, name);
			onToolRequest(id, name, knownKind, toJson(rawInput));
		}
		if ((status == AcpSchema.ToolCallStatus.COMPLETED || status == AcpSchema.ToolCallStatus.FAILED)
				&& finishedTools.add(id)) {
			onToolResult(id, name, knownKind, toolResultText(content, rawOutput, status));
		}
	}

	/**
	 * @return Name of the tool call: the given title, or the kind if there is no title. A call already reported
	 * with a different name is renamed to the given title, as the latest title is the most descriptive one
	 */
	String toolName(String id, @Nullable String title, @Nullable AcpSchema.ToolKind kind) {
		String reported = title != null && !title.isBlank() ? title.trim() : null;
		String known = toolNames.get(id);
		if (known == null) {
			return reported != null ? reported : kind != null ? kind.name().toLowerCase(Locale.ROOT) : "tool";
		}
		if (reported != null && !reported.equals(known)) {
			toolNames.put(id, reported);
			onToolRenamed(id, reported);
			return reported;
		}
		return known;
	}

	/**
	 * @return The given kind, or the kind the tool call was reported with earlier if this one is missing
	 */
	@Nullable AcpSchema.ToolKind toolKind(String id, @Nullable AcpSchema.ToolKind kind) {
		return kind != null ? kind : toolKinds.get(id);
	}

	private static String toolResultText(@Nullable List<AcpSchema.ToolCallContent> content, @Nullable Object rawOutput,
			AcpSchema.ToolCallStatus status) {
		StringBuilder result = new StringBuilder();
		if (content != null) {
			for (AcpSchema.ToolCallContent block : content) {
				String text = switch (block) {
					case AcpSchema.ToolCallContentBlock contentBlock ->
							contentBlock.content() instanceof AcpSchema.TextContent textContent ?
									textContent.text() :
									null;
					case AcpSchema.ToolCallDiff diff -> diff.path();
					default -> null;
				};
				if (text != null && !text.isBlank()) {
					if (!result.isEmpty()) {
						result.append('\n');
					}
					result.append(text);
				}
			}
		}
		if (result.isEmpty()) {
			result.append(toJson(rawOutput));
		}
		if (result.isEmpty()) {
			result.append(status.name().toLowerCase(Locale.ROOT));
		}
		return result.toString();
	}

	/**
	 * @return Raw tool input or output as text: strings as they are, everything else as JSON, null as empty text
	 */
	static String toJson(@Nullable Object value) {
		if (value == null) {
			return "";
		}
		if (value instanceof String string) {
			return string;
		}
		return GSON.toJson(value);
	}

}
