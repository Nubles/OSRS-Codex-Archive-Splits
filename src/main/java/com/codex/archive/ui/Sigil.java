package com.codex.archive.ui;

import java.awt.BasicStroke;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

/** The mark of Archive Splits: an open gilt tome split by a teal fracture, with a shard of the raid's purple above it. */
public final class Sigil
{
	private Sigil()
	{
	}

	public static BufferedImage icon(int size)
	{
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		Theme.smooth(g);
		paint(g, 0, 0, size);
		g.dispose();
		return image;
	}

	/** Draws the mark in a size by size box. */
	public static void paint(Graphics2D g, float x, float y, float size)
	{
		float s = size / 16f;
		float stroke = Math.max(0.7f, 0.6f * s);

		// The two pages, curving up from the spine.
		Path2D left = new Path2D.Float();
		left.moveTo(x + 8 * s, y + 6.4f * s);
		left.curveTo(x + 6 * s, y + 5.2f * s, x + 3 * s, y + 5 * s, x + 0.8f * s, y + 6 * s);
		left.lineTo(x + 0.8f * s, y + 14.2f * s);
		left.curveTo(x + 3 * s, y + 13.4f * s, x + 6 * s, y + 13.6f * s, x + 8 * s, y + 14.8f * s);
		left.closePath();
		Path2D right = new Path2D.Float();
		right.moveTo(x + 8 * s, y + 6.4f * s);
		right.curveTo(x + 10 * s, y + 5.2f * s, x + 13 * s, y + 5 * s, x + 15.2f * s, y + 6 * s);
		right.lineTo(x + 15.2f * s, y + 14.2f * s);
		right.curveTo(x + 13 * s, y + 13.4f * s, x + 10 * s, y + 13.6f * s, x + 8 * s, y + 14.8f * s);
		right.closePath();
		g.setPaint(new GradientPaint(x, y + 5 * s, Theme.GOLD_BRIGHT, x, y + 15 * s, Theme.GOLD_DIM));
		g.fill(left);
		g.setPaint(new GradientPaint(x, y + 5 * s, Theme.GOLD, x, y + 15 * s, Theme.mix(Theme.GOLD_DIM, Theme.INK_DEEP, 0.3)));
		g.fill(right);
		g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(Theme.INK_DEEP);
		g.draw(left);
		g.draw(right);

		// Lines of script on each page.
		g.setColor(Theme.alpha(Theme.INK_DEEP, 120));
		g.setStroke(new BasicStroke(Math.max(0.5f, 0.45f * s)));
		for (int i = 0; i < 3; i++)
		{
			float ly = y + (8.4f + i * 1.9f) * s;
			g.draw(new java.awt.geom.Line2D.Float(x + 2.2f * s, ly, x + 6.4f * s, ly + 0.3f * s));
		}

		// The fracture, down the right page.
		Path2D crack = new Path2D.Float();
		crack.moveTo(x + 11.6f * s, y + 5.6f * s);
		crack.lineTo(x + 10.6f * s, y + 8.4f * s);
		crack.lineTo(x + 12.4f * s, y + 9.8f * s);
		crack.lineTo(x + 10.8f * s, y + 13.8f * s);
		g.setStroke(new BasicStroke(Math.max(1.8f, 1.9f * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(Theme.INK_DEEP);
		g.draw(crack);
		g.setStroke(new BasicStroke(Math.max(0.9f, 0.95f * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(Theme.TEAL);
		g.draw(crack);

		// A purple shard rising from the break.
		Path2D shard = new Path2D.Float();
		shard.moveTo(x + 11.2f * s, y + 0.6f * s);
		shard.lineTo(x + 12.9f * s, y + 2.6f * s);
		shard.lineTo(x + 11.2f * s, y + 4.6f * s);
		shard.lineTo(x + 9.5f * s, y + 2.6f * s);
		shard.closePath();
		float r = 2.6f * s;
		g.setColor(Theme.alpha(Theme.PURPLE, 70));
		g.fill(new Ellipse2D.Float(x + 11.2f * s - r, y + 2.6f * s - r, r * 2, r * 2));
		g.setPaint(new GradientPaint(x + 9.5f * s, y, Theme.mix(Theme.PURPLE, Theme.TEXT, 0.45f), x + 12.9f * s, y + 4.6f * s, Theme.PURPLE_DEEP));
		g.fill(shard);
		g.setStroke(new BasicStroke(stroke));
		g.setColor(Theme.INK_DEEP);
		g.draw(shard);
	}
}
