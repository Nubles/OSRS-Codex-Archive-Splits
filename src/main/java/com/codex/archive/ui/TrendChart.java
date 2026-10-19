package com.codex.archive.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import javax.swing.JComponent;

/**
 * Recent clear times, oldest on the left, as beads on a thread. Quicker sits higher, so a
 * line climbing to the right is a raider improving. The best is a gilt rule, the typical
 * time a teal one, and each new best along the way is a filled gold bead.
 */
class TrendChart extends JComponent
{
	private static final int HEIGHT = 86;
	private static final int PAD = 8;
	private static final int LABELS = 36;

	private final List<Integer> times;
	private final int best;
	private final int typical;

	/**
	 * @param times   clear times, oldest first
	 * @param best    the party size's best, drawn as a rule
	 * @param typical the median of these times
	 */
	TrendChart(List<Integer> times, int best, int typical)
	{
		this.times = times;
		this.best = best;
		this.typical = typical;
		setOpaque(false);
		setPreferredSize(new Dimension(10, HEIGHT));
		setMinimumSize(new Dimension(10, HEIGHT));
		setToolTipText("Your last " + times.size() + " clears, oldest on the left; higher is quicker.");
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int w = getWidth();
		int h = getHeight();
		RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, Theme.RADIUS, Theme.RADIUS);
		g.setPaint(new GradientPaint(0, 0, Theme.CARD_TOP, 0, h, Theme.CARD));
		g.fill(shape);
		g.setColor(Theme.STROKE_SOFT);
		g.draw(shape);
		if (times.isEmpty())
		{
			g.dispose();
			return;
		}
		int lo = Math.min(best, times.get(0));
		int hi = times.get(0);
		for (int t : times)
		{
			lo = Math.min(lo, t);
			hi = Math.max(hi, t);
		}
		if (best > 0)
		{
			lo = Math.min(lo, best);
		}
		// A little air above and below, and never a flat zero range.
		int range = Math.max(10, hi - lo);
		lo -= range / 8;
		hi += range / 8;
		int left = PAD;
		int right = w - PAD - LABELS;
		int top = PAD + 2;
		int bottom = h - PAD - 2;

		g.setFont(Theme.small());
		FontMetrics fm = g.getFontMetrics();
		rule(g, best, lo, hi, top, bottom, left, right, Theme.GOLD, "best", fm, true);
		if (typical > 0 && typical != best)
		{
			rule(g, typical, lo, hi, top, bottom, left, right, Theme.TEAL, "typ", fm, false);
		}

		Path2D line = new Path2D.Float();
		int n = times.size();
		float[] xs = new float[n];
		float[] ys = new float[n];
		for (int i = 0; i < n; i++)
		{
			xs[i] = n == 1 ? (left + right) / 2f : left + (right - left) * i / (float) (n - 1);
			ys[i] = y(times.get(i), lo, hi, top, bottom);
			if (i == 0)
			{
				line.moveTo(xs[i], ys[i]);
			}
			else
			{
				line.lineTo(xs[i], ys[i]);
			}
		}
		g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(Theme.alpha(Theme.TEXT_MUTED, 150));
		g.draw(line);
		g.setStroke(new BasicStroke(1f));
		int record = Integer.MAX_VALUE;
		for (int i = 0; i < n; i++)
		{
			boolean pb = times.get(i) < record;
			record = Math.min(record, times.get(i));
			boolean last = i == n - 1;
			Color c = pb ? Theme.GOLD_BRIGHT : last ? Theme.TEXT : Theme.TEXT_MUTED;
			float r = last ? 3.5f : 2.5f;
			g.setColor(Theme.INK_DEEP);
			g.fill(new java.awt.geom.Ellipse2D.Float(xs[i] - r - 1, ys[i] - r - 1, r * 2 + 2, r * 2 + 2));
			g.setColor(c);
			if (pb)
			{
				g.fill(new java.awt.geom.Ellipse2D.Float(xs[i] - r, ys[i] - r, r * 2, r * 2));
			}
			else
			{
				g.draw(new java.awt.geom.Ellipse2D.Float(xs[i] - r, ys[i] - r, r * 2, r * 2));
			}
		}
		g.dispose();
	}

	private static float y(int ticks, int lo, int hi, int top, int bottom)
	{
		// Quicker is higher.
		return top + (bottom - top) * (ticks - lo) / (float) (hi - lo);
	}

	private static void rule(Graphics2D g, int ticks, int lo, int hi, int top, int bottom, int left, int right, Color color,
		String label, FontMetrics fm, boolean solid)
	{
		if (ticks <= 0)
		{
			return;
		}
		int y = Math.round(y(ticks, lo, hi, top, bottom));
		g.setStroke(solid ? new BasicStroke(1f)
			: new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{3f, 3f}, 0f));
		g.setColor(Theme.alpha(color, solid ? 120 : 110));
		g.drawLine(left, y, right, y);
		g.setStroke(new BasicStroke(1f));
		g.setColor(Theme.alpha(color, 220));
		g.drawString(label, right + 8, y + fm.getAscent() / 2 - 1);
	}
}
