package com.codex.archive.ui;

import com.codex.archive.core.Spoils;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JComponent;

/** A tablet for one unique: a cut gem, lit purple once owned and dark until then, with how many have come. */
class UniqueTile extends JComponent
{
	private final String unique;
	private final int count;

	UniqueTile(String unique, int count)
	{
		this.unique = unique;
		this.count = count;
		setOpaque(false);
		setPreferredSize(new Dimension(10, 66));
		setToolTipText(unique + (count == 0 ? ": not yet" : ": " + count));
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int w = getWidth();
		int h = getHeight();
		boolean owned = count > 0;
		Color tone = Spoils.isMegarare(unique) ? Theme.MEGARARE : Theme.PURPLE;
		RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, Theme.RADIUS, Theme.RADIUS);
		g.setPaint(new GradientPaint(0, 0, owned ? Theme.mix(Theme.CARD_TOP, tone, 0.12) : Theme.CARD_TOP, 0, h, Theme.CARD));
		g.fill(shape);
		g.setColor(owned ? Theme.alpha(tone, 120) : Theme.STROKE_SOFT);
		g.draw(shape);

		// The gem: a cut lozenge with a bright facet.
		float cx = w / 2f;
		float cy = 22;
		float r = 11;
		if (owned)
		{
			g.setPaint(new RadialGradientPaint(cx, cy, r * 2, new float[]{0, 1}, new Color[]{Theme.alpha(tone, 90), Theme.alpha(tone, 0)}));
			g.fill(new Ellipse2D.Float(cx - r * 2, cy - r * 2, r * 4, r * 4));
		}
		Path2D gem = new Path2D.Float();
		gem.moveTo(cx, cy - r);
		gem.lineTo(cx + r * 0.8f, cy - r * 0.25f);
		gem.lineTo(cx, cy + r);
		gem.lineTo(cx - r * 0.8f, cy - r * 0.25f);
		gem.closePath();
		g.setPaint(owned
			? new GradientPaint(cx - r, cy - r, Theme.mix(tone, Theme.TEXT, 0.45), cx + r, cy + r, Theme.mix(tone, Theme.INK_DEEP, 0.5))
			: new GradientPaint(cx - r, cy - r, Theme.STROKE, cx + r, cy + r, Theme.INK_DEEP));
		g.fill(gem);
		g.setStroke(new BasicStroke(1f));
		g.setColor(owned ? Theme.mix(tone, Theme.TEXT, 0.6) : Theme.STROKE);
		g.draw(new java.awt.geom.Line2D.Float(cx - r * 0.8f, cy - r * 0.25f, cx + r * 0.8f, cy - r * 0.25f));
		g.setColor(Theme.INK_DEEP);
		g.draw(gem);

		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();
		String name = Ui.clip(Spoils.shortName(unique), small, w - 8);
		g.setColor(owned ? Theme.TEXT : Theme.TEXT_FAINT);
		g.drawString(name, (w - small.stringWidth(name)) / 2, h - 18);
		String n = owned ? "x" + count : "-";
		g.setColor(owned ? tone : Theme.TEXT_FAINT);
		g.drawString(n, (w - small.stringWidth(n)) / 2, h - 6);
		g.dispose();
	}
}
