package com.codex.archive.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class StandingTest
{
	@Test
	public void standsAgainstOnlyTheRunsBeforeIt()
	{
		Run first = StandingTestRuns.run(1_000, 3, false, 100, 300, 500, 700, 1000);
		Run second = StandingTestRuns.run(2_000, 3, false, 90, 310, 480, 690, 980);
		// A quicker run later does not take "new best" away from the second.
		Run later = StandingTestRuns.run(3_000, 3, false, 80, 250, 400, 600, 900);
		// Another party size never counts.
		Run duo = StandingTestRuns.run(1_500, 2, false, 50, 100, 150, 200, 300);
		List<Run> runs = Arrays.asList(first, second, later, duo);

		Standing s = Standing.of(runs, second);
		assertTrue(s.isNewBest());
		assertFalse(s.isFirstClear());
		assertEquals(first, s.getPriorBest());
		assertEquals(-20, (int) s.totalDelta());
		assertEquals(2, s.getClearNumber());
		// Guardian's room 90 beat 100; Bork's room 220 did not beat 200; Dreadnoth's 170 beat 200.
		assertTrue(s.isGold(0));
		assertFalse(s.isGold(1));
		assertTrue(s.isGold(2));
		assertEquals(5, s.getComparedRooms());
		// At Bork the run stood 10 ticks behind the best before it.
		assertEquals(10, (int) s.delta(1));
		assertEquals(-20, (int) s.delta(4));

		Standing f = Standing.of(runs, first);
		assertTrue(f.isFirstClear());
		assertTrue(f.isNewBest());
		assertNull(f.totalDelta());
		assertEquals(1, f.getClearNumber());
		// The first time through, there is nothing to beat: no golds, no rooms compared.
		assertEquals(0, f.getGoldCount());
		assertEquals(0, f.getComparedRooms());
		assertNull(f.delta(0));
	}

	@Test
	public void aWipeIsNeverABestButItsRoomsCountForLaterGolds()
	{
		Run wipe = StandingTestRuns.run(1_000, 3, true, 60, 200);
		Run clear = StandingTestRuns.run(2_000, 3, false, 70, 260, 500, 700, 1000);
		List<Run> runs = Arrays.asList(wipe, clear);

		Standing w = Standing.of(runs, wipe);
		assertFalse(w.isNewBest());
		assertEquals(0, w.getClearNumber());
		assertNull(w.totalDelta());

		Standing c = Standing.of(runs, clear);
		// The first clear, but the wipe timed both rooms quicker (Guardian 60, Bork 140 against 70 and 190): no golds.
		assertTrue(c.isFirstClear());
		assertFalse(c.isGold(0));
		assertFalse(c.isGold(1));
		assertEquals(2, c.getComparedRooms());
	}
}
