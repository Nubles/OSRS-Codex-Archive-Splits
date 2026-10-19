package com.codex.archive.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class RoutesAndWallsTest
{
	private static final Encounter[] LEFT = {Encounter.GUARDIAN, Encounter.BORK, Encounter.DREADNOTH, Encounter.XARTH,
		Encounter.HOODED_FIGURE};
	private static final Encounter[] RIGHT = {Encounter.GUARDIAN, Encounter.EZEKIEL, Encounter.DREADNOTH, Encounter.TRIO,
		Encounter.HOODED_FIGURE};

	/** A run through these bosses, each falling at the given tick; ended by a wipe when asked. */
	private static Run run(long at, int team, Encounter[] route, boolean wiped, int... falls)
	{
		return run(at, team, route, wiped, true, falls);
	}

	private static Run run(long at, int team, Encounter[] route, boolean wiped, boolean engageNext, int... falls)
	{
		RunTracker t = new RunTracker();
		t.begin(at, 0, team, null);
		int prev = 0;
		for (int i = 0; i < falls.length; i++)
		{
			t.engage(route[i], prev + 5);
			t.fell(route[i], falls[i], false);
			for (int k = falls[i]; k <= falls[i] + RunTracker.FINAL_CONFIRM_TICKS; k++)
			{
				t.tick(k);
			}
			prev = falls[i];
		}
		if (wiped && engageNext && falls.length < route.length)
		{
			// Fought the next boss, then the party fell.
			t.engage(route[falls.length], prev + 5);
		}
		return t.finish(prev + 50, wiped);
	}

	@Test
	public void routesAreRankedByTheirBestClear()
	{
		List<Run> runs = Arrays.asList(
			run(1, 3, LEFT, false, 100, 200, 300, 400, 500),
			run(2, 3, RIGHT, false, 90, 210, 290, 420, 480),
			run(3, 3, LEFT, false, 110, 200, 320, 410, 520),
			run(4, 3, LEFT, false, 100, 200, 300, 400, 510),
			run(5, 5, LEFT, false, 50, 100, 150, 200, 250));
		List<Insights.RouteStat> routes = Insights.routes(runs, 2);
		assertEquals(2, routes.size());
		assertEquals("Ezekiel, Dreadnoth, Trio", routes.get(0).name());
		assertEquals(480, routes.get(0).best);
		assertEquals(1, routes.get(0).clears);
		Insights.RouteStat left = routes.get(1);
		assertEquals(Arrays.asList(Encounter.BORK, Encounter.DREADNOTH, Encounter.XARTH), left.doors);
		assertEquals(3, left.clears);
		assertEquals(500, left.best);
		assertEquals(510, left.typical);
		// The five-man run is another party size.
		assertEquals(1, Insights.routes(runs, 4).size());
	}

	@Test
	public void aRouteWithAnUntimedDoorIsLeftOut()
	{
		Encounter[] missed = {Encounter.GUARDIAN, Encounter.BORK, Encounter.XARTH, Encounter.HOODED_FIGURE};
		Run gap = run(1, 1, missed, false, 100, 200, 400, 500);
		assertTrue(gap.isCompleted());
		assertNull(Insights.doors(gap));
		assertTrue(Insights.routes(Arrays.asList(gap), 0).isEmpty());
		assertArrayEquals(new int[]{-1, 0}, Insights.routeBest(Arrays.asList(gap), 0, gap));
	}

	@Test
	public void aClearIsMeasuredAgainstItsOwnRoute()
	{
		Run a = run(1, 2, LEFT, false, 100, 200, 300, 400, 500);
		Run b = run(2, 2, RIGHT, false, 90, 180, 270, 360, 450);
		Run c = run(3, 2, LEFT, false, 100, 200, 300, 400, 490);
		List<Run> runs = Arrays.asList(a, b, c);
		// The right-hand route was faster, but this clear is the best on its own.
		assertArrayEquals(new int[]{500, 1}, Insights.routeBest(runs, 1, c));
		assertArrayEquals(new int[]{-1, 0}, Insights.routeBest(runs, 1, b));
	}

	@Test
	public void ladderListsEveryNewBest()
	{
		List<Run> runs = Arrays.asList(
			run(3, 1, LEFT, false, 100, 200, 300, 400, 480),
			run(1, 1, LEFT, false, 100, 200, 300, 400, 520),
			run(2, 1, LEFT, false, 100, 200, 300, 400, 530),
			run(4, 1, LEFT, true, 100, 200),
			run(5, 1, LEFT, false, 100, 200, 300, 400, 470));
		List<Insights.Best> ladder = Insights.ladder(runs, 0);
		assertEquals(3, ladder.size());
		assertEquals(1, ladder.get(0).at);
		assertEquals(-1, ladder.get(0).saved);
		assertEquals(3, ladder.get(1).at);
		assertEquals(520 - 480, ladder.get(1).saved);
		assertEquals(10, ladder.get(2).saved);
		assertEquals(3, Insights.personalBests(runs, 0));
	}

	@Test
	public void wipesArePutOnTheBossBeingFought()
	{
		List<Run> runs = Arrays.asList(
			run(1, 2, LEFT, true, 100, 200, 300),
			run(2, 2, RIGHT, true, 90, 210, 290),
			run(3, 2, LEFT, true, 100, 200, 300),
			run(4, 2, LEFT, false, 100, 200, 300, 400, 500),
			run(5, 2, LEFT, true, 100, 200, 300, 400),
			run(6, 2, LEFT, true, 100));
		List<Insights.Wall> walls = Insights.walls(runs, 1);
		assertEquals(Encounter.XARTH, walls.get(0).encounter);
		assertEquals(2, walls.get(0).wipes);
		// Xarth was reached by four runs: three left-hand runs and the clear.
		assertEquals(4, walls.get(0).tries);
		Insights.Wall trio = walls.stream().filter(w -> w.encounter == Encounter.TRIO).findFirst().get();
		assertEquals(1, trio.wipes);
		assertEquals(1, trio.tries);
		Insights.Wall last = walls.stream().filter(w -> w.encounter == Encounter.HOODED_FIGURE).findFirst().get();
		assertEquals(1, last.wipes);
		assertEquals(2, last.tries);
		// Dreadnoth was never wiped at, so it is not listed.
		assertTrue(walls.stream().noneMatch(w -> w.encounter == Encounter.DREADNOTH));
	}

	@Test
	public void aWipeBeforeTheBossIsSeenIsPutOnlyOnARowWithOneBoss()
	{
		// Wiped on the way to the final room: it can only be the final boss.
		Run toFinal = run(1, 1, LEFT, true, false, 100, 200, 300, 400);
		// Wiped before either door of the first row was seen: no telling which.
		Run toDoor = run(2, 1, LEFT, true, false, 100);
		List<Insights.Wall> walls = Insights.walls(Arrays.asList(toFinal, toDoor), 0);
		assertEquals(1, walls.size());
		assertEquals(Encounter.HOODED_FIGURE, walls.get(0).encounter);
		assertEquals(1, walls.get(0).wipes);
		assertEquals(1, walls.get(0).tries);
	}
}
