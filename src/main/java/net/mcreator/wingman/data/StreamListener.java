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

import java.util.Set;

public interface StreamListener {

	void onPartialResponse(String responseText);

	void onToolRequest(ToolRequestEvent request);

	void onToolResult(ToolResultEvent result);

	void onCompleteResponse(String finalText);

	void onError(String errorMessage);

	default PermissionDecision onPermissionRequest(PermissionRequest request) {
		return PermissionDecision.ALLOW_ONCE;
	}

	record ToolRequestEvent(String id, String name, String arguments) {
	}

	record ToolResultEvent(String id, String name, String result) {
	}

	record PermissionRequest(String toolCallId, String toolName, String title, String arguments,
							 Set<PermissionDecision> offeredDecisions) {
	}

	enum PermissionDecision {
		ALLOW_ONCE, ALLOW_ALWAYS, REJECT_ONCE, REJECT_ALWAYS, CANCELLED
	}

}
