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
import net.mcreator.ui.laf.themes.Theme;

import javax.annotation.Nullable;
import java.awt.*;

public record ChatMessage(Role role, String initialText, @Nullable Integer thinkingDurationSeconds, String toolCallId,
                          String toolName, String toolArguments, String toolResult) {

	public ChatMessage(Role role, String initialText) {
		this(role, initialText, null, null, null, null, null);
	}

	public static ChatMessage user(String text) {
		return new ChatMessage(Role.USER, text);
	}

	public static ChatMessage assistant(String text) {
		return new ChatMessage(Role.ASSISTANT, text);
	}

	public static ChatMessage thought(String text, @Nullable Integer thinkingDurationSeconds) {
		return new ChatMessage(Role.THOUGHT, text, thinkingDurationSeconds, null, null, null, null);
	}

	public static ChatMessage toolRequest(String toolCallId, String toolName, String toolArguments) {
		return new ChatMessage(Role.TOOL_REQUEST, "", null, toolCallId, toolName, toolArguments, null);
	}

	public static ChatMessage toolResult(String toolCallId, String toolName, String toolResult) {
		return new ChatMessage(Role.TOOL_RESULT, "", null, toolCallId, toolName, null, toolResult);
	}

	public String text() {
		return initialText;
	}

	public ChatMessage withToolName(String toolName) {
		return new ChatMessage(role, initialText, thinkingDurationSeconds, toolCallId, toolName, toolArguments,
				toolResult);
	}

	public ChatMessage withToolArguments(String toolArguments) {
		return new ChatMessage(role, initialText, thinkingDurationSeconds, toolCallId, toolName, toolArguments,
				toolResult);
	}

	public static ChatMessage system(String text) {
		return new ChatMessage(Role.SYSTEM, text);
	}

	public static ChatMessage requestFailed(@Nullable String reason) {
		if (reason == null || reason.isBlank() || "null".equals(reason.trim())) {
			return system(L10N.t("wingman.request.failed"));
		}
		return system(L10N.t("wingman.request.failed_reason", reason.trim()));
	}

	public enum Role {
		//@formatter:off
		USER(Theme.current().getInterfaceAccentColor(), Theme.current().getForegroundColor(), true, false, true),
		ASSISTANT(Theme.current().getAltBackgroundColor(), Theme.current().getForegroundColor(), false, true, false),
		THOUGHT(Theme.current().getAltBackgroundColor(), Theme.current().getForegroundColor(), false, true, false),
		SYSTEM(Theme.current().getAltBackgroundColor(), Theme.current().getForegroundColor(), false, true, false),
		TOOL_REQUEST(Theme.current().getAltBackgroundColor(), Theme.current().getForegroundColor(), false, true, false),
		TOOL_RESULT(Theme.current().getAltBackgroundColor(), Theme.current().getForegroundColor(), false, true, false);
		//@formatter:on

		private final Color bubbleBackground;
		private final Color bubbleForeground;
		private final boolean rightAligned;
		private final boolean filledBubble;
		private final boolean constrainedBubbleWidth;

		Role(Color bubbleBackground, Color bubbleForeground, boolean rightAligned, boolean filledBubble,
				boolean constrainedBubbleWidth) {
			this.bubbleBackground = bubbleBackground;
			this.bubbleForeground = bubbleForeground;
			this.rightAligned = rightAligned;
			this.filledBubble = filledBubble;
			this.constrainedBubbleWidth = constrainedBubbleWidth;
		}

		public Color bubbleBackground() {
			return bubbleBackground;
		}

		public Color bubbleForeground() {
			return bubbleForeground;
		}

		public boolean rightAligned() {
			return rightAligned;
		}

		public boolean filledBubble() {
			return filledBubble;
		}

		public boolean constrainedBubbleWidth() {
			return constrainedBubbleWidth;
		}
	}
}
