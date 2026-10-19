package com.codex.archive.core;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * The run history as a spreadsheet: one line per run, every room and fight in seconds, so it
 * opens in any spreadsheet program for graphs of your own.
 */
public final class Export
{
	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);

	private Export()
	{
	}

	/** Comma-separated lines, a header first, oldest run first. */
	public static String csv(List<Run> runs, ZoneId zone)
	{
		StringBuilder sb = new StringBuilder();
		sb.append("started,party,outcome,time_s,kc,deaths,route");
		for (int row = 0; row < Encounter.ROWS; row++)
		{
			String r = "row" + (row + 1);
			sb.append(',').append(r).append("_boss,").append(r).append("_room_s,").append(r).append("_fight_s,")
				.append(r).append("_down_at_s");
		}
		sb.append(",purple,role,crew,note\n");
		for (Run run : runs)
		{
			sb.append(WHEN.format(Instant.ofEpochMilli(run.getStartedAt()).atZone(zone))).append(',')
				.append(Records.BUCKETS[Records.bucket(run.getTeamSize())]).append(',')
				.append(run.getOutcome() == null ? "" : run.getOutcome().name().toLowerCase(Locale.ENGLISH)).append(',')
				.append(seconds(run.getTicks())).append(',')
				.append(run.getKc() > 0 ? String.valueOf(run.getKc()) : "").append(',')
				.append(run.getDeaths()).append(',')
				.append(cell(run.route()));
			for (int row = 0; row < Encounter.ROWS; row++)
			{
				Split s = run.split(row);
				if (s == null || !s.isDone())
				{
					sb.append(",,,,");
					continue;
				}
				sb.append(',').append(cell(s.getEncounter().getTitle()))
					.append(',').append(seconds(s.roomTicks(0)))
					.append(',').append(seconds(s.fightTicks(0)))
					.append(',').append(seconds(s.getEndTick()));
			}
			sb.append(',').append(cell(run.getPurple()))
				.append(',').append(cell(run.getRole()))
				.append(',').append(cell(String.join("; ", run.getCrew())))
				.append(',').append(cell(run.getNote()))
				.append('\n');
		}
		return sb.toString();
	}

	/** Ticks as seconds to the tenth, exact since a tick is 0.6 seconds: "36.0". */
	static String seconds(int ticks)
	{
		int tenths = Math.max(0, ticks) * 6;
		return tenths / 10 + "." + tenths % 10;
	}

	/**
	 * A text cell, quoted when it holds a comma, quote or line break. A leading =, +, -, @, tab or
	 * carriage return is set off with a quote mark so a spreadsheet never runs a name or note as a formula.
	 */
	static String cell(String text)
	{
		if (text == null || text.isEmpty())
		{
			return "";
		}
		String t = text;
		if ("=+-@\t\r".indexOf(t.charAt(0)) >= 0)
		{
			t = "'" + t;
		}
		if (t.contains(",") || t.contains("\"") || t.contains("\n") || t.contains("\r"))
		{
			t = "\"" + t.replace("\"", "\"\"") + "\"";
		}
		return t;
	}
}
