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

import com.google.gson.*;
import net.mcreator.io.mcp.McpServer;
import net.mcreator.io.mcp.tool.IMcpTool;

import javax.annotation.Nullable;
import java.util.*;

public final class McpToolCallMatcher implements McpServer.ToolCallListener {

	private static final Gson gson = new Gson();

	private final Deque<CapturedResult> results = new ArrayDeque<>();

	@Override
	public synchronized void onToolResult(IMcpTool tool, JsonObject arguments, @Nullable JsonElement result,
			@Nullable Throwable error) {
		CapturedResult captured = new CapturedResult(tool.getName(), canonicalize(arguments),
				resultText(result, error));
		results.addLast(captured);
	}

	/**
	 * Drops the recorded results. Called when a prompt starts and ends, as results can only be matched to the calls
	 * of the running prompt: anything left over belongs to a call the agent never reported as completed
	 */
	public synchronized void clear() {
		results.clear();
	}

	/**
	 * Takes the oldest recorded MCP call matching the given tool call reported over ACP.
	 *
	 * @param title     Name of the call as the agent reports it, expected to contain the MCP tool name
	 * @param arguments Raw input of the call as JSON text
	 * @return The MCP tool called and the text of the result it produced, null if no recorded call matches, e.g.
	 * because the call was not made to the MCreator MCP server
	 */
	@Nullable public synchronized Match take(String title, @Nullable String arguments) {
		Set<String> candidateArguments = candidateArguments(parse(arguments));
		String titleKey = alphanumeric(title);
		Iterator<CapturedResult> iterator = results.iterator();
		while (iterator.hasNext()) {
			CapturedResult captured = iterator.next();
			if (candidateArguments.contains(captured.arguments) && titleKey.contains(alphanumeric(captured.toolName))) {
				iterator.remove();
				return new Match(captured.toolName, captured.text);
			}
		}
		return null;
	}

	/**
	 * @param toolName Name of the MCP tool that was called
	 * @param result   Text of the result the tool produced
	 */
	public record Match(String toolName, String result) {
	}

	// Agents format the title of an MCP call differently (e.g. mcp__mcreator__query_workspace, "query_workspace
	// (mcreator)", "mcreator: query workspace"), so only letters and digits are compared
	private static String alphanumeric(String text) {
		return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}

	@Nullable private static JsonElement parse(@Nullable String json) {
		if (json == null || json.isBlank()) {
			return null;
		}
		try {
			return JsonParser.parseString(json);
		} catch (RuntimeException e) {
			return null;
		}
	}

	/**
	 * @return Canonical forms the input of an ACP tool call may take: the input itself, and each of its object members,
	 * as some agents wrap the arguments of an MCP call (e.g. Cursor reports {providerIdentifier, toolName, args})
	 */
	private static Set<String> candidateArguments(@Nullable JsonElement input) {
		Set<String> candidates = new HashSet<>();
		candidates.add(canonicalize(input));
		if (input != null && input.isJsonObject()) {
			for (JsonElement member : input.getAsJsonObject().asMap().values()) {
				if (member.isJsonObject()) {
					candidates.add(canonicalize(member));
				}
			}
		}
		return candidates;
	}

	/**
	 * @return The given JSON with object members sorted by name and null members dropped, so arguments compare equal
	 * regardless of how the agent serialized them. Missing or non-object input compares as no arguments.
	 */
	static String canonicalize(@Nullable JsonElement json) {
		if (json == null || !json.isJsonObject()) {
			return "{}";
		}
		return gson.toJson(sorted(json));
	}

	private static JsonElement sorted(JsonElement json) {
		if (json.isJsonObject()) {
			Map<String, JsonElement> members = new TreeMap<>();
			for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
				if (!entry.getValue().isJsonNull()) {
					members.put(entry.getKey(), sorted(entry.getValue()));
				}
			}
			JsonObject object = new JsonObject();
			members.forEach(object::add);
			return object;
		}
		if (json.isJsonArray()) {
			JsonArray array = new JsonArray();
			for (JsonElement element : json.getAsJsonArray()) {
				array.add(sorted(element));
			}
			return array;
		}
		return json;
	}

	/**
	 * @return Text of the tool result as the agent sees it: the text content blocks of the response, or the failure
	 */
	static String resultText(@Nullable JsonElement result, @Nullable Throwable error) {
		if (error != null) {
			return error.getClass().getSimpleName() + ": " + error.getMessage();
		}
		if (result == null) {
			return "";
		}
		StringBuilder text = new StringBuilder();
		if (result.isJsonObject() && result.getAsJsonObject().get("content") instanceof JsonElement content
				&& content.isJsonArray()) {
			for (JsonElement block : content.getAsJsonArray()) {
				if (block.isJsonObject() && block.getAsJsonObject().get("text") instanceof JsonElement blockText
						&& blockText.isJsonPrimitive()) {
					if (!text.isEmpty()) {
						text.append('\n');
					}
					text.append(blockText.getAsString());
				}
			}
		}
		if (text.isEmpty()) {
			text.append(gson.toJson(result));
		}
		return text.toString();
	}

	private record CapturedResult(String toolName, String arguments, String text) {
	}

}
