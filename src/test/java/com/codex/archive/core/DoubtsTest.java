package com.codex.archive.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** What each boss of a run was told from, kept so a guessed or missed boss can be checked. */
public class DoubtsTest
{
	private static final Gson GSON = new Gson();

	private static void fight(RunTracker t, Encounter e, String npc, int level, int engage, int fall)
	{
		t.engage(e, engage, npc, level);
		t.fell(e, fall, false);
		for (int i = fall; i <= fall + RunTracker.FINAL_CONFIRM_TICKS; i++)
		{
			t.tick(i);
		}
	}

	@Test
	public void aRunOfNamedBossesHasNothingToCheck()
	{
		RunTracker t = new RunTracker();
		t.begin(0, 0, 1, null);
		fight(t, Encounter.GUARDIAN, "Archive Guardian", 300, 5, 50);
		fight(t, Encounter.BORK, "Bork", 267, 70, 150);
		fight(t, Encounter.DREADNOTH, "Dreadnoth the Jellied", 400, 170, 250);
		fight(t, Encounter.XARTH, "Xarth", 380, 270, 350);
		fight(t, Encounter.HOODED_FIGURE, "The Hooded Figure", 600, 370, 500);
		Run run = t.finish(520, false);
		assertTrue(run.isCompleted());
		assertFalse(run.hasDoubts());
		assertTrue(Doubts.of(run).isEmpty());
	}

	@Test
	public void aBossToldBySizeSaysWhatItWasToldFrom()
	{
		RunTracker t = new RunTracker();
		t.begin(0, 0, 1, null);
		fight(t, Encounter.GUARDIAN, "Archive Guardian", 300, 5, 50);
		fight(t, Encounter.BORK, "Bork", 267, 70, 150);
		Encounter guess = t.resolve("Skeletal Archmage", 412);
		assertEquals(Encounter.NECROMANCER, guess);
		fight(t, guess, "Skeletal Archmage", 412, 170, 250);
		fight(t, Encounter.XARTH, "Xarth", 380, 270, 350);
		fight(t, Encounter.HOODED_FIGURE, "The Hooded Figure", 600, 370, 500);
		Run run = t.finish(520, false);

		Split necro = run.split(2);
		assertEquals("Skeletal Archmage", necro.getSeen());
		assertEquals(412, necro.getSeenLevel());
		// The hooded figure was known by name, so only the one guess is told.
		assertNull(run.split(4).getSeen());
		assertEquals(Arrays.asList("Necromancer was told by its size: Skeletal Archmage, level 412."), Doubts.of(run));
	}

	@Test
	public void aGuessTheNamedBossCorrectedIsNoLongerInDoubt()
	{
		RunTracker t = new RunTracker();
		t.begin(0, 0, 1, null);
		fight(t, Encounter.GUARDIAN, "Archive Guardian", 300, 5, 50);
		fight(t, Encounter.BORK, "Bork", 267, 70, 150);
		// A big add is hit first and taken for the unnamed boss; then Dreadnoth himself is engaged.
		t.engage(t.resolve("Jelly Spawn", 180), 160, "Jelly Spawn", 180);
		t.engage(Encounter.DREADNOTH, 170, "Dreadnoth the Jellied", 400);
		t.fell(Encounter.DREADNOTH, 250, false);
		for (int i = 250; i < 262; i++)
		{
			t.tick(i);
		}
		Run run = t.finish(300, false);
		assertEquals(Encounter.DREADNOTH, run.split(2).getEncounter());
		assertNull(run.split(2).getSeen());
	}

	@Test
	public void aBossNotRecognisedShowsTheBigFoesTheRunFought()
	{
		RunTracker t = new RunTracker();
		t.begin(0, 0, 1, null);
		fight(t, Encounter.GUARDIAN, "Archive Guardian", 300, 5, 50);
		// Row 1's boss turned out to have another name: the plugin never opens its split.
		for (int i = 0; i < 5; i++)
		{
			t.foe("Bork the Unbroken", 290);
		}
		t.foe("Smork", 160);
		// Small fry is not counted.
		t.foe("Imp", 7);
		Run run = t.finish(400, true);
		assertEquals(Arrays.asList("Bork the Unbroken (290)", "Smork (160)"), run.getUnknown());
		assertTrue(run.hasDoubts());
		List<String> lines = Doubts.of(run);
		assertEquals(1, lines.size());
		assertEquals("Not timed: Bork / Ezekiel. Big foes no boss name matched: Bork the Unbroken (290), Smork (160).", lines.get(0));
	}

	@Test
	public void addsInATimedRunAreNoDoubt()
	{
		RunTracker t = new RunTracker();
		t.begin(0, 0, 1, null);
		fight(t, Encounter.GUARDIAN, "Archive Guardian", 300, 5, 50);
		fight(t, Encounter.BORK, "Bork", 267, 70, 150);
		t.foe("Smork", 160);
		fight(t, Encounter.DREADNOTH, "Dreadnoth the Jellied", 400, 170, 250);
		fight(t, Encounter.XARTH, "Xarth", 380, 270, 350);
		fight(t, Encounter.HOODED_FIGURE, "The Hooded Figure", 600, 370, 500);
		Run run = t.finish(520, false);
		assertFalse(run.hasDoubts());
	}

	@Test
	public void onlyTheLatestRunsKeepWhatTheyWereToldFrom()
	{
		Archive archive = new Archive();
		for (int i = 0; i < Archive.DOUBT_RUNS + 2; i++)
		{
			RunTracker t = new RunTracker();
			t.begin(i * 1_000_000L, 0, 1, null);
			fight(t, Encounter.GUARDIAN, "Archive Guardian", 300, 5, 50);
			t.foe("Bork the Unbroken", 290);
			archive.record(t.finish(400, true), java.util.Collections.emptyList(), i * 1_000_000L + 1000);
		}
		Archive read = GSON.fromJson(GSON.toJson(archive), Archive.class).repair();
		List<Run> runs = read.getRuns();
		assertEquals(Archive.DOUBT_RUNS + 2, runs.size());
		assertTrue(runs.get(0).getUnknown().isEmpty());
		assertFalse(runs.get(runs.size() - 1).getUnknown().isEmpty());
	}

	@Test
	public void aSaveFromBeforeKeepsReading()
	{
		String old = "{\"runs\":[{\"startedAt\":1,\"teamSize\":1,\"outcome\":\"COMPLETED\",\"ticks\":900,"
			+ "\"splits\":[{\"encounter\":\"GUARDIAN\",\"startTick\":0,\"engageTick\":5,\"endTick\":50}]}]}";
		Archive read = GSON.fromJson(old, Archive.class).repair();
		Run run = read.getRuns().get(0);
		assertNull(run.split(0).getSeen());
		assertEquals(0, run.split(0).getSeenLevel());
		assertTrue(run.getUnknown().isEmpty());
	}
}
