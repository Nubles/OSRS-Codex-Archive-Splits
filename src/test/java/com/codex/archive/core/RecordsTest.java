package com.codex.archive.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class RecordsTest
{
	private static Run run(int team, int... falls)
	{
		RunTracker t = new RunTracker();
		t.begin(0, 0, team, null);
		Encounter[] route = {Encounter.GUARDIAN, Encounter.BORK, Encounter.DREADNOTH, Encounter.XARTH, Encounter.HOODED_FIGURE};
		int prev = 0;
		for (int i = 0; i < falls.length; i++)
		{
			t.engage(route[i], prev + 5);
			t.fell(route[i], falls[i], false);
			for (int k = falls[i]; k <= falls[i] + RunTracker.CONFIRM_TICKS; k++)
			{
				t.tick(k);
			}
			prev = falls[i];
		}
		return t.finish(prev + 50, false);
	}

	@Test
	public void bestsAreKeptPerPartySize()
	{
		List<Run> runs = Arrays.asList(
			run(3, 100, 200, 300, 400, 500),
			run(3, 90, 210, 290, 420, 480),
			run(5, 80, 160, 240, 320, 400),
			run(3, 100, 150));
		Run best = Records.best(runs, Records.bucket(3));
		assertEquals(480, best.getTicks());
		assertEquals(400, Records.best(runs, Records.bucket(5)).getTicks());
		assertNull(Records.best(runs, Records.bucket(1)));
		assertEquals(90, Records.bestRoom(runs, 2, 0));
		assertEquals(50, Records.bestRoom(runs, 2, 1));
		assertEquals(90 + 50 + 80 + 100 + 60, Records.sumOfBest(runs, 2));
		assertEquals(490, Records.recentAverage(runs, 2, 10));
		assertEquals(2, Records.favouriteBucket(runs));
		assertEquals(Integer.valueOf(-60), Records.delta(runs.get(3), best, 1));
		assertEquals(1, Records.count(runs, Outcome.LEFT));
		assertEquals(3, Records.count(runs, Outcome.COMPLETED));
	}

	@Test
	public void purplesResetTheDryStreak()
	{
		Archive a = new Archive();
		for (int i = 0; i < 4; i++)
		{
			a.record(run(3, 10, 20, 30, 40, 50));
		}
		assertEquals(4, a.getDry());
		a.kc(4, 0);
		a.purple("Rondache", 0);
		assertEquals(0, a.getDry());
		assertEquals(4, a.getDrops().get(0).getDry());
		assertEquals(4, a.getDrops().get(0).getKc());
		assertEquals("Rondache", a.last().getPurple());
		a.record(run(3, 10, 20, 30, 40, 50));
		assertEquals(5, a.getKc());
		assertEquals(1, a.getDry());
	}
}
