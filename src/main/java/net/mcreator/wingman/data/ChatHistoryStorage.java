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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.mcreator.io.FileIO;
import net.mcreator.ui.MCreator;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class ChatHistoryStorage {

	private static final String CONVERSATION_EXTENSION = ".wgm";
	private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

	private final MCreator mcreator;

	public ChatHistoryStorage(MCreator mcreator) {
		this.mcreator = mcreator;
	}

	public ChatConversation newConversation() {
		return new ChatConversation(this, UUID.randomUUID().toString(), ChatConversation.DEFAULT_TITLE,
				System.currentTimeMillis());
	}

	public List<ChatConversation.Summary> listSummaries() {
		File[] files = listConversationFiles();
		List<ChatConversation.Summary> summaries = new ArrayList<>(files.length);
		for (File file : files) {
			ChatConversation conversation = readConversationFile(file);
			if (conversation != null && !conversation.messages().isEmpty()) {
				summaries.add(conversation.toSummary());
			}
		}
		summaries.sort(Comparator.comparingLong(ChatConversation.Summary::updatedAt).reversed());
		return List.copyOf(summaries);
	}

	public ChatConversation load(String conversationId) {
		File file = getConversationFile(conversationId);
		if (!file.exists()) {
			return null;
		}
		return readConversationFile(file);
	}

	void save(ChatConversation conversation) {
		if (conversation.isEmpty()) {
			return;
		}
		FileIO.writeStringToFile(gson.toJson(conversation), getConversationFile(conversation.id()));
	}

	public boolean delete(String conversationId) {
		return getConversationFile(conversationId).delete();
	}

	private File getWingmanDir() {
		File wingmanDir = new File(mcreator.getWorkspace().getFolderManager().getWorkspaceCacheDir(), "wingman");
		if (!wingmanDir.exists()) {
			wingmanDir.mkdirs();
		}
		return wingmanDir;
	}

	private File getConversationFile(String conversationId) {
		return new File(getWingmanDir(), conversationId + CONVERSATION_EXTENSION);
	}

	private File[] listConversationFiles() {
		File[] files = getWingmanDir().listFiles((_, name) -> name != null && name.endsWith(CONVERSATION_EXTENSION));
		return files != null ? files : new File[0];
	}

	private ChatConversation readConversationFile(File file) {
		ChatConversation conversation = gson.fromJson(FileIO.readFileToString(file), ChatConversation.class);
		if (conversation != null) {
			conversation.initializeAfterDeserialization(FilenameUtils.getBaseName(file.getName()), this);
		}
		return conversation;
	}

}
