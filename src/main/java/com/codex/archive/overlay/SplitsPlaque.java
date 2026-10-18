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
	public Dimension render(Graphics2D g)
	{
		View v = view;
		if (!enabled || v == null || v.live == null || v.archive == null)
		{
			return null;
		}
		Run run = v.live;
		int row = 0;
		while (row < Encounter.ROWS && run.split(row) != null && run.split(row).isDone())
		{
			row++;
		}
		Split split = row < Encounter.ROWS ? run.split(row) : null;
		Run best = Records.best(v.archive.getRuns(), Records.bucket(run.getTeamSize()));
		Integer delta = row > 0 ? Records.delta(run, best, row - 1) : null;

		String room = row >= Encounter.ROWS ? "Cleared" : split == null ? (row == 0 ? "Entering" : "Next room") : split.getEncounter().getShortName();
		String roomClock = row >= Encounter.ROWS ? "" : Format.ticks(split == null ? run.getTicks() - lastEnd(run, row) : split.roomTicks(run.getTicks()));
		String total = Format.ticks(run.getTicks());
		String deltaText = delta == null ? null : Format.delta(delta);

		Theme.smooth(g);
		g.setFont(Theme.title());
		FontMetrics bold = g.getFontMetrics();
		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();
		int icon = 14;
		int line1 = icon + 6 + bold.stringWidth(room) + 10 + small.stringWidth(roomClock);
		int line2 = small.stringWidth("Run " + total) + (deltaText == null ? 0 : 10 + small.stringWidth(deltaText));
		int width = PAD * 2 + Math.max(line1, line2 + icon + 6);
		int height = PAD * 2 + bold.getHeight() + small.getHeight();

		boolean done = row >= Encounter.ROWS;
		Plaque.paint(g, width, height, done ? Theme.alpha(Theme.GOLD, 190) : Theme.alpha(Theme.TEAL, 150));
		int x = PAD;
		int base = PAD + bold.getAscent();
		Sigil.paint(g, x, PAD, icon);
		x += icon + 6;
		g.setFont(Theme.title());
		Ui.shadowed(g, room, x, base, done ? Theme.GOLD_BRIGHT : Theme.mix(Theme.TEAL, Theme.TEXT, 0.5));
		g.setFont(Theme.small());
		Ui.shadowed(g, roomClock, width - PAD - small.stringWidth(roomClock), base, Theme.TEXT);

		int base2 = base + small.getHeight() + 2;
		Ui.shadowed(g, "Run " + total, x, base2, done ? Theme.GOLD : Theme.TEXT_MUTED);
		if (deltaText != null)
		{
			Color c = Theme.delta(delta);
			Ui.shadowed(g, deltaText, width - PAD - small.stringWidth(deltaText), base2, c);
		}
		return new Dimension(width, height);
	}

	/** When the last boss fell, so the walk to the next room has a clock too. */
	private static int lastEnd(Run run, int row)
	{
		Split prev = row > 0 ? run.split(row - 1) : null;
		return prev == null ? 0 : prev.getEndTick();
	}
}
