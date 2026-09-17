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

import javax.swing.SizeRequirements;
import javax.swing.text.*;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.InlineView;
import java.awt.*;

final class WrappingHtmlEditorKit extends HTMLEditorKit {

	private static final ViewFactory FACTORY = new WrappingViewFactory();

	@Override public ViewFactory getViewFactory() {
		return FACTORY;
	}

	private static final class WrappingViewFactory extends HTMLFactory {
		@Override public View create(Element element) {
			View view = super.create(element);
			// Exact match only: subclasses such as the <br> view carry forced line breaks that must be kept
			if (view.getClass() == InlineView.class) {
				return new WrappingInlineView(element);
			} else if (view instanceof ParagraphView) {
				return new WrappingParagraphView(element);
			}
			return view;
		}
	}

	private static final class WrappingInlineView extends InlineView {
		private WrappingInlineView(Element element) {
			super(element);
		}

		@Override public int getBreakWeight(int axis, float pos, float len) {
			if (axis == View.X_AXIS) {
				checkPainter();
				int p0 = getStartOffset();
				int p1 = getGlyphPainter().getBoundedPosition(this, p0, pos, len);
				if (p1 == p0) {
					return View.BadBreakWeight;
				}
				return containsWhitespace(p0, p1) ? View.ExcellentBreakWeight : View.GoodBreakWeight;
			}
			return super.getBreakWeight(axis, pos, len);
		}

		private boolean containsWhitespace(int p0, int p1) {
			try {
				String text = getDocument().getText(p0, p1 - p0);
				for (int i = 0; i < text.length(); i++) {
					if (Character.isWhitespace(text.charAt(i))) {
						return true;
					}
				}
			} catch (BadLocationException ignored) {
			}
			return false;
		}
	}

	private static final class WrappingParagraphView extends javax.swing.text.html.ParagraphView {

		private static final int MAX_MINIMUM_WIDTH = 96;

		private WrappingParagraphView(Element element) {
			super(element);
		}

		private boolean isPreformatted() {
			Object whitespace = getElement().getAttributes()
					.getAttribute(javax.swing.text.html.CSS.Attribute.WHITE_SPACE);
			return whitespace != null && "pre".equals(whitespace.toString());
		}

		@Override protected SizeRequirements calculateMinorAxisRequirements(int axis, SizeRequirements r) {
			SizeRequirements requirements = super.calculateMinorAxisRequirements(axis, r);
			requirements.minimum = Math.min(requirements.minimum, MAX_MINIMUM_WIDTH);
			return requirements;
		}

		@Override public boolean isVisible() {
			// Blank lines inside <pre> must stay visible
			return isPreformatted() || super.isVisible();
		}

		@Override public float nextTabStop(float x, int tabOffset) {
			if (isPreformatted() && getTabSet() == null) {
				View v = getViewAtPosition(tabOffset, null);
				Container container = getContainer();
				if (getDocument() instanceof StyledDocument styledDocument && v != null && container != null) {
					FontMetrics metrics = container.getFontMetrics(styledDocument.getFont(v.getAttributes()));
					int tabWidth = 8 * metrics.charWidth('W');
					int tabBase = (int) getTabBase();
					return (float) ((((int) x - tabBase) / tabWidth + 1) * tabWidth + tabBase);
				}
			}
			return super.nextTabStop(x, tabOffset);
		}
	}

}
