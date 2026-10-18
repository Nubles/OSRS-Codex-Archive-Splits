package com.codex.archive.overlay;

import com.codex.archive.ui.Theme;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;

/** The gilt plaque both overlays sit on. */
final class Plaque
{
	private Plaque()
	{
	}

	static void paint(Graphics2D g, int width, int height, Color rim)
	{
		RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, width - 1, height - 1, 10, 10);
		g.setPaint(new GradientPaint(0, 0, Theme.alpha(Theme.CARD_TOP, 232), 0, height, Theme.alpha(Theme.INK_DEEP, 232)));
		g.fill(shape);
		g.setColor(rim);
		g.draw(shape);
	}
}
