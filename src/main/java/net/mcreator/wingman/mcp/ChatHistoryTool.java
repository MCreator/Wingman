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

package net.mcreator.wingman.mcp;

import net.mcreator.io.mcp.protocol.McpSchema;
import net.mcreator.io.mcp.protocol.SchemaDescription;
import net.mcreator.io.mcp.tool.McpTool;
import net.mcreator.io.mcp.tool.ToolResult;
import net.mcreator.wingman.acp.SessionPrompts;
import net.mcreator.wingman.data.ChatConversation;
import net.mcreator.wingman.data.ChatHistoryStorage;

import javax.annotation.Nullable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class ChatHistoryTool extends McpTool<ChatHistoryTool.Args> {

	public static final String NAME = "read_chat_history";

	public static class Args {
		@SchemaDescription("ID of the conversation to read. Omit to list the available conversations instead.")
		@Nullable public String conversationId;
		@SchemaDescription("Optional Java regex filter on conversation titles when listing conversations.") @Nullable
		public String filter;
		@SchemaDescription("When reading a conversation, only the last N characters of the transcript are returned.")
		public int lastNCharsToRead = 30000;
	}

	public record ConversationInfo(String id, String title, String updatedAt) {
	}

	private final ChatHistoryStorage historyStorage;

	public ChatHistoryTool(ChatHistoryStorage historyStorage) {
		super(Args.class);
		this.historyStorage = historyStorage;
	}

	@Override public String getName() {
		return NAME;
	}

	@Override public String getDescription() {
		return """
				Reads earlier Wingman conversations the user had in this workspace. Without conversationId, lists \
				the conversations with their IDs and titles, newest first. The first entry is the conversation \
				you are in right now, which is already in your context. With conversationId, returns the \
				transcript of that conversation. Use it when the user refers to something discussed in an \
				earlier chat.""";
	}

	@Override public McpSchema.ToolAnnotations getAnnotations() {
		return new McpSchema.ToolAnnotations(null, true, false);
	}

	@Override protected CompletableFuture<ToolResult> call(Args input) {
		try {
			if (input.conversationId == null || input.conversationId.isBlank()) {
				return listConversations(input.filter);
			}
			return readConversation(input.conversationId.trim(), input.lastNCharsToRead);
		} catch (RuntimeException e) {
			return completedError("Failed to read chat history: " + e.getMessage(), e);
		}
	}

	private CompletableFuture<ToolResult> listConversations(@Nullable String filter) {
		Pattern pattern;
		try {
			pattern = filter == null || filter.isBlank() ? null : Pattern.compile(filter, Pattern.CASE_INSENSITIVE);
		} catch (PatternSyntaxException e) {
			return completedError("Invalid filter regex: " + e.getMessage());
		}

		List<ConversationInfo> conversations = historyStorage.listSummaries().stream()
				.filter(summary -> pattern == null || pattern.matcher(summary.title()).find())
				.map(summary -> new ConversationInfo(summary.id(), summary.title(),
						formatTimestamp(summary.updatedAt()))).toList();
		return completed(ToolResult.collection(conversations));
	}

	private CompletableFuture<ToolResult> readConversation(String conversationId, int lastNCharsToRead) {
		ChatConversation conversation = historyStorage.load(conversationId);
		if (conversation == null) {
			return completedError("No conversation with ID " + conversationId
					+ " exists. Call this tool without conversationId to list the available conversations.");
		}

		String transcript = SessionPrompts.toTranscript(conversation.messages());
		if (transcript.isEmpty()) {
			return completedText("The conversation \"" + conversation.title() + "\" has no messages.");
		}

		StringBuilder result = new StringBuilder("Conversation \"").append(conversation.title()).append("\"\n\n");
		int omitted = transcript.length() - Math.max(0, lastNCharsToRead);
		if (omitted > 0) {
			result.append("[first ").append(omitted).append(" characters omitted]\n");
			transcript = transcript.substring(omitted);
		}
		return completedText(result.append(transcript).toString());
	}

	private static String formatTimestamp(long epochMillis) {
		return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(
				Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS));
	}

}
