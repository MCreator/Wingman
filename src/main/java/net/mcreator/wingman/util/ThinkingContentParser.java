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

package net.mcreator.wingman.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ThinkingContentParser {

	private static final Pattern OPEN_TAG = Pattern.compile("<thinking\\s*>", Pattern.CASE_INSENSITIVE);
	private static final Pattern CLOSE_TAG = Pattern.compile("</thinking\\s*>", Pattern.CASE_INSENSITIVE);

	public record ParsedContent(String thinking, String response, boolean thinkingInProgress) {
		static ParsedContent empty() {
			return new ParsedContent("", "", false);
		}
	}

	public static ParsedContent parse(String raw, boolean finalizeOpenThinking) {
		if (raw == null || raw.isEmpty()) {
			return ParsedContent.empty();
		}

		String text = stripTrailingPartialTag(raw);
		StringBuilder thinking = new StringBuilder();
		StringBuilder response = new StringBuilder();
		boolean thinkingInProgress = false;

		int pos = 0;
		while (pos < text.length()) {
			Matcher openMatcher = OPEN_TAG.matcher(text);
			openMatcher.region(pos, text.length());
			if (!openMatcher.find()) {
				response.append(text.substring(pos));
				break;
			}

			response.append(text, pos, openMatcher.start());
			int contentStart = openMatcher.end();

			Matcher closeMatcher = CLOSE_TAG.matcher(text);
			closeMatcher.region(contentStart, text.length());
			if (!closeMatcher.find()) {
				appendThinkingSegment(thinking, text.substring(contentStart));
				thinkingInProgress = true;
				break;
			}

			appendThinkingSegment(thinking, text.substring(contentStart, closeMatcher.start()));
			pos = closeMatcher.end();
		}

		if (finalizeOpenThinking && thinkingInProgress) {
			thinkingInProgress = false;
		}

		return new ParsedContent(thinking.toString().trim(), response.toString().stripLeading(), thinkingInProgress);
	}

	private static void appendThinkingSegment(StringBuilder thinking, String segment) {
		if (segment.isEmpty()) {
			return;
		}
		if (!thinking.isEmpty()) {
			thinking.append('\n');
		}
		thinking.append(segment);
	}

	private static String stripTrailingPartialTag(String text) {
		int lastLt = text.lastIndexOf('<');
		if (lastLt < 0) {
			return text;
		}

		String tail = text.substring(lastLt);
		if (isIncompleteThinkingTag(tail)) {
			return text.substring(0, lastLt);
		}
		return text;
	}

	private static boolean isIncompleteThinkingTag(String tail) {
		String normalized = tail.toLowerCase().replaceAll("\\s+", "");
		if (normalized.equals("<thinking>") || normalized.equals("</thinking>")) {
			return false;
		}
		return "<thinking>".startsWith(normalized) || "</thinking>".startsWith(normalized);
	}
}
