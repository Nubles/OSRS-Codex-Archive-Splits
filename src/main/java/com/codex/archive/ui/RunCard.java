package com.codex.archive.ui;

import com.codex.archive.core.Encounter;
import com.codex.archive.core.Outcome;
import com.codex.archive.core.Records;
import com.codex.archive.core.Run;
import com.codex.archive.core.Split;
import com.codex.archive.core.Standing;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Random;

/**
 * The run card: one run of the Fractured Archive on a gilt page, to save or paste into a chat.
 * The time large, how it stood against the best before it, and the five bosses down a thread,
 * each with its room time, the run's time at its fall and the gap to the best run there, and a
 * gold mark on every room that was the quickest yet. Drawn at twice the panel's scale so the
 * game's pixel fonts stay crisp.
 */
public final class RunCard
{
	static final int WIDTH = 360;
	static final int SCALE = 2;
	private static final int PAD = 20;
	private static final int ROW = 40;
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH);

	private RunCard()
	{
	}

	/** @param player the name to put on the card, or null to leave it off */
	public static BufferedImage render(Standing st, String player, ZoneId zone)
	{
		return render(st, player, true, zone);
	}

	/**
	 * @param player the name to put on the card, or null to leave it off
	 * @param names false to leave every name off, the player's and the party's, for a card shared without them
	 */
	public static BufferedImage render(Standing st, String player, boolean names, ZoneId zone)
	{
		if (!names)
		{
			player = null;
		}
		BufferedImage scratch = new BufferedImage(WIDTH, 1200, BufferedImage.TYPE_INT_ARGB);
		Graphics2D sg = scratch.createGraphics();
		int height = content(sg, st, player, names, zone);
		sg.dispose();

		BufferedImage image = new BufferedImage(WIDTH * SCALE, height * SCALE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.scale(SCALE, SCALE);
		Theme.smooth(g);
		frame(g, height, st.isNewBest());
		content(g, st, player, names, zone);
		g.dispose();
		return image;
	}

	private static int content(Graphics2D g, Standing st, String player, boolean names, ZoneId zone)
	{
		Theme.smooth(g);
		Run run = st.getRun();
		int x = PAD;
		int w = WIDTH - PAD * 2;
		int y = PAD + 4;

		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();
		Sigil.paint(g, x, y - 2, 14);
		g.setColor(Theme.GOLD);
		g.drawString("CODEX ARCHIVE SPLITS", x + 19, y + small.getAscent() - 1);
		String place = "FRACTURED ARCHIVE";
		g.setColor(Theme.TEXT_MUTED);
		g.drawString(place, x + w - small.stringWidth(place), y + small.getAscent() - 1);
		y += small.getHeight() + 10;

		// The time itself, large; a wipe's in ember, since it is where the run stopped.
		boolean cleared = run.getOutcome() == Outcome.COMPLETED;
		Font huge = Theme.title().deriveFont(Theme.title().getSize2D() * 3.5f);
		g.setFont(huge);
		FontMetrics hm = g.getFontMetrics();
		String time = Format.ticks(run.getTicks());
		// Set glyph by glyph: the pixel font gives a colon and a point a digit's spacing, which at
		// this size opens a gap ("6 :54.0"); they sit close to their figures instead.
		Tight.Layout set = Tight.layout(g, time);
		float tx = x + (w - set.width) / 2f;
		int base = y + hm.getAscent() - 10;
		g.setColor(Color.BLACK);
		set.draw(g, tx + 2, base + 2);
		Color top = cleared ? Theme.GOLD_BRIGHT : Theme.mix(Theme.EMBER, Theme.TEXT, 0.25);
		Color bottom = cleared ? Theme.GOLD_DIM : Theme.mix(Theme.EMBER, Theme.INK, 0.35);
		g.setPaint(new GradientPaint(0, base - hm.getAscent() + 12, top, 0, base, bottom));
		set.draw(g, tx, base);
		y = base + 10;

		// The ribbon: what the run was, in a word or two.
		ribbon(g, verdict(st), ribbonColor(st), x + w / 2, y);
		y += 24;

		g.setFont(Theme.small());
		String party = Records.BUCKETS[Records.bucket(run.getTeamSize())];
		StringBuilder line = new StringBuilder(party);
		if (run.getKc() > 0)
		{
			line.append(" · KC ").append(Format.count(run.getKc()));
		}
		line.append(" · ").append(DATE.format(Instant.ofEpochMilli(run.getStartedAt()).atZone(zone)));
		centered(g, Ui.clip(line.toString(), small, w), x, w, y + small.getAscent(), Theme.TEXT_MUTED);
		y += small.getHeight() + 2;
		if (player != null && !player.isEmpty())
		{
			g.setFont(Theme.title());
			FontMetrics bold = g.getFontMetrics();
			centered(g, Ui.clip(player, bold, w), x, w, y + bold.getAscent(), Theme.TEXT);
			y += bold.getHeight();
		}
		y += 6;
		rule(g, x, y, w);
		y += 12;

		// The thread of bosses.
		int nodeX = x + 10;
		int rows = 0;
		for (int row = 0; row < Encounter.ROWS; row++)
		{
			rows += show(run, row) ? 1 : 0;
		}
		int shown = 0;
		for (int row = 0; row < Encounter.ROWS; row++)
		{
			if (!show(run, row))
			{
				continue;
			}
			Split s = run.split(row);
			boolean done = s != null && s.isDone();
			boolean gold = done && st.isGold(row);
			int ny = y + 9;
			g.setStroke(new BasicStroke(1.2f));
			g.setColor(Theme.alpha(Theme.GOLD_DIM, 170));
			if (shown > 0)
			{
				g.drawLine(nodeX, y - 4, nodeX, ny);
			}
			if (shown < rows - 1)
			{
				g.drawLine(nodeX, ny, nodeX, y + ROW - 4);
			}
			node(g, nodeX, ny, gold, done);

			String name = s == null || s.getEncounter() == null ? Encounter.rowLabel(row) : s.getEncounter().getTitle();
			int nx = nodeX + 14;
			g.setFont(Theme.title());
			FontMetrics bold = g.getFontMetrics();
			int right = x + w;
			String end = done ? Format.ticks(s.getEndTick()) : run.getOutcome() == Outcome.WIPED && row == run.reached() ? "wiped" : "not timed";
			g.setFont(Theme.body());
			FontMetrics body = g.getFontMetrics();
			int ew = body.stringWidth(end);
			Ui.shadowed(g, end, right - ew, ny + body.getAscent() / 2 - 1, done ? Theme.TEXT : Theme.EMBER);
			g.setFont(Theme.title());
			Ui.shadowed(g, Ui.clip(name, bold, right - ew - 10 - nx), nx, ny + bold.getAscent() / 2 - 1, gold ? Theme.GOLD_BRIGHT : Theme.TEXT);

			g.setFont(Theme.small());
			if (done)
			{
				String room = "room " + Format.ticks(s.roomTicks(0));
				g.setColor(Theme.TEXT_MUTED);
				int sy = ny + body.getAscent() / 2 + small.getHeight() + 1;
				g.drawString(room, nx, sy);
				if (gold)
				{
					int gx = nx + small.stringWidth(room) + 6;
					goldTag(g, gx, sy - small.getAscent() + 1, "GOLD");
				}
				Integer delta = st.delta(row);
				if (delta != null)
				{
					SplitTable.paintPill(g, Format.delta(delta), Theme.delta(delta), right, sy - small.getAscent() - 1);
				}
			}
			y += ROW;
			shown++;
		}
		y += 2;
		rule(g, x, y, w);
		y += 10;

		// Three figures beneath: golds, deaths, and its place among the clears.
		int tileW = (w - 12) / 3;
		int tileH = 50;
		int compared = st.getComparedRooms();
		StatTile.paint(g, compared == 0 ? "-" : st.getGoldCount() + " of " + compared, "Golds", compared == 0 ? "first time" : "quickest yet",
			Theme.GOLD, x, y, tileW, tileH);
		StatTile.paint(g, String.valueOf(run.getDeaths()), "Deaths", run.getDeaths() == 0 ? "deathless" : "in the run",
			run.getDeaths() == 0 ? Theme.GREEN : Theme.EMBER, x + tileW + 6, y, tileW, tileH);
		if (st.getClearNumber() > 0)
		{
			StatTile.paint(g, Format.ordinal(st.getClearNumber()), "Clear", Format.clear(party),
				Theme.TEAL, x + (tileW + 6) * 2, y, w - (tileW + 6) * 2, tileH);
		}
		else
		{
			// A run that did not clear: how far down the archive it got.
			StatTile.paint(g, Math.min(run.reached(), Encounter.ROWS) + " of " + Encounter.ROWS, "Bosses", "down",
				Theme.EMBER, x + (tileW + 6) * 2, y, w - (tileW + 6) * 2, tileH);
		}
		y += tileH + 10;

		if (names && !run.getCrew().isEmpty())
		{
			g.setFont(Theme.small());
			String with = "With " + crew(run);
			centered(g, Ui.clip(with, small, w), x, w, y + small.getAscent(), Theme.TEXT_MUTED);
			y += small.getHeight() + 6;
		}

		rule(g, x, y, w);
		y += 8;
		g.setFont(Theme.small());
		g.setColor(Theme.TEXT_FAINT);
		g.drawString("Codex Archive Splits for RuneLite", x, y + small.getAscent());
		String route = run.route();
		if (!route.isEmpty())
		{
			int room = w - small.stringWidth("Codex Archive Splits for RuneLite") - 12;
			String r = Ui.clip(route, small, room);
			g.drawString(r, x + w - small.stringWidth(r), y + small.getAscent());
		}
		y += small.getHeight();
		return y + PAD;
	}

	/** A row goes on the card when it was timed, or when the run passed it, or stopped there. */
	private static boolean show(Run run, int row)
	{
		Split s = run.split(row);
		return (s != null && s.isDone()) || row < run.reached() || (run.getOutcome() == Outcome.WIPED && row == run.reached());
	}

	/** "New personal best, 31.2s off", "First trio clear", "42.0s behind your best", "Wiped at Xarth". */
	static String verdict(Standing st)
	{
		Run run = st.getRun();
		if (run.getOutcome() != Outcome.COMPLETED)
		{
			Split last = null;
			for (int row = 0; row < Encounter.ROWS; row++)
			{
				Split s = run.split(row);
				last = s != null && s.getEncounter() != null && s.getEncounter().getRow() == run.reached() ? s : last;
			}
			// "The Trio" on its own would read as a party size beside a duo's or a trio's runs.
			String where = last == null ? (run.reached() < Encounter.ROWS ? Encounter.rowLabel(run.reached()) : null)
				: last.getEncounter() == Encounter.TRIO ? "the Trio" : last.getEncounter().getShortName();
			return (run.getOutcome() == Outcome.WIPED ? "Wiped" : "Left") + (where == null ? "" : " at " + where);
		}
		if (st.isFirstClear())
		{
			return "First " + Format.clear(Records.BUCKETS[Records.bucket(run.getTeamSize())]);
		}
		int delta = st.totalDelta();
		if (delta < 0)
		{
			return "New personal best, " + Format.span(-delta) + " off";
		}
		// "Off" is kept for time taken off a best; a slower run is behind it, in the same seconds.
		return delta == 0 ? "Level with your best" : Format.span(delta) + " behind your best";
	}

	private static Color ribbonColor(Standing st)
	{
		if (st.getRun().getOutcome() != Outcome.COMPLETED)
		{
			return Theme.EMBER;
		}
		return st.isNewBest() ? Theme.GOLD_BRIGHT : Theme.TEXT_MUTED;
	}

	private static String crew(Run run)
	{
		java.util.List<String> names = run.getCrew();
		if (names.size() <= 3)
		{
			return String.join(", ", names);
		}
		return String.join(", ", names.subList(0, 3)) + " and " + (names.size() - 3) + " more";
	}

	static void ribbon(Graphics2D g, String text, Color color, int cx, int y)
	{
		g.setFont(Theme.body());
		FontMetrics fm = g.getFontMetrics();
		int tw = fm.stringWidth(text);
		int pw = tw + 34;
		int ph = 18;
		RoundRectangle2D shape = new RoundRectangle2D.Float(cx - pw / 2f, y, pw, ph, ph, ph);
		g.setColor(Theme.alpha(color, 30));
		g.fill(shape);
		g.setStroke(new BasicStroke(1f));
		g.setColor(Theme.alpha(color, 150));
		g.draw(shape);
		diamond(g, cx - pw / 2f + 9, y + ph / 2f, 2.4f, color);
		diamond(g, cx + pw / 2f - 9, y + ph / 2f, 2.4f, color);
		Ui.shadowed(g, text, cx - tw / 2, y + (ph + fm.getAscent()) / 2 - 2, color);
	}

	private static void goldTag(Graphics2D g, int x, int y, String text)
	{
		FontMetrics fm = g.getFontMetrics();
		int w = fm.stringWidth(text) + 8;
		int h = fm.getAscent() + 2;
		RoundRectangle2D tag = new RoundRectangle2D.Float(x, y, w, h, 4, 4);
		g.setColor(Theme.alpha(Theme.GOLD, 50));
		g.fill(tag);
		g.setColor(Theme.alpha(Theme.GOLD, 160));
		g.draw(tag);
		g.setColor(Theme.GOLD_BRIGHT);
		g.drawString(text, x + 4, y + fm.getAscent());
	}

	/** A node on the thread: a gold room shines, a timed one is gilt, an untimed one hollow. */
	private static void node(Graphics2D g, float x, float y, boolean gold, boolean done)
	{
		if (gold)
		{
			g.setColor(Theme.alpha(Theme.GOLD, 60));
			g.fill(new Ellipse2D.Float(x - 7.5f, y - 7.5f, 15, 15));
			g.setColor(Theme.alpha(Theme.GOLD_BRIGHT, 170));
			g.draw(new Ellipse2D.Float(x - 6.5f, y - 6.5f, 13, 13));
		}
		g.setColor(Theme.INK_DEEP);
		g.fill(new Ellipse2D.Float(x - 5, y - 5, 10, 10));
		if (done)
		{
			diamond(g, x, y, gold ? 4.5f : 3.8f, gold ? Theme.GOLD_BRIGHT : Theme.GOLD);
		}
		else
		{
			g.setStroke(new BasicStroke(1f));
			g.setColor(Theme.EMBER);
			Path2D d = new Path2D.Float();
			d.moveTo(x, y - 3.5f);
			d.lineTo(x + 3.5f, y);
			d.lineTo(x, y + 3.5f);
			d.lineTo(x - 3.5f, y);
			d.closePath();
			g.draw(d);
		}
	}

	static void frame(Graphics2D g, int h, boolean best)
	{
		int w = WIDTH;
		RoundRectangle2D page = new RoundRectangle2D.Float(0, 0, w, h, 18, 18);
		g.setPaint(new GradientPaint(0, 0, Theme.mix(Theme.INK, Theme.CARD_TOP, 0.5), 0, h, Theme.INK_DEEP));
		g.fill(page);
		g.setClip(page);
		g.setPaint(new RadialGradientPaint(w / 2f, 64, w * 0.75f, new float[]{0f, 1f},
			new Color[]{Theme.alpha(best ? Theme.GOLD_BRIGHT : Theme.GOLD, best ? 74 : 50), Theme.alpha(Theme.GOLD, 0)}));
		g.fillRect(0, 0, w, h);
		// A faint grain, the same every time, so the page reads as vellum rather than a flat fill.
		Random grain = new Random(17);
		for (int i = 0; i < w * h / 26; i++)
		{
			g.setColor(Theme.alpha(grain.nextBoolean() ? Theme.GOLD_BRIGHT : Color.BLACK, 6 + grain.nextInt(8)));
			g.fillRect(grain.nextInt(w), grain.nextInt(h), 1, 1);
		}
		// The mark engraved faintly in the corner: lines only, so it never muddies what sits over it.
		Sigil.engrave(g, w - 196, h - 206, 180, Theme.alpha(Theme.GOLD, 16));
		g.setClip(null);
		g.setStroke(new BasicStroke(1f));
		g.setColor(Theme.alpha(Theme.GOLD, 190));
		g.draw(new RoundRectangle2D.Float(4.5f, 4.5f, w - 9, h - 9, 14, 14));
		g.setColor(Theme.alpha(Theme.GOLD_DIM, 120));
		g.draw(new RoundRectangle2D.Float(8.5f, 8.5f, w - 17, h - 17, 10, 10));
		float[][] corners = {{14, 14}, {w - 14, 14}, {14, h - 14}, {w - 14, h - 14}};
		for (float[] c : corners)
		{
			diamond(g, c[0], c[1], 3.5f, Theme.GOLD);
		}
	}

	static void centered(Graphics2D g, String text, int x, int w, int baseline, Color color)
	{
		FontMetrics fm = g.getFontMetrics();
		Ui.shadowed(g, text, x + (w - fm.stringWidth(text)) / 2, baseline, color);
	}

	static void rule(Graphics2D g, int x, int y, int w)
	{
		g.setStroke(new BasicStroke(1f));
		g.setPaint(new GradientPaint(x, 0, Theme.alpha(Theme.GOLD, 0), x + w / 2f, 0, Theme.alpha(Theme.GOLD, 150), true));
		g.drawLine(x, y, x + w, y);
		diamond(g, x + w / 2f, y, 3, Theme.GOLD);
	}

	static void diamond(Graphics2D g, float cx, float cy, float r, Color color)
	{
		Path2D d = new Path2D.Float();
		d.moveTo(cx, cy - r);
		d.lineTo(cx + r, cy);
		d.lineTo(cx, cy + r);
		d.lineTo(cx - r, cy);
		d.closePath();
		g.setColor(color);
		g.fill(d);
	}
}
