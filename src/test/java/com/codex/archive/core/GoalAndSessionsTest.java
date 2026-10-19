package com.codex.archive.core;

import static com.codex.archive.core.InsightsTest.run;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** A goal time raced room by room, and the history told in sittings. */
public class GoalAndSessionsTest
{
	private static final Encounter[] ROUTE = {Encounter.GUARDIAN, Encounter.BORK, Encounter.DREADNOTH, Encounter.XARTH,
		Encounter.HOODED_FIGURE};
	private static final long MIN = 60_000L;

	private static List<Run> history()
	{
		// Three trio clears: rooms of 100, 100, 100, 100, 100 ticks at best, and typically 120 each.
		return Arrays.asList(
			run(1, 3, ROUTE, 120, 240, 360, 480, 600),
			run(2, 3, ROUTE, 100, 200, 300, 400, 500),
			run(3, 3, ROUTE, 120, 240, 360, 480, 600));
	}

	@Test
	public void aGoalIsSharedOutInTheShapeOfYourRuns()
	{
		Comparison c = Comparison.of(history(), 2, Comparison.Mode.GOAL, null, 451);
		assertEquals(Comparison.Mode.GOAL, c.getMode());
		// Five equal rooms of 451 ticks: 90 each, and the one tick over goes to the first.
		int sum = 0;
		for (int row = 0; row < Encounter.ROWS; row++)
		{
			assertTrue(c.room(row) == 90 || c.room(row) == 91);
			sum += c.room(row);
		}
		assertEquals(451, sum);
		assertEquals(451, c.total());
		// A run at 500 when the goal's Bork mark is 181 is behind by its own time less the mark.
		Run live = history().get(1);
		assertEquals(Integer.valueOf(200 - c.end(1)), c.delta(live, 1));
	}

	@Test
	public void withoutAGoalTheGoalModeIsTheBest()
	{
		Comparison c = Comparison.of(history(), 2, Comparison.Mode.GOAL, null, 0);
		assertEquals(Comparison.Mode.PERSONAL_BEST, c.getMode());
		assertEquals(500, c.total());
		Archive a = new Archive();
		history().forEach(a::record);
		assertEquals(Comparison.Mode.PERSONAL_BEST, Comparison.of(a, 2, Comparison.Mode.GOAL, null).getMode());
		a.setGoal(2, 450);
		assertEquals(Comparison.Mode.GOAL, Comparison.of(a, 2, Comparison.Mode.GOAL, null).getMode());
		// Other party sizes keep no goal.
		assertEquals(0, a.goal(0));
	}

	@Test
	public void aGoalWithNoRunsMarksOnlyTheWholeRun()
	{
		Comparison c = Comparison.of(new ArrayList<>(), 2, Comparison.Mode.GOAL, null, 1000);
		assertEquals(1000, c.total());
		assertEquals(-1, c.end(0));
		assertEquals(-1, c.room(2));
	}

	@Test
	public void theGoalSaysWhereTheTimeIs()
	{
		List<Run> runs = new ArrayList<>(history());
		// Three more clears, slow in the third row only.
		for (int i = 4; i <= 6; i++)
		{
			runs.add(run(i, 3, ROUTE, 120, 240, 420, 540, 660));
		}
		Goal g = Goal.of(runs, 2, 450);
		assertNotNull(g);
		assertEquals(500, g.getBest());
		assertEquals(50, g.gap());
		assertEquals(0, g.getBeaten());
		// The typical third room (median of 180, 180, 180, 120, 100, 120) is the furthest over the goal's share.
		assertEquals(2, g.getRows().get(0).row);
		assertTrue(g.getRows().get(0).over() > g.getRows().get(1).over());
		Goal beaten = Goal.of(runs, 2, 600);
		assertEquals(3, beaten.getBeaten());
		// Best rooms of 100 each share a 600-tick goal evenly.
		assertEquals(120, beaten.getRows().get(0).allowed);
		assertEquals(30, beaten.getRows().get(0).over());
		assertEquals(1, beaten.getFirst().getStartedAt());
		assertNull(Goal.of(runs, 2, 0));
	}

	@Test
	public void theLiveMarksAndTheGoalCardShareTheGoalAlike()
	{
		List<Run> runs = new ArrayList<>(history());
		for (int i = 4; i <= 6; i++)
		{
			runs.add(run(i, 3, ROUTE, 120, 240, 420, 540, 660));
		}
		// The goal card says what each room is allowed; the splits must race those same marks.
		Comparison c = Comparison.of(runs, 2, Comparison.Mode.GOAL, null, 450);
		for (Goal.Row r : Goal.of(runs, 2, 450).getRows())
		{
			assertEquals("row " + r.row, r.allowed, c.room(r.row));
		}
	}

	@Test
	public void goalTimesAreReadAsPlayersWriteThem()
	{
		assertEquals(2000, Goal.parse("20:00"));
		assertEquals(Math.round((19 * 60 + 42.6) / 0.6), Goal.parse(" 19:42.6 "));
		assertEquals(6250, Goal.parse("1:02:30"));
		// A bare number is minutes, as the prompt asks for: "20" means twenty minutes, not seconds.
		assertEquals(2000, Goal.parse("20"));
		assertEquals(2050, Goal.parse("20.5"));
		assertEquals(-1, Goal.parse("20:75"));
		assertEquals(-1, Goal.parse("twenty"));
		assertEquals(-1, Goal.parse("0:00"));
		assertEquals(-1, Goal.parse(""));
		assertEquals(-1, Goal.parse(null));
	}

	@Test
	public void goalsSurviveASaveAndOlderSavesHaveNone()
	{
		Gson gson = new Gson();
		Archive a = new Archive();
		a.setGoal(5, 3000);
		Archive read = gson.fromJson(gson.toJson(a), Archive.class).repair();
		assertEquals(3000, read.goal(5));
		Archive old = gson.fromJson("{\"runs\":[],\"kc\":3}", Archive.class).repair();
		assertEquals(0, old.goal(2));
		old.setGoal(2, 100);
		assertEquals(100, old.goal(2));
		a.setGoal(9, 100);
		a.setGoal(-1, 100);
		assertEquals(0, a.goal(9));
	}

	@Test
	public void runsAreToldInSittings()
	{
		long day = 1_000_000_000_000L;
		List<Run> runs = new ArrayList<>();
		// Three trio runs in an evening, ten minutes apart, the second a new best and the third a wipe.
		runs.add(run(day, 3, ROUTE, 120, 240, 360, 480, 600));
		runs.add(run(day + 16 * MIN, 3, ROUTE, 100, 200, 300, 400, 500));
		Run wipe = run(day + 31 * MIN, 3, ROUTE, 100, 200);
		runs.add(wipe);
		// The next day, one slower clear.
		runs.add(run(day + 24 * 60 * MIN, 3, ROUTE, 130, 260, 390, 520, 650));
		List<Sessions.Session> all = Sessions.of(runs);
		assertEquals(2, all.size());
		Sessions.Session evening = all.get(0);
		assertEquals(3, evening.getRuns().size());
		assertEquals(2, evening.getClears());
		assertEquals(500, evening.getBest());
		assertEquals(550, evening.getAverage());
		// The first clear and the second both set a best when they were made.
		assertEquals(2, evening.getBests());
		// Ten minutes between the first's end (6:00 in) and the second's start, and the same again to the third.
		assertEquals(10 * MIN, evening.getBetween(), 1);
		Sessions.Session next = all.get(1);
		assertEquals(0, next.getBests());
		assertEquals(-1, next.getBetween());
		assertEquals(0, next.perHour(), 0);
		assertEquals(next.getStart(), Sessions.current(runs, day + 24 * 60 * MIN + 30 * MIN).getStart());
		assertNull(Sessions.current(runs, day + 48 * 60 * MIN));
	}
}
