package com.codex.archive.ui;

import com.codex.archive.core.Outcome;
import com.codex.archive.core.Run;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import javax.swing.JComponent;

/** One run in the history: when, which doors, the time and how it ended, with a purple gem when it gave a unique. */
class RunRow extends JComponent
{
	private final Run run;
	private final boolean best;
	/** The party size's best time, for the gap under each clear; -1 with none. */
	private final int bestTicks;
	private final long now;

	RunRow(Run run, boolean best, int bestTicks, long now)
	{
		this.run = run;
		this.best = best;
		this.bestTicks = bestTicks;
		this.now = now;
		setOpaque(false);
		setPreferredSize(new Dimension(10, 32));
		setToolTipText(Format.day(run.getStartedAt()) + " at " + Format.time(run.getStartedAt())
			+ (run.getKc() > 0 ? ", completion " + Format.count(run.getKc()) : ""));
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int w = getWidth();
		int h = getHeight();
		g.setColor(Theme.STROKE_SOFT);
		g.drawLine(4, h - 1, w - 4, h - 1);

		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();
		g.setFont(Theme.body());
		FontMetrics body = g.getFontMetrics();

		String time;
		Color color;
		if (run.getOutcome() == Outcome.COMPLETED)
		{
			time = Format.ticks(run.getTicks());
			color = best ? Theme.GOLD_BRIGHT : Theme.TEXT;
		}
		else
		{
			time = run.getOutcome() == Outcome.WIPED ? "Wiped" : "Left";
			color = run.getOutcome() == Outcome.WIPED ? Theme.BLOOD : Theme.TEXT_MUTED;
		}
		int tw = body.stringWidth(time);
		Ui.shadowed(g, time, w - 6 - tw, 14, color);
		// Under the time: the best itself, or how far off it a clear was. The page is one party size, so it needs no saying.
		g.setFont(Theme.small());
		String note = "";
		Color noteColor = Theme.TEXT_FAINT;
		if (best)
		{
			note = "best";
			noteColor = Theme.GOLD;
		}
		else if (run.isCompleted() && bestTicks >= 0)
		{
			note = Format.delta(run.getTicks() - bestTicks);
			noteColor = Theme.mix(Theme.BEHIND, Theme.TEXT_FAINT, 0.45);
		}
		else if (!run.isCompleted())
		{
			note = "at " + Format.ticks(run.getTicks());
		}
		g.setColor(noteColor);
		g.drawString(note, w - 6 - small.stringWidth(note), h - 6);

		int x = 6;
		if (run.getPurple() != null)
		{
			Color gem = Theme.PURPLE;
			g.setColor(Theme.alpha(gem, 70));
			g.fill(new Ellipse2D.Float(x - 2, 6, 12, 12));
			g.setColor(gem);
			g.fill(new Ellipse2D.Float(x + 1, 9, 6, 6));
			x += 14;
		}
		g.setFont(Theme.small());
		String route = run.route().isEmpty() ? "Guardian" : run.route();
		Ui.shadowed(g, Ui.clip(route, small, w - x - tw - 12), x, 14, Theme.TEXT);
		g.setFont(Theme.small());
		g.setColor(Theme.TEXT_FAINT);
		String when = Format.ago(now - run.getStartedAt());
		if (run.getPurple() != null)
		{
			when = run.getPurple() + ", " + when;
			g.setColor(Theme.mix(Theme.PURPLE, Theme.TEXT_FAINT, 0.3));
		}
		g.drawString(Ui.clip(when, small, w - x - 70), x, h - 6);
		g.dispose();
	}
}
