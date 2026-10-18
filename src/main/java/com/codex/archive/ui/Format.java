package com.codex.archive.ui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** How numbers and times are written in the panel. */
public final class Format
{
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
	private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);

	private Format()
	{
	}

	/**
	 * Game ticks as a raid time, to the tenth of a second the way the game's own raid timers
	 * write it: "0:36.0", "24:31.8", "1:02:07.2". A tick is 0.6 seconds, so tenths are exact.
	 */
	public static String ticks(int ticks)
	{
		if (ticks < 0)
		{
			return "-";
		}
		int tenths = ticks * 6;
		int seconds = tenths / 10;
		int tenth = tenths % 10;
		int h = seconds / 3600;
		int m = seconds / 60 % 60;
		int s = seconds % 60;
		if (h > 0)
		{
			return String.format(Locale.ENGLISH, "%d:%02d:%02d.%d", h, m, s, tenth);
		}
		return String.format(Locale.ENGLISH, "%d:%02d.%d", m, s, tenth);
	}

	/** The same without tenths, for the big crest figure while a run is going: "12:04". */
	public static String clock(int ticks)
	{
		int seconds = Math.max(0, ticks) * 6 / 10;
		int h = seconds / 3600;
		if (h > 0)
		{
			return String.format(Locale.ENGLISH, "%d:%02d:%02d", h, seconds / 60 % 60, seconds % 60);
		}
		return String.format(Locale.ENGLISH, "%d:%02d", seconds / 60, seconds % 60);
	}

	/** A time against a best: "-4.2" ahead, "+1:12.0" behind, "0.0" level. Under a minute it drops the minutes. */
	public static String delta(int ticks)
	{
		int abs = Math.abs(ticks);
		String t = abs < 100 ? String.format(Locale.ENGLISH, "%d.%d", abs * 6 / 10, abs * 6 % 10) : ticks(abs);
		return ticks < 0 ? "-" + t : ticks > 0 ? "+" + t : t;
	}

	/** A gap in time without a sign: "4.2s" under a minute, "1:12.0" above. */
	public static String span(int ticks)
	{
		int abs = Math.abs(ticks);
		return abs < 100 ? String.format(Locale.ENGLISH, "%d.%ds", abs * 6 / 10, abs * 6 % 10) : ticks(abs);
	}

	/** A delta in words, for the chat box: "4.2s ahead of your best", "1:12.0 behind your best". */
	public static String versus(int ticks)
	{
		if (ticks == 0)
		{
			return "level with your best";
		}
		return span(ticks) + (ticks < 0 ? " ahead of" : " behind") + " your best";
	}

	public static String count(long n)
	{
		return String.format(Locale.ENGLISH, "%,d", n);
	}

	/** "just now", "12m ago", "3h ago", "2d ago". */
	public static String ago(long ms)
	{
		long minutes = Math.max(0, ms) / 60_000;
		if (minutes < 1)
		{
			return "just now";
		}
		if (minutes < 60)
		{
			return minutes + "m ago";
		}
		if (minutes < 48 * 60)
		{
			return minutes / 60 + "h ago";
		}
		return minutes / (24 * 60) + "d ago";
	}

	/** "Thu 16 Oct". */
	public static String day(long epochMs)
	{
		return DAY.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()));
	}

	/** "16 Oct". */
	public static String shortDay(long epochMs)
	{
		return SHORT.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()));
	}

	/** "14:02". */
	public static String time(long epochMs)
	{
		return CLOCK.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()));
	}

	/** "1st", "2nd", "11th", "23rd". */
	public static String ordinal(int n)
	{
		int mod100 = n % 100;
		String suffix = mod100 >= 11 && mod100 <= 13 ? "th" : n % 10 == 1 ? "st" : n % 10 == 2 ? "nd" : n % 10 == 3 ? "rd" : "th";
		return n + suffix;
	}
}
