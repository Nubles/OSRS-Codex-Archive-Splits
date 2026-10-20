package com.codex.archive.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the game's chat messages about the archive. The wording is matched loosely, so a small
 * change to it on the game's side still reads, and anything that does not match is ignored.
 */
public final class Messages
{
	private static final Pattern KC = Pattern.compile(
		"your (?:completed )?(?:[^.]*?archive[^.]*?) (?:completion |kill )?count is:? ?([\\d,]+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern WIPE = Pattern.compile(
		"(?:your party|your team|everyone) ha(?:s|ve) (?:fallen|been defeated|perished)", Pattern.CASE_INSENSITIVE);

	private static final Pattern GAME_TIME = Pattern.compile(
		"archive[^.]*?total completion time: ?(?:(\\d+):)?(\\d+):(\\d+)(?:\\.(\\d+))?", Pattern.CASE_INSENSITIVE);

	private Messages()
	{
	}

	/** The message without the game's colour and image tags. */
	public static String plain(String message)
	{
		return message == null ? "" : message.replaceAll("<[^>]*>", "").replace('\u00A0', ' ');
	}

	/** @return the completion count the message announces, or -1 when it announces none */
	public static int completionCount(String message)
	{
		Matcher m = KC.matcher(plain(message));
		if (!m.find())
		{
			return -1;
		}
		try
		{
			return Integer.parseInt(m.group(1).replace(",", ""));
		}
		catch (NumberFormatException e)
		{
			return -1;
		}
	}

	/** Whether the message says the whole party fell. */
	public static boolean isWipe(String message)
	{
		return WIPE.matcher(plain(message)).find();
	}

	/**
	 * The game's own time for a clear, as it tells it when the archive is done ("The Fractured
	 * Archive: Total completion time: 23:45.60. Personal best: ...").
	 *
	 * @return the time in ticks, or -1 when the message gives none
	 */
	public static int gameTime(String message)
	{
		Matcher m = GAME_TIME.matcher(plain(message));
		if (!m.find())
		{
			return -1;
		}
		try
		{
			long hundredths = (m.group(1) == null ? 0 : Long.parseLong(m.group(1)) * 360000)
				+ Long.parseLong(m.group(2)) * 6000 + Long.parseLong(m.group(3)) * 100;
			String fraction = m.group(4);
			if (fraction != null)
			{
				hundredths += Integer.parseInt((fraction + "00").substring(0, 2));
			}
			return hundredths > Integer.MAX_VALUE / 2 ? -1 : (int) Math.round(hundredths / 60.0);
		}
		catch (NumberFormatException e)
		{
			return -1;
		}
	}
}
