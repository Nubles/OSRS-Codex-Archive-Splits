package com.codex.archive.ui;

import com.codex.archive.core.Outcome;
import com.codex.archive.core.Run;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;

/** One run in the history: when, which doors, the time and how it ended, with a purple gem when it gave a unique. */
class RunRow extends JComponent
{
	/**
	 * The width a row gets in the Records card: the sidebar, less its scroll bar, the page's
	 * gutters and the card's own edge. A long page always scrolls, so the scroll bar is counted.
	 */
	private static final int ROW_WIDTH = 225 - 8 - 2 * Theme.GUTTER - 8;
	private static final int SHORT = 32;
	private static final int TALL = 44;
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
		// A long route takes a second line of its own rather than being cut short.
		// A first guess at the width; once the row is laid out it measures again at its real width.
		setPreferredSize(new Dimension(10, heightAt(ROW_WIDTH)));
		StringBuilder tip = new StringBuilder("<html>").append(Format.day(run.getStartedAt())).append(" at ")
			.append(Format.time(run.getStartedAt())).append(run.getKc() > 0 ? ", completion " + Format.count(run.getKc()) : "");
		if (run.getRole() != null)
		{
			tip.append("<br>Role: ").append(Ui.escape(run.getRole()));
		}
		if (!run.getCrew().isEmpty())
		{
			tip.append("<br>With ").append(Ui.escape(String.join(", ", run.getCrew())));
		}
		if (run.getNote() != null)
		{
			tip.append("<br>").append(Ui.escape(run.getNote()));
		}
		setToolTipText(tip.append("</html>").toString());
	}

	/** Two lines when the route does not fit beside the time at this width, one when it does. */
	private int heightAt(int width)
	{
		int room = width - 6 - (run.getPurple() != null ? 14 : 0) - getFontMetrics(Theme.body()).stringWidth(timeText()) - 12;
		return routeLines(getFontMetrics(Theme.small()), room).size() > 1 ? TALL : SHORT;
	}

	@Override
	public void setBounds(int x, int y, int width, int height)
	{
		super.setBounds(x, y, width, height);
		int want = width > 0 ? heightAt(width) : getPreferredSize().height;
		if (want != getPreferredSize().height)
		{
			// The guess was wrong for the width the row really got: take the right height at the next layout.
			setPreferredSize(new Dimension(10, want));
			revalidate();
		}
	}

	/** The time on the right: the clear's time, or how the run ended. */
	private String timeText()
	{
		if (run.getOutcome() == Outcome.COMPLETED)
		{
			return Format.ticks(run.getTicks());
		}
		return run.getOutcome() == Outcome.WIPED ? "Wiped" : "Left";
	}

	/**
	 * The route on one line when it fits, else on two broken between doors, the second cut short
	 * only if a single door's name is longer than the line.
	 */
	List<String> routeLines(FontMetrics fm, int width)
	{
		String route = run.route().isEmpty() ? "Guardian" : run.route();
		List<String> lines = new ArrayList<>();
		if (fm.stringWidth(route) <= width)
		{
			lines.add(route);
			return lines;
		}
		String[] doors = route.split(", ");
		StringBuilder first = new StringBuilder(doors[0]);
		int i = 1;
		while (i < doors.length && fm.stringWidth(first + ", " + doors[i] + ",") <= width)
		{
			first.append(", ").append(doors[i]);
			i++;
		}
		if (i >= doors.length)
		{
			lines.add(first.toString());
			return lines;
		}
		lines.add(Ui.clip(first + ",", fm, width));
		StringBuilder rest = new StringBuilder(doors[i]);
		for (int k = i + 1; k < doors.length; k++)
		{
			rest.append(", ").append(doors[k]);
		}
		lines.add(rest.toString());
		return lines;
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

		String time = timeText();
		Color color;
		if (run.getOutcome() == Outcome.COMPLETED)
		{
			color = best ? Theme.GOLD_BRIGHT : Theme.TEXT;
		}
		else
		{
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
		g.drawString(note, w - 6 - small.stringWidth(note), h >= TALL ? 27 : h - 6);

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
		List<String> route = h >= TALL ? routeLines(small, w - x - tw - 12)
			: java.util.Collections.singletonList(Ui.clip(run.route().isEmpty() ? "Guardian" : run.route(), small, w - x - tw - 12));
		Ui.shadowed(g, route.get(0), x, 14, Theme.TEXT);
		if (route.size() > 1)
		{
			// The second line runs beside the note under the time, so it stops short of it.
			Ui.shadowed(g, Ui.clip(route.get(1), small, w - x - Math.max(tw, small.stringWidth(note)) - 12), x, 27, Theme.TEXT);
		}
		g.setFont(Theme.small());
		g.setColor(Theme.TEXT_FAINT);
		String when = Format.ago(now - run.getStartedAt());
		if (run.getRole() != null)
		{
			when += ", " + run.getRole().toLowerCase();
		}
		if (run.getNote() != null)
		{
			when += ", " + run.getNote();
		}
		if (run.getPurple() != null)
		{
			when = run.getPurple() + ", " + when;
			g.setColor(Theme.mix(Theme.PURPLE, Theme.TEXT_FAINT, 0.3));
		}
		// On a two-line route the note sits beside the route, so this last line has the row to itself.
		g.drawString(Ui.clip(when, small, h >= TALL ? w - x - 6 : w - x - 70), x, h - 6);
		g.dispose();
	}
}
