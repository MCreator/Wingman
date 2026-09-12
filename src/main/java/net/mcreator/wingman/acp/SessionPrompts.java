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

import net.mcreator.wingman.data.ChatMessage;
import net.mcreator.wingman.util.ThinkingContentParser;
import net.mcreator.util.StringUtils;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Builds the first prompt of an ACP session. ACP has no system prompt, so the agent is told its purpose at the
 * start of the first prompt, followed by the local history of the conversation if the agent has no context of it yet.
 */
final class SessionPrompts {

	private static final String SESSION_INSTRUCTIONS = """
			You are MCreator Wingman, an assistant running inside MCreator, a Minecraft mod maker. The working directory \
			is an MCreator workspace of the mod the user is building. Your purpose is to help the user with Minecraft \
			modding: understanding and creating mod elements, textures, procedures, recipes, and other mod content, \
			and diagnosing problems in this workspace. Stay focused on Minecraft modding and this workspace, and \
			operate only inside the working directory.""";

	// Added to the instructions when the local history of a conversation is relayed to a fresh session
	private static final String HISTORY_INSTRUCTIONS = """
			The conversation in the conversation_history block below took place before this session was started, so \
			it is not part of your context yet. Treat it as the earlier part of this chat and continue from it.""";

	// Tags delimiting the parts prepended to the first prompt of a session, so they can be recognized on replay
	private static final String INSTRUCTIONS_OPEN = "<instructions>\n";
	private static final String INSTRUCTIONS_CLOSE = "\n</instructions>\n\n";
	private static final String HISTORY_OPEN = "<conversation_history>\n";
	private static final String HISTORY_CLOSE = "\n</conversation_history>\n\n";

	// Tool arguments and results in the relayed history are cut to this many characters
	private static final int RELAY_TOOL_TEXT_LIMIT = 2000;

	/**
	 * @param history     Messages of the conversation recorded before this prompt, relayed as a transcript
	 * @param messageText The new message of the user
	 */
	static String firstPrompt(List<ChatMessage> history, String messageText) {
		String transcript = toTranscript(history);
		StringBuilder prompt = new StringBuilder(INSTRUCTIONS_OPEN).append(SESSION_INSTRUCTIONS);
		if (!transcript.isEmpty()) {
			prompt.append("\n\n").append(HISTORY_INSTRUCTIONS);
		}
		prompt.append(INSTRUCTIONS_CLOSE);
		if (!transcript.isEmpty()) {
			prompt.append(HISTORY_OPEN).append(transcript).append(HISTORY_CLOSE);
		}
		return prompt.append(messageText).toString();
	}

	private static String toTranscript(List<ChatMessage> history) {
		StringBuilder transcript = new StringBuilder();
		for (ChatMessage message : history) {
			String entry = switch (message.role()) {
				case USER -> "User: " + message.text();
				case ASSISTANT -> {
					// Thoughts are not relayed, only the response shown to the user
					String response = ThinkingContentParser.parse(message.text(), true).response();
					yield response.isBlank() ? null : "Assistant: " + response;
				}
				case TOOL_REQUEST -> "Tool call " + message.toolName() + ": " + abbreviate(message.toolArguments());
				case TOOL_RESULT -> "Tool result " + message.toolName() + ": " + abbreviate(message.toolResult());
				case SYSTEM -> null; // UI-only messages are not relayed
			};
			if (entry != null) {
				if (!transcript.isEmpty()) {
					transcript.append("\n\n");
				}
				transcript.append(entry);
			}
		}
		return transcript.toString();
	}

	private static String abbreviate(@Nullable String text) {
		return text == null ? "" : StringUtils.abbreviateString(text, RELAY_TOOL_TEXT_LIMIT);
	}

	/**
	 * Removes the instructions and the relayed history from the text of a replayed user message, so that the first
	 * prompt of a session is recorded locally the way the user typed it.
	 */
	static String stripPreamble(String userText) {
		if (!userText.startsWith(INSTRUCTIONS_OPEN)) {
			return userText;
		}
		int end = userText.indexOf(INSTRUCTIONS_CLOSE);
		if (end < 0) {
			return userText;
		}
		end += INSTRUCTIONS_CLOSE.length();
		if (userText.startsWith(HISTORY_OPEN, end)) {
			int historyEnd = userText.indexOf(HISTORY_CLOSE, end);
			if (historyEnd >= 0) {
				end = historyEnd + HISTORY_CLOSE.length();
			}
		}
		return userText.substring(end);
	}

}
