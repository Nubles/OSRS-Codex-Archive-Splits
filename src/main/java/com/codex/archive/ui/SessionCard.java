package com.codex.archive.ui;

import com.codex.archive.core.Encounter;
import com.codex.archive.core.Records;
import com.codex.archive.core.Run;
import com.codex.archive.core.Sessions;
import com.codex.archive.core.Split;
import com.codex.archive.core.Standing;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The session card: one sitting in the Fractured Archive on the run card's gilt page. The sitting's
 * best clear large, a ribbon for what the sitting brought (a purple, a new best, how close it came
 * to the best), then every run as a bar of its time against the sitting's slowest clear, the best
 * lit, with its tags: a new best, a first clear, a purple, where a wipe stopped. Beneath, the
 * average clear, the pace and the time between runs.
 */
public final class SessionCard
{
	/** Runs drawn on the card; a longer sitting shows its latest. */
	static final int RUNS = 12;
	private static final int PAD = 20;
	private static final int ROW = 18;
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH);

	private SessionCard()
	{
	}

	/**
	 * @param all every run kept, so each run of the sitting is measured against those before it
	 * @param player the name to put on the card, or null to leave it off
	 */
	public static BufferedImage render(Sessions.Session s, List<Run> all, String player, ZoneId zone)
	{
		List<Row> rows = rows(s, all);
		BufferedImage scratch = new BufferedImage(RunCard.WIDTH, 1200, BufferedImage.TYPE_INT_ARGB);
		Graphics2D sg = scratch.createGraphics();
		int height = content(sg, s, rows, all, player, zone);
		sg.dispose();

		BufferedImage image = new BufferedImage(RunCard.WIDTH * RunCard.SCALE, height * RunCard.SCALE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.scale(RunCard.SCALE, RunCard.SCALE);
		Theme.smooth(g);
		RunCard.frame(g, height, s.getBests() > 0);
		content(g, s, rows, all, player, zone);
		g.dispose();
		return image;
	}

	/** One run of the sitting as the card tells it. */
	static final class Row
	{
		final Run run;
		/** "PB", "FIRST", or null. */
		final String tag;
		/** What the line ends with: a purple's name, or null. A run that did not clear says where it stopped in place of its time. */
		final String end;

		Row(Run run, String tag, String end)
		{
			this.run = run;
			this.tag = tag;
			this.end = end;
		}
	}

	static List<Row> rows(Sessions.Session s, List<Run> all)
	{
		List<Row> out = new ArrayList<>();
		for (Run r : s.getRuns())
		{
			Standing st = Standing.of(all, r);
			String tag = !r.isCompleted() ? null : st.isFirstClear() ? "FIRST" : st.isNewBest() ? "PB" : null;
			String end = r.getPurple();
			out.add(new Row(r, tag, end));
		}
		return out;
	}

	/**
	 * What the sitting brought, in a few words, never a figure the card shows elsewhere: its purples
	 * first, then its new bests, then how near its best clear came to the best before it.
	 */
	static String verdict(Sessions.Session s, List<Run> all)
	{
		List<String> purples = new ArrayList<>();
		for (Run r : s.getRuns())
		{
			if (r.getPurple() != null)
			{
				purples.add(r.getPurple());
			}
		}
		if (purples.size() == 1)
		{
			return "A purple: " + purples.get(0);
		}
		if (purples.size() > 1)
		{
			return purples.size() + " purples in one sitting";
		}
		int bests = 0;
		Run first = null;
		for (Run r : s.getRuns())
		{
			Standing st = r.isCompleted() ? Standing.of(all, r) : null;
			if (st != null && st.isFirstClear())
			{
				first = first == null ? r : first;
			}
			else if (st != null && st.isNewBest())
			{
				bests++;
			}
		}
		if (bests > 0)
		{
			return bests == 1 ? "A new personal best" : bests + " new personal bests";
		}
		if (first != null)
		{
			return "A first " + Format.clear(Records.BUCKETS[Records.bucket(first.getTeamSize())]);
		}
		if (s.getClears() == 0)
		{
			String furthest = furthest(s);
			return furthest == null ? "No clear yet" : "Furthest: " + furthest;
		}
		Run best = bestRun(s);
		Integer delta = best == null ? null : Standing.of(all, best).totalDelta();
		if (delta != null && delta > 0)
		{
			return "Within " + Format.span(delta) + " of your best";
		}
		return s.getWipes() == 0 ? "Every run cleared" : "A session in the archive";
	}

	/** The deepest boss the sitting's runs reached, by its short name, or null. */
	static String furthest(Sessions.Session s)
	{
		int deepest = -1;
		String name = null;
		for (Run r : s.getRuns())
		{
			int row = r.reached();
			for (int i = 0; i < Encounter.ROWS; i++)
			{
				Split sp = r.split(i);
				if (sp != null && sp.getEncounter() != null && sp.getEncounter().getRow() == row && row > deepest)
				{
					deepest = row;
					name = sp.getEncounter().getShortName();
				}
			}
		}
		return name;
	}

	/** The sitting's quickest clear, or null with none. */
	static Run bestRun(Sessions.Session s)
	{
		Run best = null;
		for (Run r : s.getRuns())
		{
			if (r.isCompleted() && (best == null || r.getTicks() < best.getTicks()))
			{
				best = r;
			}
		}
		return best;
	}

	/** "4 clears, 1 wipe", "1 clear, no wipes". */
	static String tally(Sessions.Session s)
	{
		String clears = s.getClears() + (s.getClears() == 1 ? " clear" : " clears");
		String wipes = s.getWipes() + (s.getWipes() == 1 ? " wipe" : " wipes");
		if (s.getClears() == 0)
		{
			return wipes;
		}
		return clears + ", " + (s.getWipes() == 0 ? "no wipes" : wipes);
	}

	/** The party the sitting was raided in: "Trio", or "Mixed parties". */
	static String party(Sessions.Session s)
	{
		int bucket = -1;
		for (Run r : s.getRuns())
		{
			int b = Records.bucket(r.getTeamSize());
			if (bucket >= 0 && b != bucket)
			{
				return "Mixed parties";
			}
			bucket = b;
		}
		return bucket < 0 ? "" : Records.BUCKETS[bucket];
	}

	private static int content(Graphics2D g, Sessions.Session s, List<Row> rows, List<Run> all, String player, ZoneId zone)
	{
		Theme.smooth(g);
		int x = PAD;
		int w = RunCard.WIDTH - PAD * 2;
		int y = PAD + 4;

		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();
		Sigil.paint(g, x, y - 2, 14);
		g.setColor(Theme.GOLD);
		g.drawString("CODEX ARCHIVE SPLITS", x + 19, y + small.getAscent() - 1);
		String corner = "A SESSION";
		g.setColor(Theme.TEXT_MUTED);
		g.drawString(corner, x + w - small.stringWidth(corner), y + small.getAscent() - 1);
		y += small.getHeight() + 10;

		// The sitting's best clear, large; with none, No clear in ember.
		Run best = bestRun(s);
		Font huge = Theme.title().deriveFont(Theme.title().getSize2D() * 3.5f);
		g.setFont(huge);
		FontMetrics hm = g.getFontMetrics();
		String big = best != null ? Format.ticks(best.getTicks()) : "No clear";
		Tight.Layout set = Tight.layout(g, big);
		float tx = x + (w - set.width) / 2f;
		int base = y + hm.getAscent() - 10;
		g.setColor(Color.BLACK);
		set.draw(g, tx + 2, base + 2);
		Color top = best != null ? Theme.GOLD_BRIGHT : Theme.mix(Theme.EMBER, Theme.TEXT, 0.25);
		Color bottom = best != null ? Theme.GOLD_DIM : Theme.mix(Theme.EMBER, Theme.INK, 0.35);
		g.setPaint(new GradientPaint(0, base - hm.getAscent() + 12, top, 0, base, bottom));
		set.draw(g, tx, base);
		y = base + 2;
		if (best != null)
		{
			g.setFont(Theme.small());
			RunCard.centered(g, "BEST CLEAR", x, w, y + small.getAscent(), Theme.TEXT_MUTED);
			y += small.getHeight() + 4;
		}
		else
		{
			y += 8;
		}

		g.setFont(Theme.body());
		// A long unique's name still leaves the ribbon its ends inside the page.
		String verdict = Ui.clip(verdict(s, all), g.getFontMetrics(), w - 40);
		Color ribbon = verdict.startsWith("A purple") || verdict.contains("purples") ? Theme.PURPLE
			: s.getBests() > 0 ? Theme.GOLD_BRIGHT : s.getClears() == 0 ? Theme.EMBER : Theme.TEXT_MUTED;
		RunCard.ribbon(g, verdict, ribbon, x + w / 2, y);
		y += 24;

		g.setFont(Theme.small());
		String when = Instant.ofEpochMilli(s.getStart()).atZone(zone).format(DATE) + ", " + Format.time(s.getStart()) + " to " + Format.time(s.getEnd());
		String party = party(s);
		String line = party.isEmpty() ? when : party + " · " + when;
		RunCard.centered(g, Ui.clip(line, small, w), x, w, y + small.getAscent(), Theme.TEXT_MUTED);
		y += small.getHeight() + 2;
		if (player != null && !player.isEmpty())
		{
			g.setFont(Theme.title());
			FontMetrics bold = g.getFontMetrics();
			RunCard.centered(g, Ui.clip(player, bold, w), x, w, y + bold.getAscent(), Theme.TEXT);
			y += bold.getHeight();
		}
		y += 6;
		RunCard.rule(g, x, y, w);
		y += 10;

		// Every run, a bar for its time against the sitting's slowest clear.
		g.setFont(Theme.small());
		g.setColor(Theme.GOLD);
		g.drawString("THE RUNS", x, y + small.getAscent());
		String tally = tally(s);
		g.setColor(Theme.TEXT_MUTED);
		g.drawString(tally, x + w - small.stringWidth(tally), y + small.getAscent());
		y += small.getHeight() + 6;

		int slowest = 0;
		for (Row r : rows)
		{
			slowest = r.run.isCompleted() ? Math.max(slowest, r.run.getTicks()) : slowest;
		}
		int from = Math.max(0, rows.size() - RUNS);
		if (from > 0)
		{
			String earlier = from + (from == 1 ? " earlier run" : " earlier runs") + " not shown";
			RunCard.centered(g, earlier, x, w, y + small.getAscent(), Theme.TEXT_FAINT);
			y += small.getHeight() + 2;
		}
		g.setFont(Theme.body());
		FontMetrics body = g.getFontMetrics();
		int numW = small.stringWidth("00") + 6;
		int timeW = body.stringWidth("00:00.0") + 8;
		for (int i = from; i < rows.size(); i++)
		{
			Row r = rows.get(i);
			int mid = y + ROW / 2;
			g.setFont(Theme.small());
			g.setColor(Theme.TEXT_FAINT);
			String n = String.valueOf(i + 1);
			g.drawString(n, x + numW - 6 - small.stringWidth(n), mid + small.getAscent() / 2 - 1);
			int tx0 = x + numW;
			boolean top1 = best != null && r.run == best;
			g.setFont(Theme.body());
			if (r.run.isCompleted())
			{
				Ui.shadowed(g, Format.ticks(r.run.getTicks()), tx0, mid + body.getAscent() / 2 - 1, top1 ? Theme.GOLD_BRIGHT : Theme.TEXT);
			}
			else
			{
				// Where it stopped, once, in place of a time and a bar.
				Ui.shadowed(g, Ui.clip(RunCard.verdict(Standing.of(all, r.run)), body, w - numW - 60), tx0, mid + body.getAscent() / 2 - 1, Theme.EMBER);
			}

			// The tags and the end of the line, from the right.
			int right = x + w;
			g.setFont(Theme.small());
			if (r.end != null)
			{
				boolean purple = r.run.getPurple() != null;
				int room = Math.max(40, (w - numW - timeW) / 2);
				String end = Ui.clip(r.end, small, room);
				g.setColor(purple ? Theme.PURPLE : Theme.EMBER);
				g.drawString(end, right - small.stringWidth(end), mid + small.getAscent() / 2 - 1);
				right -= small.stringWidth(end) + 6;
			}
			if (r.tag != null)
			{
				right -= tag(g, r.tag, right, mid) + 6;
			}
			int barX = tx0 + timeW;
			int barW = right - barX;
			if (r.run.isCompleted() && slowest > 0 && barW > 8)
			{
				int len = Math.max(3, Math.round(barW * r.run.getTicks() / (float) slowest));
				g.setColor(Theme.alpha(Theme.STROKE_SOFT, 200));
				g.fill(new RoundRectangle2D.Float(barX, mid - 2, barW, 4, 4, 4));
				g.setPaint(new GradientPaint(barX, 0, top1 ? Theme.GOLD : Theme.GOLD_DIM, barX + len, 0, top1 ? Theme.GOLD_BRIGHT : Theme.GOLD));
				g.fill(new RoundRectangle2D.Float(barX, mid - 2, len, 4, 4, 4));
			}
			y += ROW;
		}
		y += 6;
		RunCard.rule(g, x, y, w);
		y += 10;

		// Three figures beneath: the average clear, the pace and the time between runs.
		int tileW = (w - 12) / 3;
		int tileH = 50;
		StatTile.paint(g, s.getAverage() < 0 ? "-" : Format.ticks(s.getAverage()), "Average", "a clear", Theme.TEXT, x, y, tileW, tileH);
		StatTile.paint(g, s.perHour() > 0 ? String.format(Locale.ENGLISH, "%.1f", s.perHour()) : "-", "An hour",
			s.perHour() > 0 ? "clears" : s.getClears() == 0 ? "no clear yet" : "too short to tell", Theme.TEAL, x + tileW + 6, y, tileW, tileH);
		StatTile.paint(g, s.getBetween() < 0 ? "-" : Format.clock((int) (s.getBetween() / 600)), "Downtime",
			s.getBetween() < 0 ? "one run" : "between runs", Theme.TEXT, x + (tileW + 6) * 2, y, w - (tileW + 6) * 2, tileH);
		y += tileH + 10;

		RunCard.rule(g, x, y, w);
		y += 8;
		g.setFont(Theme.small());
		g.setColor(Theme.TEXT_FAINT);
		g.drawString("Codex Archive Splits for RuneLite", x, y + small.getAscent());
		String span = hours(s.getEnd() - s.getStart()) + " at the archive";
		g.drawString(span, x + w - small.stringWidth(span), y + small.getAscent());
		y += small.getHeight();
		return y + PAD;
	}

	/** "1h 35m", "48m". */
	static String hours(long ms)
	{
		long minutes = Math.max(1, ms / 60_000);
		return minutes < 60 ? minutes + "m" : minutes / 60 + "h " + minutes % 60 + "m";
	}

	/** A small gilt tag ending at right; returns its width. */
	private static int tag(Graphics2D g, String text, int right, int mid)
	{
		FontMetrics fm = g.getFontMetrics();
		int w = fm.stringWidth(text) + 8;
		int h = fm.getAscent() + 2;
		RoundRectangle2D shape = new RoundRectangle2D.Float(right - w, mid - h / 2f - 1, w, h, 4, 4);
		g.setColor(Theme.alpha(Theme.GOLD, 50));
		g.fill(shape);
		g.setColor(Theme.alpha(Theme.GOLD, 160));
		g.draw(shape);
		g.setColor(Theme.GOLD_BRIGHT);
		g.drawString(text, right - w + 4, mid + fm.getAscent() / 2 - 1);
		return w;
	}
}
