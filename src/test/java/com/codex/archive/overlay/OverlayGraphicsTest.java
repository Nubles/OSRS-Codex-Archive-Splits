package com.codex.archive.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.codex.archive.core.Archive;
import com.codex.archive.core.Encounter;
import com.codex.archive.core.Run;
import com.codex.archive.core.RunTracker;
import com.codex.archive.core.Supply;
import com.codex.archive.ui.View;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import net.runelite.client.ui.overlay.Overlay;
import org.junit.Test;

/**
 * RuneLite draws every overlay with one shared Graphics2D. An overlay that leaves its clip,
 * transform, stroke, font, composite or hints changed breaks every overlay drawn after it, so each
 * of this plugin's plaques is drawn over a graphics in a known state that must be the same after.
 */
public class OverlayGraphicsTest
{
	private static Map<Supply, Integer> pack(int food, int brews)
	{
		Map<Supply, Integer> m = new EnumMap<>(Supply.class);
		m.put(Supply.FOOD, food);
		m.put(Supply.BREWS, brews);
		return m;
	}

	@Test
	public void theSplitsPlaqueLeavesTheGraphicsAsItFoundThem()
	{
		RunTracker t = new RunTracker();
		t.begin(0, 0, 3, pack(10, 12));
		t.engage(Encounter.GUARDIAN, 20);
		t.fell(Encounter.GUARDIAN, 100, false);
		for (int i = 100; i <= 110; i++)
		{
			t.tick(i);
		}
		t.engage(Encounter.BORK, 130);
		Run live = t.view(200);
		SplitsPlaque plaque = new SplitsPlaque();
		plaque.setEnabled(true);
		plaque.show(new View("x", true, new Archive(), live, false, null, null, 0));
		drawsCleanly(plaque);
	}

	@Test
	public void thePackPlaqueLeavesTheGraphicsAsItFoundThem()
	{
		PackPlaque plaque = new PackPlaque();
		plaque.setEnabled(true);
		plaque.show(new View("x", true, new Archive(), null, true, pack(2, 4), pack(10, 12), 0));
		drawsCleanly(plaque);
	}

	private static void drawsCleanly(Overlay overlay)
	{
		BufferedImage image = new BufferedImage(400, 200, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.translate(12, 9);
		g.setClip(new Rectangle(3, 4, 220, 90));
		g.setStroke(new BasicStroke(2.5f));
		g.setFont(new Font(Font.SERIF, Font.ITALIC, 17));
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f));
		g.setColor(Color.MAGENTA);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		Area clip = new Area(g.getClip());
		AffineTransform transform = g.getTransform();

		assertNotNull("the plaque is drawn", overlay.render(g));

		// Area's own equals compares the region; the bounds make a failure readable.
		assertEquals("clip", clip.getBounds(), g.getClipBounds());
		assertTrue("clip", clip.equals(new Area(g.getClip())));
		assertEquals("transform", transform, g.getTransform());
		assertEquals("stroke", new BasicStroke(2.5f), g.getStroke());
		assertEquals("font", new Font(Font.SERIF, Font.ITALIC, 17), g.getFont());
		assertEquals("composite", AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f), g.getComposite());
		assertEquals("colour", Color.MAGENTA, g.getColor());
		assertEquals("hints", RenderingHints.VALUE_ANTIALIAS_OFF, g.getRenderingHint(RenderingHints.KEY_ANTIALIASING));
		g.dispose();
	}
}
