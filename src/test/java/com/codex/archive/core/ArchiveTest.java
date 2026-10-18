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
}
