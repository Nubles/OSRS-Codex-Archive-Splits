package com.codex.archive.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MessagesTest
{
	@Test
	public void completionCounts()
	{
		assertEquals(12, Messages.completionCount("Your completed Fractured Archive count is: <col=ff0000>12</col>."));
		assertEquals(1204, Messages.completionCount("Your Fractured Archive completion count is: 1,204."));
		assertEquals(-1, Messages.completionCount("Your Chambers of Xeric count is: 12."));
		assertEquals(-1, Messages.completionCount("You find an archive of old letters. Your Bork kill count is: 3."));
		assertEquals(-1, Messages.completionCount(null));
		assertEquals(-1, Messages.completionCount("Your Fractured Archive count is: 99999999999."));
	}

	@Test
	public void wipes()
	{
		assertTrue(Messages.isWipe("Your party has fallen."));
		assertFalse(Messages.isWipe("You have fallen."));
	}

	@Test
	public void theGamesOwnTime()
	{
		// The wording RuneLite's own chat commands read for the archive's personal best.
		assertEquals(2376, Messages.gameTime("The Fractured Archive: Total completion time: <col=ef1020>23:45.60</col>. Personal best: 21:02.40"));
		assertEquals(2376, Messages.gameTime("The Fractured Archive: Total completion time: <col=ef1020>23:45.60</col> (new personal best)"));
		assertEquals(6100, Messages.gameTime("The Fractured Archive: Total completion time: 1:01:00.00."));
		assertEquals(100, Messages.gameTime("The Fractured Archive: Total completion time: 1:00."));
		assertEquals(-1, Messages.gameTime("Theatre of Blood total completion time: 23:45.60."));
		assertEquals(-1, Messages.gameTime("The Fractured Archive: Total completion time: 99999999999:00."));
		assertEquals(-1, Messages.gameTime(null));
	}
}
