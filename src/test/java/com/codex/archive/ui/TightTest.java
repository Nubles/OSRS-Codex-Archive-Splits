package com.codex.archive.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.Test;

public class TightTest
{
	@Test
	public void setsAColonCloseAndEvenBetweenItsFigures()
	{
		Graphics2D g = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB).createGraphics();
		g.setFont(Theme.title().deriveFont(Theme.title().getSize2D() * 3.5f));
		Tight.Layout set = Tight.layout(g, "6:54.0");
		float before = gapBefore(g, set, 1);
		float after = gapBefore(g, set, 2);
		assertEquals(before, after, 0.01);
		// Narrower than the font sets it on its own, which opened a gap at the colon.
		assertTrue(set.width < g.getFontMetrics().stringWidth("6:54.0"));
		g.dispose();
	}

	@Test
	public void setsEveryFigureTheSameDistanceFromTheNext()
	{
		Graphics2D g = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB).createGraphics();
		g.setFont(Theme.title().deriveFont(Theme.title().getSize2D() * 3.5f));
		// The font gives a 1 a digit's room and a 9 a wide left side, which opened "1 0:0 9.0".
		for (String time : new String[]{"10:09.0", "11:43.8", "12:03.6"})
		{
			Tight.Layout set = Tight.layout(g, time);
			float first = gapBefore(g, set, 1);
			for (int i = 2; i < time.length(); i++)
			{
				assertEquals(time + " at " + i, first, gapBefore(g, set, i), 0.01);
			}
		}
		g.dispose();
	}

	/** The space between the ink of glyph i - 1 and glyph i. */
	private static float gapBefore(Graphics2D g, Tight.Layout set, int i)
	{
		java.awt.geom.Rectangle2D prev = g.getFont().createGlyphVector(g.getFontRenderContext(), set.text.substring(i - 1, i)).getVisualBounds();
		java.awt.geom.Rectangle2D next = g.getFont().createGlyphVector(g.getFontRenderContext(), set.text.substring(i, i + 1)).getVisualBounds();
		return (float) ((set.pens[i] + next.getX()) - (set.pens[i - 1] + prev.getX() + prev.getWidth()));
	}
}
