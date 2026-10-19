package com.codex.archive.ui;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;

/**
 * A time set large in the pixel font with even spacing. The font gives a colon and a point as
 * much room as a digit, so at three or four times its size a gap opens beside them ("6 :54.0").
 * Here figures keep the font's own spacing, and a colon or point is placed by the ink it
 * draws, a small even gap from the figures either side.
 */
final class Tight
{
	/** The gap either side of a colon or point, as a share of the font's size. */
	private static final float PUNCTUATION_GAP = 0.07f;

	private Tight()
	{
	}

	/** Where each glyph of a text goes, and how wide the whole is as drawn. */
	static final class Layout
	{
		final String text;
		final float[] pens;
		/** From the first glyph's ink to the last's. */
		final float width;
		private final float lead;

		private Layout(String text, float[] pens, float width, float lead)
		{
			this.text = text;
			this.pens = pens;
			this.width = width;
			this.lead = lead;
		}

		/** Draws it with its ink starting at x, in the graphics' font and paint. */
		void draw(Graphics2D g, float x, float baseline)
		{
			for (int i = 0; i < text.length(); i++)
			{
				g.drawString(String.valueOf(text.charAt(i)), x - lead + pens[i], baseline);
			}
		}
	}

	static boolean narrow(char c)
	{
		return c == ':' || c == '.';
	}

	/** Lays a text out in the graphics' current font. */
	static Layout layout(Graphics2D g, String text)
	{
		Font font = g.getFont();
		FontRenderContext frc = g.getFontRenderContext();
		float gap = font.getSize2D() * PUNCTUATION_GAP;
		float[] pens = new float[text.length()];
		float pen = 0;
		float right = 0;
		float lead = 0;
		float advance = 0;
		for (int i = 0; i < text.length(); i++)
		{
			char c = text.charAt(i);
			GlyphVector gv = font.createGlyphVector(frc, String.valueOf(c));
			Rectangle2D box = ink(gv);
			if (i == 0)
			{
				lead = (float) box.getX();
			}
			else if (narrow(c) || narrow(text.charAt(i - 1)))
			{
				pen = right + gap - (float) box.getX();
			}
			else
			{
				pen += advance;
			}
			pens[i] = pen;
			advance = gv.getGlyphMetrics(0).getAdvance();
			right = pen + (float) (box.getX() + box.getWidth());
		}
		return new Layout(text, pens, right - lead, lead);
	}

	/** The ink a glyph draws, from its pen position; a space is given its advance. */
	private static Rectangle2D ink(GlyphVector gv)
	{
		Rectangle2D box = gv.getVisualBounds();
		return box.isEmpty() ? new Rectangle2D.Float(0, 0, gv.getGlyphMetrics(0).getAdvance(), 0) : box;
	}
}
