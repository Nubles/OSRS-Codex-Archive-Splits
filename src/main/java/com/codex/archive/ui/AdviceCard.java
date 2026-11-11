package com.codex.archive.ui;

import com.codex.archive.core.PackAdvice;
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
 * The pack check learned from the player's clears: for each supply, the most one clear used and
 * what to bring for it, with the amount set now beside any that would change. A ledger of three
 * columns, so the eye runs down "most" and "bring" without a legend.
 */
public class AdviceCard extends JComponent
{
	private static final int ROW = 18;
	private static final int TOP = 40;

	private PackAdvice.Suggestion advice;
	private Map<Supply, Integer> targets;

	public AdviceCard()
	{
		setOpaque(false);
		setPreferredSize(new Dimension(10, TOP + ROW * Supply.values().length + 6));
	}

	public void update(PackAdvice.Suggestion advice, Map<Supply, Integer> targets)
	{
		this.advice = advice;
		this.targets = targets;
		int h = TOP + ROW * rows().size() + 6;
		if (getPreferredSize().height != h)
		{
			setPreferredSize(new Dimension(10, h));
			setMinimumSize(new Dimension(10, h));
			revalidate();
		}
		setToolTipText(advice == null ? null : "<html>The most one of " + (advice.clears == 1 ? "your clears" : "your last " + advice.clears + " clears")
			+ " used,<br>plus a tenth to spare (at least one more),<br>with potions rounded up to whole potions.</html>");
		repaint();
	}

	/** Supplies worth a line: any to bring, and any the check counts now that it would leave out. */
	private List<Supply> rows()
	{
		List<Supply> out = new ArrayList<>();
		if (advice == null)
		{
			return out;
		}
		for (Supply s : Supply.values())
		{
			if (advice.amount(s) > 0 || target(s) > 0)
			{
				out.add(s);
			}
		}
		return out;
	}

	private int target(Supply s)
	{
		Integer n = targets == null ? null : targets.get(s);
		return n == null ? 0 : n;
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		if (advice == null)
		{
			return;
		}
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int w = getWidth();
		int h = getHeight();
		boolean matches = advice.matches(targets);
		RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, Theme.RADIUS, Theme.RADIUS);
		g.setPaint(new GradientPaint(0, 0, Theme.CARD_TOP, 0, h, Theme.CARD));
		g.fill(shape);
		g.setColor(matches ? Theme.alpha(Theme.GREEN, 110) : Theme.alpha(Theme.GOLD, 120));
		g.draw(shape);
		g.setColor(Theme.alpha(java.awt.Color.WHITE, 12));
		g.drawLine(Theme.RADIUS / 2, 1, w - Theme.RADIUS / 2, 1);

		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();
		g.setColor(Theme.GOLD);
		g.drawString("SUGGESTED", 10, 16);
		int changes = 0;
		for (Supply s : rows())
		{
			changes += advice.amount(s) != target(s) ? 1 : 0;
		}
		String verdict = matches ? "Matches yours" : changes + (changes == 1 ? " change" : " changes");
		g.setColor(matches ? Theme.GREEN : Theme.GOLD_BRIGHT);
		g.drawString(verdict, w - 10 - small.stringWidth(verdict), 16);

		// Three columns: the most a clear used, what to bring, and what is set now where it differs.
		int nowRight = w - 10;
		int nowWidth = small.stringWidth("now 112");
		int bringRight = nowRight - nowWidth - 8;
		int mostRight = bringRight - small.stringWidth("bring") - 12;
		g.setColor(Theme.TEXT_FAINT);
		g.drawString("most", mostRight - small.stringWidth("most"), 32);
		g.drawString("bring", bringRight - small.stringWidth("bring"), 32);
		g.setColor(Theme.alpha(Theme.GOLD, 50));
		g.drawLine(10, 35, w - 10, 35);

		int y = TOP;
		for (Supply s : rows())
		{
			int base = y + small.getAscent();
			int most = advice.most(s);
			int bring = advice.amount(s);
			int now = target(s);
			boolean change = bring != now;
			g.setColor(Theme.TEXT_MUTED);
			g.drawString(s.getTitle(), 10, base);
			String mostText = most > 0 ? String.valueOf(most) : "-";
			Ui.shadowed(g, mostText, mostRight - small.stringWidth(mostText), base, most > 0 ? Theme.TEXT : Theme.TEXT_FAINT);
			String bringText = bring > 0 ? String.valueOf(bring) : "off";
			Color bringColor = !change ? Theme.TEXT : bring > 0 ? Theme.GOLD_BRIGHT : Theme.TEXT_MUTED;
			Ui.shadowed(g, bringText, bringRight - small.stringWidth(bringText), base, bringColor);
			if (change)
			{
				String nowText = now > 0 ? "now " + now : "now off";
				g.setColor(Theme.TEXT_FAINT);
				g.drawString(nowText, nowRight - small.stringWidth(nowText), base);
			}
			y += ROW;
		}
		g.dispose();
	}
}
