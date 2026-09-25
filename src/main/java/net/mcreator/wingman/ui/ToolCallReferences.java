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

package net.mcreator.wingman.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.mcreator.element.ModElementTypeLoader;
import net.mcreator.ui.workspace.resources.TextureType;
import net.mcreator.util.image.ImageUtils;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

final class ToolCallReferences {

	private static final int SIZE = 16;

	/**
	 * @return Icons of the mod elements and textures an MCreator MCP tool call refers to in its arguments
	 */
	static List<JLabel> icons(Workspace workspace, String toolName, @Nullable String arguments) {
		JsonObject args = parseArguments(arguments);
		if (args == null)
			return List.of();

		String tool = toolName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
		String name = string(args, "name");
		if (tool.contains("modelementdefinition"))
			return modElement(workspace, string(args, "elementName"), string(args, "elementType"));
		if (tool.contains("findreferences"))
			return "TEXTURE".equalsIgnoreCase(string(args, "referenceType")) ?
					texture(workspace, name, string(args, "textureType")) :
					modElement(workspace, name, null);
		if (tool.contains("createtexture"))
			return texture(workspace, name, string(args, "type"));
		return List.of();
	}

	private static List<JLabel> modElement(Workspace workspace, @Nullable String name, @Nullable String type) {
		if (name == null)
			return List.of();
		ModElement element = workspace.getModElementByName(name);
		ImageIcon icon = element != null ? element.getElementIcon() : null;
		if (icon == null && element != null)
			icon = element.getType().getIcon();
		if (icon == null && type != null) {
			try {
				icon = ModElementTypeLoader.getModElementType(type.toLowerCase(Locale.ROOT)).getIcon();
			} catch (IllegalArgumentException ignored) {
			}
		}
		return icon == null ? List.of() : List.of(label(icon, name));
	}

	private static List<JLabel> texture(Workspace workspace, @Nullable String name, @Nullable String type) {
		if (name == null)
			return List.of();
		for (TextureType textureType : TextureType.values()) {
			if (type != null && !type.equalsIgnoreCase(textureType.getID()))
				continue;
			File file = workspace.getFolderManager().getTextureFile(name, textureType);
			try {
				BufferedImage image = file.isFile() ? ImageIO.read(file) : null;
				if (image != null)
					return List.of(label(new ImageIcon(image), name));
			} catch (IOException ignored) {
			}
		}
		return List.of();
	}

	private static JLabel label(ImageIcon icon, String tooltip) {
		JLabel label = new JLabel(new ImageIcon(ImageUtils.resizeAA(icon.getImage(), SIZE)));
		label.setToolTipText(tooltip);
		return label;
	}

	@Nullable private static JsonObject parseArguments(@Nullable String arguments) {
		try {
			JsonElement json = JsonParser.parseString(arguments == null ? "" : arguments);
			if (!json.isJsonObject())
				return null;
			JsonObject object = json.getAsJsonObject();
			// Some agents wrap the arguments of MCP calls (e.g. Cursor reports {providerIdentifier, toolName, args})
			return object.get("args") instanceof JsonObject args ? args : object;
		} catch (RuntimeException e) {
			return null;
		}
	}

	@Nullable private static String string(JsonObject object, String key) {
		return object.get(key) instanceof JsonPrimitive value ? value.getAsString() : null;
	}

}
