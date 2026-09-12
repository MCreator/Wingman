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
import net.mcreator.wingman.data.ChatMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects the history an agent replays while loading a session into chat messages of the same shape as the
 * ones recorded from live turns: tool calls as they happen, the response text once the turn ends.
 */
final class HistoryReplay extends TurnAssembler {

	private final List<ChatMessage> messages = new ArrayList<>();
	private final StringBuilder userBuilder = new StringBuilder();

	@Override synchronized void handle(AcpSchema.SessionUpdate update) {
		if (update instanceof AcpSchema.UserMessageChunk chunk) {
			finishAssistantMessage();
			if (chunk.content() instanceof AcpSchema.TextContent text && text.text() != null) {
				userBuilder.append(text.text());
			}
		} else {
			finishUserMessage();
			super.handle(update);
		}
	}

	@Override void onResponseText(String responseText) {
		// The response is recorded once the turn ends
	}

	@Override void onToolRequest(String id, String name, String arguments) {
		messages.add(ChatMessage.toolRequest(id, name, arguments));
	}

	@Override void onToolResult(String id, String name, String result) {
		messages.add(ChatMessage.toolResult(id, name, result));
	}

	synchronized List<ChatMessage> messages() {
		finishUserMessage();
		finishAssistantMessage();
		return List.copyOf(messages);
	}

	private void finishUserMessage() {
		if (!userBuilder.isEmpty()) {
			String text = SessionPrompts.stripPreamble(userBuilder.toString());
			if (!text.isBlank()) {
				messages.add(ChatMessage.user(text));
			}
			userBuilder.setLength(0);
		}
	}

	private void finishAssistantMessage() {
		String text = finishResponse();
		if (!text.isEmpty()) {
			messages.add(ChatMessage.assistant(text));
		}
	}

}
