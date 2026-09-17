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

import net.mcreator.ui.laf.themes.Theme;
import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.awt.*;
import java.util.List;
import java.util.regex.Matcher;

public final class MarkdownRenderer {

	private static final java.util.regex.Pattern OUTER_MARKDOWN_FENCE = java.util.regex.Pattern.compile(
			"^\\s*```(?:markdown|md)?[ \\t]*\\r?\\n([\\s\\S]*?)\\r?\\n```\\s*$",
			java.util.regex.Pattern.CASE_INSENSITIVE);

	private static final java.util.regex.Pattern INLINE_MARKDOWN_FENCE = java.util.regex.Pattern.compile(
			"```(?:markdown|md)[ \\t]*\\r?\\n([\\s\\S]*?)\\r?\\n```", java.util.regex.Pattern.CASE_INSENSITIVE);

	private static final List<Extension> EXTENSIONS = List.of(AutolinkExtension.create(), TablesExtension.create());

	private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();

	private static final HtmlRenderer RENDERER = HtmlRenderer.builder().extensions(EXTENSIONS).escapeHtml(true).build();

	public static String toHtmlDocument(String markdown, Color textColor, Font font) {
		String safeMarkdown = normalizeMarkdownInput(markdown == null ? "" : markdown);
		Node parsed = PARSER.parse(safeMarkdown);
		String body = RENDERER.render(parsed);
		String colorHex = toHex(textColor);
		String fontFamily = font != null ? font.getFamily() : "SansSerif";
		int fontSize = font != null ? font.getSize() : 13;

		// Blocks, borders, and links follow the current theme, so the rendered text fits light and dark themes alike
		Theme theme = Theme.current();
		String codeBackgroundHex = toHex(theme.getSecondAltBackgroundColor());
		String borderHex = toHex(theme.getAltForegroundColor());
		String linkHex = toHex(theme.getInterfaceAccentColor());
		String codeFontFamily = theme.getConsoleFont().getFamily();
		return """
				<html>
				  <head>
				    <style>
				      html {
				        font-size: %dpt;
				      }
				      body {
				        margin: 0;
				        padding: 0;
				        color: %s;
				        font-family: '%s', SansSerif;
				        font-size: 1em;
				        line-height: 1.35;
				      }
				      p, li, td, th, a {
				        font-size: 1em;
				      }
				      h1, h2, h3, h4, h5, h6 {
				        margin: 0;
				        font-size: 1em;
				        font-weight: 600;
				      }
				      p { margin: 0; }
				      ul, ol {
				        margin: 0 0 0 18px;
				        padding: 0;
				      }
				      li {
				        margin: 2px 0;
				      }
				      pre {
				        margin: 0;
				        padding: 8px;
				        background-color: %s;
				        font-size: 0.95em;
				      }
				      code {
				        font-family: '%s', monospace;
				        font-size: 1em;
				      }
				      table {
				        border-collapse: collapse;
				        margin: 0;
				      }
				      th, td {
				        border: 1px solid %s;
				        padding: 4px 8px;
				      }
				      a {
				        color: %s;
				        text-decoration: underline;
				      }
				    </style>
				  </head>
				  <body>%s</body>
				</html>
				""".formatted(fontSize, colorHex, fontFamily, codeBackgroundHex, codeFontFamily, borderHex, linkHex,
				body);
	}

	private static String normalizeMarkdownInput(String markdown) {
		String unwrapped = unwrapOuterMarkdownFence(markdown);
		Matcher matcher = INLINE_MARKDOWN_FENCE.matcher(unwrapped);
		StringBuilder normalized = new StringBuilder();
		while (matcher.find()) {
			matcher.appendReplacement(normalized, Matcher.quoteReplacement(matcher.group(1)));
		}
		matcher.appendTail(normalized);
		return normalized.toString();
	}

	private static String unwrapOuterMarkdownFence(String markdown) {
		Matcher matcher = OUTER_MARKDOWN_FENCE.matcher(markdown);
		return matcher.matches() ? matcher.group(1) : markdown;
	}

	private static String toHex(Color color) {
		Color safeColor = color == null ? Color.WHITE : color;
		return "#%02x%02x%02x".formatted(safeColor.getRed(), safeColor.getGreen(), safeColor.getBlue());
	}
}