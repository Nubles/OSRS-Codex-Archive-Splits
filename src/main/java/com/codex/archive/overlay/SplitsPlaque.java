package com.codex.archive.overlay;

import com.codex.archive.core.Encounter;
import com.codex.archive.core.Records;
import com.codex.archive.core.Run;
import com.codex.archive.core.Split;
import com.codex.archive.ui.Format;
import com.codex.archive.ui.Sigil;
import com.codex.archive.ui.Theme;
import com.codex.archive.ui.Ui;
import com.codex.archive.ui.View;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The live plaque during a run: the room being fought, its clock, the run's clock and how the
 * last fallen boss stood against the best. It draws what the plugin hands it.
 */
public class SplitsPlaque extends Overlay
{
	private static final int PAD = 7;
	/** After this long with no boss recognised, the plaque stops calling it the walk in, a minute and a half. */
	private static final int WALK_IN_TICKS = 150;

	private volatile View view;
	private volatile boolean enabled;

	public SplitsPlaque()
	{
		setPosition(OverlayPosition.TOP_LEFT);
	}

	public void show(View view)
	{
		this.view = view;
	}

	public void setEnabled(boolean enabled)
	{
		this.enabled = enabled;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		View v = view;
		if (!enabled || v == null || v.archive == null || v.live == null && v.cleared == null)
		{
			return null;
		}
		Run run = v.live != null ? v.live : v.cleared;
		int row = run.reached();
		boolean done = row >= Encounter.ROWS || run.isCompleted();
		Split split = done ? null : run.split(row);
		Run best = v.liveBest;
		Integer delta;
		if (done)
		{
			delta = best == null ? null : run.getTicks() - best.getTicks();
		}
		else
		{
			delta = row > 0 ? Records.delta(run, best, row - 1) : null;
		}

		String room;
		if (done)
		{
			room = "Cleared";
		}
		else if (split != null)
		{
			room = split.getEncounter().getShortName();
		}
		else if (row > 0)
		{
			room = "Next room";
		}
		else
		{
			// No boss recognised yet: early on that is the walk in; much later, a run the plugin cannot follow by name.
			room = run.getTicks() < WALK_IN_TICKS ? "Entering" : "In the archive";
		}
		String roomClock = done ? (best == null || delta < 0 ? "new best" : "")
			: Format.ticks(split == null ? run.getTicks() - lastEnd(run) : split.roomTicks(run.getTicks()));
		String total = Format.ticks(run.getTicks());
		String deltaText = delta == null ? null : Format.delta(delta);

		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		g.setFont(Theme.title());
		FontMetrics bold = g.getFontMetrics();
		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();
		int icon = 14;
		int line1 = icon + 6 + bold.stringWidth(room) + 12 + small.stringWidth(roomClock);
		int line2 = small.stringWidth("Run " + total) + (deltaText == null ? 0 : 12 + small.stringWidth(deltaText));
		int width = PAD * 2 + Math.max(line1, line2 + icon + 6);
		int height = PAD * 2 + bold.getHeight() + small.getHeight();

		Plaque.paint(g, width, height, done ? Theme.alpha(Theme.GOLD, 190) : Theme.alpha(Theme.TEAL, 150));
		int x = PAD;
		int base = PAD + bold.getAscent();
		Sigil.paint(g, x, PAD, icon);
		x += icon + 6;
		g.setFont(Theme.title());
		Ui.shadowed(g, room, x, base, done ? Theme.GOLD_BRIGHT : Theme.mix(Theme.TEAL, Theme.TEXT, 0.5));
		g.setFont(Theme.small());
		Ui.shadowed(g, roomClock, width - PAD - small.stringWidth(roomClock), base, done ? Theme.GOLD_BRIGHT : Theme.TEXT);

		int base2 = base + small.getHeight() + 2;
		Ui.shadowed(g, "Run " + total, x, base2, done ? Theme.GOLD : Theme.TEXT_MUTED);
		if (deltaText != null)
		{
			Color c = Theme.delta(delta);
			Ui.shadowed(g, deltaText, width - PAD - small.stringWidth(deltaText), base2, c);
		}
		g.dispose();
		return new Dimension(width, height);
	}

	/** When the furthest boss so far fell, so the walk to the next room has a clock too. */
	private static int lastEnd(Run run)
	{
		int end = 0;
		for (Split s : run.getSplits())
		{
			if (s.isDone())
			{
				end = Math.max(end, s.getEndTick());
			}
		}
		return end;
	}
}
