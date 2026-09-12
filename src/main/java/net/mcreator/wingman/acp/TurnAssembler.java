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

import javax.annotation.Nullable;
import java.util.*;

/**
 * Assembles the session updates of agent turns. Text and thoughts are accumulated into one response text with
 * thoughts wrapped in thinking tags, and each tool call is reported once when it starts and once when it finishes.
 */
abstract class TurnAssembler {

	private static final Gson GSON = new Gson();

	private final StringBuilder responseBuilder = new StringBuilder();
	private final Map<String, String> toolNames = new HashMap<>();
	private final Set<String> finishedTools = new HashSet<>();

	private boolean thinking;

	void handle(AcpSchema.SessionUpdate update) {
		switch (update) {
		case AcpSchema.AgentThoughtChunk chunk -> appendText(chunk.content(), true);
		case AcpSchema.AgentMessageChunk chunk -> appendText(chunk.content(), false);
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
	 * Called with the whole response text accumulated so far whenever it grows
	 */
	abstract void onResponseText(String responseText);

	abstract void onToolRequest(String id, String name, String arguments);

	abstract void onToolResult(String id, String name, String result);

	/**
	 * Closes an open thought and returns the response text accumulated so far, starting a new response
	 */
	String finishResponse() {
		if (thinking) {
			responseBuilder.append("</thinking>");
			thinking = false;
		}
		String text = responseBuilder.toString();
		responseBuilder.setLength(0);
		return text;
	}

	private void appendText(@Nullable AcpSchema.ContentBlock content, boolean thought) {
		if (!(content instanceof AcpSchema.TextContent text) || text.text() == null || text.text().isEmpty()) {
			return;
		}
		// Thoughts are wrapped in thinking tags so the chat bubble shows them in its thinking panel
		if (thought && !thinking) {
			responseBuilder.append("<thinking>");
			thinking = true;
		} else if (!thought && thinking) {
			responseBuilder.append("</thinking>\n");
			thinking = false;
		}
		responseBuilder.append(text.text());
		onResponseText(responseBuilder.toString());
	}

	private void onToolCall(@Nullable String id, @Nullable String title, @Nullable AcpSchema.ToolKind kind,
			@Nullable AcpSchema.ToolCallStatus status, @Nullable List<AcpSchema.ToolCallContent> content,
			@Nullable Object rawInput, @Nullable Object rawOutput) {
		if (id == null) {
			return;
		}
		String name = toolName(id, title, kind);
		if (!toolNames.containsKey(id)) {
			toolNames.put(id, name);
			onToolRequest(id, name, toJson(rawInput));
		}
		if ((status == AcpSchema.ToolCallStatus.COMPLETED || status == AcpSchema.ToolCallStatus.FAILED)
				&& finishedTools.add(id)) {
			onToolResult(id, name, toolResultText(content, rawOutput, status));
		}
	}

	String toolName(String id, @Nullable String title, @Nullable AcpSchema.ToolKind kind) {
		String known = toolNames.get(id);
		if (known != null) {
			return known;
		}
		if (title != null && !title.isBlank()) {
			return title.trim();
		}
		return kind != null ? kind.name().toLowerCase(Locale.ROOT) : "tool";
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
