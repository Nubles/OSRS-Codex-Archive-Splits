package com.codex.archive.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.BorderLayout;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import org.junit.Test;

/** What clips its drawing to a rounded shape gives the clip it was handed back unchanged, never cleared. */
public class ClipTest
{
	@Test
	public void anAccentedCardKeepsTheClip()
	{
		Card card = new Card(new BorderLayout());
		card.accent(Theme.EMBER);
		card.setSize(180, 60);
		Graphics2D g = graphics();
		Area before = new Area(g.getClip());
		card.paintComponent(g);
		kept(before, g);
		g.dispose();
	}

	@Test
	public void aCardsPageKeepsTheClip()
	{
		Graphics2D g = graphics();
		Area before = new Area(g.getClip());
		RunCard.frame(g, 400, true);
		kept(before, g);
		g.dispose();
	}

	/** Area's own equals compares the region; the bounds make a failure readable. */
	private static void kept(Area before, Graphics2D g)
	{
		assertEquals(before.getBounds(), g.getClipBounds());
		assertTrue(before.equals(new Area(g.getClip())));
	}

	private static Graphics2D graphics()
	{
		Graphics2D g = new BufferedImage(RunCard.WIDTH, 420, BufferedImage.TYPE_INT_ARGB).createGraphics();
		g.setClip(new Rectangle(5, 6, 150, 40));
		return g;
	}
}
