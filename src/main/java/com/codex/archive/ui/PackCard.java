package com.codex.archive.ui;

import com.codex.archive.core.Supply;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.JComponent;

/**
 * The pack check: one bar per kind of supply, filled as far as the pack meets what the player set
 * out to bring. Nothing can be banked once the archive's doors close, so short bars burn ember.
 * It also shows what a run used, with the same bars and no targets.
 */
public class PackCard extends JComponent
{
	private static final int ROW = 22;
	private static final int TOP = 26;

	private final String title;
	private Map<Supply, Integer> have;
	private Map<Supply, Integer> want;

	/** @param title the card's heading */
	public PackCard(String title)
	{
		this.title = title;
		setOpaque(false);
		int h = TOP + ROW * Supply.values().length + 6;
		setPreferredSize(new Dimension(10, h));
		setMinimumSize(new Dimension(10, h));
	}

	/** @param want the targets, or null to show plain counts */
	public void update(Map<Supply, Integer> have, Map<Supply, Integer> want)
	{
		this.have = have;
		this.want = want;
		int h = TOP + ROW * rows().size() + 6;
		if (getPreferredSize().height != h)
		{
			setPreferredSize(new Dimension(10, h));
			setMinimumSize(new Dimension(10, h));
			revalidate();
		}
		repaint();
	}

	/** The supplies to draw: against targets only those with one set, otherwise only those used. */
	private List<Supply> rows()
	{
		List<Supply> out = new ArrayList<>();
		for (Supply s : Supply.values())
		{
			if (want != null ? count(want, s) > 0 : count(have, s) > 0)
			{
				out.add(s);
			}
		}
		return out;
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int w = getWidth();
		int h = getHeight();
		boolean ready = true;
		if (want != null)
		{
			for (Supply s : Supply.values())
			{
				ready &= count(have, s) >= count(want, s);
			}
		}
		RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, Theme.RADIUS, Theme.RADIUS);
		g.setPaint(new GradientPaint(0, 0, Theme.CARD_TOP, 0, h, Theme.CARD));
		g.fill(shape);
		g.setColor(want == null ? Theme.STROKE_SOFT : ready ? Theme.alpha(Theme.GREEN, 110) : Theme.alpha(Theme.EMBER, 140));
		g.draw(shape);

		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();
		g.setColor(Theme.GOLD);
		g.drawString(title.toUpperCase(), 10, 16);
		if (want != null)
		{
			String verdict = ready ? "Ready" : "Short";
			g.setColor(ready ? Theme.GREEN : Theme.EMBER);
			g.drawString(verdict, w - 10 - small.stringWidth(verdict), 16);
		}

		int y = TOP;
		for (Supply s : rows())
		{
			int n = count(have, s);
			int target = want == null ? 0 : count(want, s);
			g.setColor(Theme.TEXT_MUTED);
			g.drawString(s.getTitle(), 10, y + small.getAscent() + 1);
			String figure = want == null ? n + " " + s.getUnit() : n + " / " + target;
			Color figureColor = want == null ? Theme.TEXT : target == 0 || n >= target ? Theme.TEXT : Theme.EMBER;
			Ui.shadowed(g, figure, w - 10 - small.stringWidth(figure), y + small.getAscent() + 1, figureColor);

			int bx = 70;
			int bw = w - bx - 10 - small.stringWidth(figure) - 8;
			if (bw > 12)
			{
				int by = y + 4;
				RoundRectangle2D track = new RoundRectangle2D.Float(bx, by, bw, 6, 6, 6);
				g.setColor(Theme.INK_DEEP);
				g.fill(track);
				double fill = want == null ? Math.min(1, n / (double) Math.max(1, maxOf(have))) : target == 0 ? 1 : Math.min(1, n / (double) target);
				if (fill > 0)
				{
					Color c = want == null ? Theme.GOLD : fill >= 1 ? Theme.GREEN : Theme.progressColor(fill * 0.5);
					g.setPaint(new GradientPaint(bx, 0, Theme.mix(c, Theme.INK, 0.25), bx + bw, 0, c));
					g.fill(new RoundRectangle2D.Float(bx, by, (float) (bw * fill), 6, 6, 6));
				}
				g.setColor(Theme.STROKE_SOFT);
				g.draw(track);
			}
			y += ROW;
		}
		g.dispose();
	}

	private static int count(Map<Supply, Integer> map, Supply s)
	{
		return map == null ? 0 : map.getOrDefault(s, 0);
	}

	private static int maxOf(Map<Supply, Integer> map)
	{
		int max = 0;
		if (map != null)
		{
			for (int v : map.values())
			{
				max = Math.max(max, v);
			}
		}
		return max;
	}
}
