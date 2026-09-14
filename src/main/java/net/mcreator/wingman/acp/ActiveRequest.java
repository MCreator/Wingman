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
import net.mcreator.wingman.data.ChatConversation;
import net.mcreator.wingman.data.ChatMessage;
import net.mcreator.wingman.data.StreamListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * State of the prompt currently running for a conversation. Session updates are recorded in the conversation and
 * forwarded to the stream listener. All session updates and cancellation synchronize on this object, so once
 * canceled, no further listener events are delivered for this request.
 */
final class ActiveRequest extends TurnAssembler {

	private static final Logger LOG = LogManager.getLogger(ActiveRequest.class);

	private final ChatConversation conversation;
	private final StreamListener listener;

	private boolean cancelled;

	ActiveRequest(ChatConversation conversation, StreamListener listener) {
		this.conversation = conversation;
		this.listener = listener;
	}

	ChatConversation conversation() {
		return conversation;
	}

	@Override synchronized void handle(AcpSchema.SessionUpdate update) {
		if (cancelled) {
			return;
		}
		super.handle(update);
	}

	@Override void onResponseText(String responseText) {
		listener.onPartialResponse(responseText);
	}

	@Override void onToolRequest(String id, String name, @Nullable AcpSchema.ToolKind kind, String arguments) {
		conversation.addMessage(ChatMessage.toolRequest(id, name, arguments));
		listener.onToolRequest(new StreamListener.ToolRequestEvent(id, name, toKind(kind), arguments));
	}

	@Override void onToolResult(String id, String name, @Nullable AcpSchema.ToolKind kind, String result) {
		conversation.addMessage(ChatMessage.toolResult(id, name, result));
		listener.onToolResult(new StreamListener.ToolResultEvent(id, name, toKind(kind), result));
	}

	AcpSchema.RequestPermissionOutcome requestPermission(@Nullable AcpSchema.ToolCallUpdate toolCall,
			List<AcpSchema.PermissionOption> options) {
		Map<StreamListener.PermissionDecision, AcpSchema.PermissionOption> byDecision = new EnumMap<>(
				StreamListener.PermissionDecision.class);
		for (AcpSchema.PermissionOption option : options) {
			StreamListener.PermissionDecision decision = toDecision(option.kind());
			if (decision != null) {
				byDecision.putIfAbsent(decision, option);
			}
		}

		String id = toolCall != null ? toolCall.toolCallId() : null;
		String title = toolCall != null ? toolCall.title() : null;
		String arguments = toolCall != null ? toJson(toolCall.rawInput()) : "";
		String name;
		StreamListener.ToolKind kind;
		synchronized (this) {
			if (cancelled) {
				return new AcpSchema.PermissionCancelled();
			}
			name = id != null ? toolName(id, title, toolCall.kind()) : "tool";
			kind = toKind(id != null ? toolKind(id, toolCall.kind()) : null);
		}

		// The listener may block waiting for the user, so it is called without holding the request lock
		StreamListener.PermissionDecision decision = listener.onPermissionRequest(
				new StreamListener.PermissionRequest(id, name, title != null ? title : name, arguments, kind,
						Set.copyOf(byDecision.keySet())));

		AcpSchema.PermissionOption chosen = decision != null ? byDecision.get(decision) : null;
		if (chosen == null) {
			return new AcpSchema.PermissionCancelled();
		}
		return new AcpSchema.PermissionSelected(chosen.optionId());
	}

	private static StreamListener.ToolKind toKind(@Nullable AcpSchema.ToolKind kind) {
		if (kind == null) {
			return StreamListener.ToolKind.OTHER;
		}
		return switch (kind) {
			case READ -> StreamListener.ToolKind.READ;
			case EDIT -> StreamListener.ToolKind.EDIT;
			case DELETE -> StreamListener.ToolKind.DELETE;
			case MOVE -> StreamListener.ToolKind.MOVE;
			case SEARCH -> StreamListener.ToolKind.SEARCH;
			case EXECUTE -> StreamListener.ToolKind.EXECUTE;
			case THINK -> StreamListener.ToolKind.THINK;
			case FETCH -> StreamListener.ToolKind.FETCH;
			case SWITCH_MODE -> StreamListener.ToolKind.SWITCH_MODE;
			case OTHER -> StreamListener.ToolKind.OTHER;
		};
	}

	@Nullable
	private static StreamListener.PermissionDecision toDecision(@Nullable AcpSchema.PermissionOptionKind kind) {
		if (kind == null) {
			return null;
		}
		return switch (kind) {
			case ALLOW_ONCE -> StreamListener.PermissionDecision.ALLOW_ONCE;
			case ALLOW_ALWAYS -> StreamListener.PermissionDecision.ALLOW_ALWAYS;
			case REJECT_ONCE -> StreamListener.PermissionDecision.REJECT_ONCE;
			case REJECT_ALWAYS -> StreamListener.PermissionDecision.REJECT_ALWAYS;
		};
	}

	synchronized void complete(@Nullable AcpSchema.StopReason stopReason) {
		if (cancelled) {
			return;
		}
		if (stopReason != null && stopReason != AcpSchema.StopReason.END_TURN
				&& stopReason != AcpSchema.StopReason.CANCELLED) {
			LOG.warn("ACP prompt stopped with reason {}", stopReason);
		}
		String finalText = finishResponse();
		if (!finalText.isEmpty()) {
			conversation.addMessage(ChatMessage.assistant(finalText));
		}
		listener.onCompleteResponse(finalText);
	}

	synchronized void fail(Throwable error) {
		if (cancelled) {
			return;
		}
		String partialText = finishResponse();
		if (!partialText.isEmpty()) {
			conversation.addMessage(ChatMessage.assistant(partialText));
		}
		conversation.addMessage(ChatMessage.requestFailed(error.getMessage()));
		listener.onError(error.getMessage());
	}

	synchronized void cancel() {
		if (cancelled) {
			return;
		}
		cancelled = true;
		String partialText = finishResponse();
		if (!partialText.isEmpty()) {
			conversation.addMessage(ChatMessage.assistant(partialText));
		}
		listener.onCompleteResponse(partialText);
	}

}
