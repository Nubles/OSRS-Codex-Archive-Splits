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

	private Messages()
	{
	}

	/** The message without the game's colour and image tags. */
	public static String plain(String message)
	{
		return message == null ? "" : message.replaceAll("<[^>]*>", "").replace(' ', ' ');
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
}
