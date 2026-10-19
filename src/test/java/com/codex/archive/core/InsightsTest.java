package com.codex.archive.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class InsightsTest
{
	private static final Encounter[] LEFT = {Encounter.GUARDIAN, Encounter.BORK, Encounter.DREADNOTH, Encounter.XARTH,
		Encounter.HOODED_FIGURE};
	private static final Encounter[] RIGHT = {Encounter.GUARDIAN, Encounter.EZEKIEL, Encounter.DREADNOTH, Encounter.TRIO,
		Encounter.HOODED_FIGURE};

	/** A run through these bosses, each falling at the given tick from the start. */
	static Run run(long at, int team, Encounter[] route, int... falls)
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
		return t.finish(prev + 50, false);
	}

	private static List<Run> history()
	{
		return Arrays.asList(
			run(1, 3, LEFT, 100, 200, 300, 400, 500),
			run(2, 3, RIGHT, 90, 210, 290, 420, 480),
			run(3, 3, LEFT, 110, 200, 320, 410, 520),
			run(4, 5, LEFT, 50, 100, 150, 200, 250));
	}

	@Test
	public void personalBestComparesBossByBoss()
	{
		List<Run> runs = history();
		Comparison pb = Comparison.of(runs, 2, Comparison.Mode.PERSONAL_BEST, null);
		assertEquals(480, pb.total());
		assertEquals(210, pb.end(1));
		Run live = run(9, 3, LEFT, 95, 205);
		assertEquals(Integer.valueOf(5), pb.delta(live, 0));
		assertEquals(Integer.valueOf(-5), pb.delta(live, 1));
		assertNull(pb.delta(live, 2));
	}

	@Test
	public void bestRoomsStringTheFastestRoomsTogether()
	{
		Comparison golds = Comparison.of(history(), 2, Comparison.Mode.BEST_ROOMS, null);
		// Each row's fastest room in any of the three trio runs.
		assertEquals(90, golds.room(0));
		assertEquals(90, golds.room(1));
		assertEquals(80, golds.room(2));
		assertEquals(90, golds.room(3));
		assertEquals(60, golds.room(4));
		assertEquals(410, golds.total());
		assertEquals(Records.sumOfBest(history(), 2), golds.total());
	}

	@Test
	public void typicalRunIsTheMedianOfEachRoom()
	{
		Comparison typical = Comparison.of(history(), 2, Comparison.Mode.TYPICAL, null);
		assertEquals(100, typical.room(0));
		assertEquals(100, typical.end(0));
		// Rooms 2: 100, 120, 90.
		assertEquals(100, typical.room(1));
		assertEquals(200, typical.end(1));
	}

	@Test
	public void aRunIsNeverComparedWithItself()
	{
		List<Run> runs = history();
		Run best = runs.get(1);
		Comparison pb = Comparison.of(runs, 2, Comparison.Mode.PERSONAL_BEST, best);
		assertEquals(500, pb.total());
	}

	@Test
	public void missingRowBreaksTheChainOfMarks()
	{
		Comparison none = Comparison.of(Collections.singletonList(run(1, 3, LEFT, 100, 200)), 2, Comparison.Mode.BEST_ROOMS, null);
		assertEquals(200, none.end(1));
		assertEquals(-1, none.end(2));
		assertEquals(-1, none.total());
	}

	@Test
	public void bestPossibleCountsTheRoomBeingFought()
	{
		int[] best = {90, 90, 80, 90, 60};
		Run live = run(9, 3, LEFT, 100, 200);
		// 200 so far, then 80 + 90 + 60 at their best.
		assertEquals(430, Comparison.bestPossible(live, best, 230));
		// Already 120 into a room whose best is 80: that room costs at least what it has taken.
		assertEquals(470, Comparison.bestPossible(live, best, 320));
		assertEquals(-1, Comparison.bestPossible(live, new int[]{90, 90, -1, 90, 60}, 230));
	}

	@Test
	public void rowsLosingMostTimeComeFirst()
	{
		List<Insights.RowStat> rows = Insights.rows(history(), 2);
		assertEquals(5, rows.size());
		// The final room ran 100, 60 and 110: best 60, typical 100. The third row ran 100, 80, 120.
		Insights.RowStat first = rows.get(0);
		assertEquals(4, first.row);
		assertEquals(40, first.loss());
		assertEquals(2, rows.get(1).row);
		assertEquals(20, rows.get(1).loss());
		assertTrue(rows.get(0).loss() >= rows.get(1).loss());
	}

	@Test
	public void doorsAreComparedOnceBothHaveEnoughRuns()
	{
		List<Run> runs = new ArrayList<>(history());
		assertTrue(Insights.doors(runs, 2).isEmpty());
		runs.add(run(5, 3, RIGHT, 100, 190, 300, 390, 480));
		runs.add(run(6, 3, RIGHT, 100, 195, 300, 400, 490));
		runs.add(run(7, 3, LEFT, 100, 210, 300, 400, 490));
		List<Insights.DoorStat[]> doors = Insights.doors(runs, 2);
		assertEquals(2, doors.size());
		Insights.DoorStat[] row1 = doors.get(0);
		assertEquals(Encounter.BORK, row1[0].encounter);
		assertEquals(3, row1[0].runs);
		assertEquals(100, row1[0].typical);
		assertEquals(95, row1[1].typical);
	}

	@Test
	public void trendAndPersonalBests()
	{
		assertEquals(Arrays.asList(500, 480, 520), Insights.trend(history(), 2));
		assertEquals(2, Insights.personalBests(history(), 2));
	}

	@Test
	public void crewIsCountedPerTeammate()
	{
		RunTracker t = new RunTracker();
		t.begin(1, 0, 3, null);
		t.seeCrew(Arrays.asList("Iron Bob", "Zezima"));
		t.seeCrew(Arrays.asList("Zezima", ""));
		Run a = finishClear(t, 400);
		t.begin(2, 0, 3, null);
		t.seeCrew(Collections.singletonList("Zezima"));
		Run b = finishClear(t, 380);
		assertEquals(Arrays.asList("Iron Bob", "Zezima"), a.getCrew());
		List<Insights.Mate> crew = Insights.crew(Arrays.asList(a, b), 2);
		assertEquals("Zezima", crew.get(0).name);
		assertEquals(2, crew.get(0).runs);
		assertEquals(380, crew.get(0).best);
		assertEquals(1, crew.get(1).runs);
	}

	private static Run finishClear(RunTracker t, int at)
	{
		t.engage(Encounter.HOODED_FIGURE, at - 50);
		t.complete(at);
		return t.finish(at + 5, false);
	}

	@Test
	public void rolesAndNotesAreKeptAndSaved()
	{
		Archive a = new Archive();
		Run r = run(7, 3, LEFT, 100, 200, 300, 400, 500);
		a.record(r);
		assertTrue(a.setRole(7, "Tank"));
		assertTrue(a.setNote(7, "  forgot\nthe spec weapon  "));
		Archive back = new Gson().fromJson(new Gson().toJson(a), Archive.class).repair();
		assertEquals("Tank", back.last().getRole());
		assertEquals("forgot the spec weapon", back.last().getNote());
		a.setNote(7, " ");
		assertNull(a.last().getNote());
		a.setNote(7, String.join("", Collections.nCopies(30, "abcd")));
		assertEquals(Run.NOTE_LENGTH, a.last().getNote().length());
		// An older save has no crew at all.
		Archive old = new Gson().fromJson("{\"runs\":[{\"startedAt\":3,\"teamSize\":2,\"outcome\":\"COMPLETED\",\"ticks\":100}]}",
			Archive.class).repair();
		assertTrue(old.last().getCrew().isEmpty());
	}

	@Test
	public void exportIsASpreadsheetLinePerRun()
	{
		Run r = run(0, 3, LEFT, 100, 200, 300, 400, 500);
		Archive a = new Archive();
		a.record(r);
		a.setNote(0, "=cmd, \"quoted\"");
		String csv = Export.csv(a.getRuns(), ZoneOffset.UTC);
		String[] lines = csv.split("\n");
		assertEquals(2, lines.length);
		assertTrue(lines[0].startsWith("started,party,outcome,time_s,kc,deaths,route,row1_boss,row1_room_s"));
		assertTrue(lines[1].startsWith("1970-01-01 00:00:00,Trio,completed,300.0,,0,\"Bork, Dreadnoth, Xarth\",Archive Guardian,60.0,57.0,60.0,"));
		assertTrue(lines[1].endsWith(",\"'=cmd, \"\"quoted\"\"\""));
		assertEquals("36.0", Export.seconds(60));
	}

	@Test
	public void exportGuardsEveryFormulaLead()
	{
		// A spreadsheet also runs a cell that starts with a tab or carriage return followed by a formula.
		assertEquals("'\t=1+1", Export.cell("\t=1+1"));
		assertEquals("\"'\r=1+1\"", Export.cell("\r=1+1"));
		assertEquals("'-2+3", Export.cell("-2+3"));
		assertEquals("'@SUM(A1)", Export.cell("@SUM(A1)"));
		assertEquals("\"a\nb\"", Export.cell("a\nb"));
		assertEquals("Ashen Reed", Export.cell("Ashen Reed"));
	}
}
