package com.codex.archive.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JComponent;

/**
 * One row of the raid on the records page: its best room, and under it a bar for what a
 * typical run lately gives away against that best. The row losing most is lit in ember.
 */
class RoomLine extends JComponent
{
	private final String label;
	private final int best;
	private final int typical;
	/** The biggest loss on the page, which fills a bar. */
	private final int scale;
	private final boolean worst;

	RoomLine(String label, int best, int typical, int scale, boolean worst)
	{
		this.label = label;
		this.best = best;
		this.typical = typical;
		this.scale = scale;
		this.worst = worst;
		setOpaque(false);
		setPreferredSize(new Dimension(10, typical >= 0 ? 30 : 18));
		if (typical >= 0)
		{
			setToolTipText("Best " + Format.ticks(best) + ", typical lately " + Format.ticks(typical) + ".");
		}
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int w = getWidth();
		g.setFont(Theme.body());
		FontMetrics body = g.getFontMetrics();
		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();
		int base = body.getAscent();
		String time = best < 0 ? "-" : Format.ticks(best);
		g.setColor(worst ? Theme.mix(Theme.EMBER, Theme.TEXT_MUTED, 0.3) : Theme.TEXT_MUTED);
		g.drawString(Ui.clip(label, small, w - body.stringWidth(time) - 10), 0, base);
		g.setFont(Theme.body());
		Ui.shadowed(g, time, w - body.stringWidth(time), base, best < 0 ? Theme.TEXT_FAINT : Theme.TEXT);
		if (typical < 0 || best < 0)
		{
			g.dispose();
			return;
		}
		int loss = Math.max(0, typical - best);
		g.setFont(Theme.small());
		String note = loss == 0 ? "typical is your best" : "typical " + Format.delta(loss);
		int nw = small.stringWidth(note);
		int barY = base + 7;
		int barW = Math.max(10, w - nw - 8);
		RoundRectangle2D track = new RoundRectangle2D.Float(0, barY, barW, 5, 5, 5);
		g.setColor(Theme.INK_DEEP);
		g.fill(track);
		Color fill = worst ? Theme.EMBER : Theme.mix(Theme.GOLD_DIM, Theme.GOLD, 0.5);
		if (scale > 0 && loss > 0)
		{
			float fw = Math.max(5, barW * Math.min(1f, loss / (float) scale));
			g.setColor(Theme.alpha(fill, 210));
			g.fill(new RoundRectangle2D.Float(0, barY, fw, 5, 5, 5));
		}
		g.setColor(Theme.STROKE_SOFT);
		g.draw(track);
		g.setColor(worst ? Theme.EMBER : Theme.TEXT_FAINT);
		g.drawString(note, w - nw, barY + 5);
		g.dispose();
	}
}
