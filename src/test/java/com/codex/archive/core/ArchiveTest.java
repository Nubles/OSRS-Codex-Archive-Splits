package com.codex.archive.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class ArchiveTest
{
	private static Run clear(long at)
	{
		RunTracker t = new RunTracker();
		t.begin(at, 0, 3, null);
		t.complete(500);
		return t.finish(510, false);
	}

	@Test
	public void aPurpleDuringTheRunCountsThatRun()
	{
		Archive a = new Archive();
		a.record(clear(1), null, 0);
		a.record(clear(2), null, 0);
		Run third = clear(3);
		a.record(third, Collections.singletonList("Zeal"), 10);
		assertEquals("the streak ends with the run that gave it", 0, a.getDry());
		assertEquals(3, a.getDrops().get(0).getDry());
	}

	@Test
	public void thePanelsCopyIsNotChangedByRemovingAPurple()
	{
		// The panel draws its copy on the Swing thread while the client thread changes the archive.
		Archive a = new Archive();
		a.record(clear(1_000), null, 0);
		a.purple("Zeal", 1_500);
		a.record(clear(2_000), null, 0);
		a.purple("Rondache", 2_500);
		Archive copy = a.copy();
		assertEquals(1, copy.getDrops().get(1).getDry());
		assertTrue(a.removeDrop(1_500, "Zeal"));
		assertEquals(2, a.getDrops().get(0).getDry());
		assertEquals(1, copy.getDrops().get(1).getDry());
	}

	@Test
	public void twoPurplesInOneRun()
	{
		Archive a = new Archive();
		a.record(clear(1), Arrays.asList("Zeal", "Rondache"), 10);
		assertEquals(2, a.getDrops().size());
		assertEquals(1, a.getDrops().get(0).getDry());
		assertEquals(0, a.getDrops().get(1).getDry());
		assertEquals(0, a.getDry());
	}

	@Test
	public void deletingARunTakesItOffTheStreak()
	{
		Archive a = new Archive();
		a.record(clear(1_000), null, 0);
		a.purple("Zeal", 2_000);
		a.record(clear(3_000), null, 0);
		a.record(clear(4_000), null, 0);
		assertEquals(2, a.getDry());
		assertTrue(a.removeRun(4_000));
		assertEquals(1, a.getDry());
		assertTrue("a run before the purple leaves the streak alone", a.removeRun(1_000));
		assertEquals(1, a.getDry());
		assertFalse(a.removeRun(99));
		assertEquals(1, a.getRuns().size());
	}

	@Test
	public void removingAPurpleGivesItsClearsBack()
	{
		Archive a = new Archive();
		for (int i = 1; i <= 3; i++)
		{
			a.record(clear(i * 1_000), null, 0);
		}
		a.purple("Zeal", 3_500);
		a.record(clear(4_000), null, 0);
		a.record(clear(5_000), null, 0);
		a.purple("Rondache", 5_500);
		a.record(clear(6_000), null, 0);
		assertEquals(3, a.getDrops().get(0).getDry());
		assertEquals(2, a.getDrops().get(1).getDry());

		assertTrue(a.removeDrop(3_500, "Zeal"));
		assertEquals("the next purple took longer", 5, a.getDrops().get(0).getDry());
		assertTrue(a.removeDrop(5_500, "Rondache"));
		assertEquals("the newest goes back on the dry streak", 6, a.getDry());
		assertNull(a.getRuns().get(4).getPurple());
		assertFalse(a.removeDrop(5_500, "Rondache"));
	}

	@Test
	public void repairSurvivesAnEmptyOrDamagedSave()
	{
		Archive a = new com.google.gson.Gson().fromJson("{\"runs\":[null,{\"splits\":[{\"encounter\":\"NOT_A_BOSS\"}]}],\"dry\":-3}", Archive.class).repair();
		assertEquals(1, a.getRuns().size());
		assertEquals(Outcome.LEFT, a.getRuns().get(0).getOutcome());
		assertTrue(a.getRuns().get(0).getSplits().isEmpty());
		assertEquals(0, a.getDry());
		assertTrue(a.getDrops().isEmpty());
	}

	private static Run timed(long at, int team, int ticks)
	{
		RunTracker t = new RunTracker();
		t.begin(at, 0, team, null);
		t.engage(Encounter.GUARDIAN, 1);
		t.fell(Encounter.GUARDIAN, ticks / 2, false);
		t.tick(ticks / 2 + RunTracker.CONFIRM_TICKS);
		t.complete(ticks);
		return t.finish(ticks + 5, false);
	}

	@Test
	public void oldRunsGoButRecordsStay()
	{
		Archive a = new Archive();
		a.record(timed(0, 3, 500), null, 0);
		for (int i = 1; i <= Archive.MAX_RUNS + 50; i++)
		{
			a.record(timed(i, 3, 900), null, 0);
		}
		assertEquals(Archive.MAX_RUNS, a.getRuns().size());
		assertEquals("the best is kept however old", 0, a.getRuns().get(0).getStartedAt());
		assertEquals(500, Records.best(a.getRuns(), Records.bucket(3)).getTicks());
		assertEquals("clears are counted apart from the runs kept", Archive.MAX_RUNS + 51, a.clears(Records.bucket(3)));
	}

	@Test
	public void countsComeBackForAnOlderSave()
	{
		Archive a = new Archive();
		a.record(timed(1, 2, 500), null, 0);
		a.record(timed(2, 2, 600), null, 0);
		RunTracker t = new RunTracker();
		t.begin(3, 0, 2, null);
		t.died();
		a.record(t.finish(40, true), null, 0);
		com.google.gson.Gson gson = new com.google.gson.Gson();
		com.google.gson.JsonObject json = gson.toJsonTree(a).getAsJsonObject();
		json.remove("clears");
		json.remove("wipes");
		Archive old = gson.fromJson(json, Archive.class).repair();
		assertEquals(2, old.clears(Records.bucket(2)));
		assertEquals(1, old.wipes(Records.bucket(2)));
		assertTrue(old.removeRun(3));
		assertEquals(0, old.wipes(Records.bucket(2)));
	}

	@Test
	public void longestDryCountsTheStreakNow()
	{
		Archive a = new Archive();
		for (int i = 0; i < 4; i++)
		{
			a.record(clear(i), null, 0);
		}
		a.purple("Zeal", 10);
		a.record(clear(20), null, 0);
		assertEquals(4, a.longestDry());
		for (int i = 0; i < 6; i++)
		{
			a.record(clear(30 + i), null, 0);
		}
		assertEquals(7, a.longestDry());
	}
}
