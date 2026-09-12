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

import net.mcreator.ui.MCreator;

import java.util.concurrent.CompletableFuture;

public abstract class AssistantService {

	protected final ChatHistoryStorage historyStorage;

	public AssistantService(MCreator mcreator) {
		this.historyStorage = new ChatHistoryStorage(mcreator);
	}

	public ChatHistoryStorage getHistoryStorage() {
		return historyStorage;
	}

	/**
	 * Loads the conversation to be shown. Services that keep conversation state on an external agent can
	 * override this to synchronize the local history first. If the returned future completes exceptionally,
	 * the local history is shown as is.
	 */
	public CompletableFuture<ChatConversation> openConversation(String conversationId) {
		return CompletableFuture.completedFuture(historyStorage.load(conversationId));
	}

	abstract public void stream(ChatConversation conversation, String messageText, StreamListener listener);

	/**
	 * Cancels the request currently streaming for the given conversation, if any. The implementation must
	 * still invoke {@link StreamListener#onCompleteResponse(String)} with the partial response.
	 */
	abstract public void cancel(ChatConversation conversation);

	abstract public float getContextUsage(ChatConversation conversation);

	abstract public void close();

}
