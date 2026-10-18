package com.codex.archive.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.EnumMap;
import java.util.Map;
import org.junit.Test;

public class RunTrackerTest
{
	private static Map<Supply, Integer> pack(int food, int brews)
	{
		Map<Supply, Integer> m = new EnumMap<>(Supply.class);
		m.put(Supply.FOOD, food);
		m.put(Supply.BREWS, brews);
		return m;
	}

	/** Fights one boss: first hit at engage, falls at fall, then waits for the split to close. */
	private static int fight(RunTracker t, Encounter e, int engage, int fall)
	{
		t.engage(e, engage);
		t.fell(e, fall, false);
		for (int i = fall; i <= fall + RunTracker.FINAL_CONFIRM_TICKS; i++)
		{
			t.tick(i);
		}
		return fall + RunTracker.FINAL_CONFIRM_TICKS;
	}

	@Test
	public void timesAWholeRunFromTheEntrance()
	{
		RunTracker t = new RunTracker();
		t.begin(0, 100, 3, pack(20, 16));
		fight(t, Encounter.GUARDIAN, 110, 200);
		fight(t, Encounter.BORK, 230, 330);
		fight(t, Encounter.DREADNOTH, 360, 500);
		t.pack(pack(12, 8));
		fight(t, Encounter.TRIO, 520, 650);
		assertFalse(t.isFinished());
		fight(t, Encounter.HOODED_FIGURE, 700, 900);
		assertTrue(t.isFinished());

		Run run = t.finish(1000, false);
		assertEquals(Outcome.COMPLETED, run.getOutcome());
		assertEquals(800, run.getTicks());
		assertEquals(3, run.getTeamSize());
		assertEquals(5, run.getSplits().size());
		assertEquals("Bork, Dreadnoth, Trio", run.route());
		Split bork = run.split(1);
		assertEquals(100, bork.getStartTick());
		assertEquals(130, bork.getEngageTick());
		assertEquals(130, bork.roomTicks(0));
		assertEquals(100, bork.fightTicks(0));
		assertEquals(Integer.valueOf(8), run.getUsed().get("FOOD"));
		assertEquals(Integer.valueOf(8), run.getUsed().get("BREWS"));
		assertFalse(t.isActive());
	}

	@Test
	public void aPhaseChangeDoesNotCloseTheSplit()
	{
		RunTracker t = new RunTracker();
		t.begin(0, 0, 2, null);
		t.engage(Encounter.GUARDIAN, 5);
		t.fell(Encounter.GUARDIAN, 50, false);
		t.tick(52);
		// The next form is hit before the fall is confirmed.
		t.engage(Encounter.GUARDIAN, 53);
		for (int i = 53; i < 70; i++)
		{
			t.tick(i);
		}
		assertEquals(0, t.nextRow());
		t.fell(Encounter.GUARDIAN, 120, false);
		for (int i = 120; i < 130; i++)
		{
			t.tick(i);
		}
		assertEquals(1, t.nextRow());
		assertEquals(120, t.view(130).split(0).getEndTick());
	}

	@Test
	public void theTrioFallsWhenTheLastOneDoes()
	{
		RunTracker t = new RunTracker();
		t.begin(0, 0, 4, null);
		fight(t, Encounter.GUARDIAN, 1, 10);
		fight(t, Encounter.EZEKIEL, 20, 30);
		fight(t, Encounter.NECROMANCER, 40, 50);
		t.engage(Encounter.TRIO, 60);
		t.fell(Encounter.TRIO, 70, true);
		for (int i = 70; i < 90; i++)
		{
			t.tick(i);
		}
		assertEquals(3, t.nextRow());
		t.fell(Encounter.TRIO, 95, false);
		for (int i = 95; i < 110; i++)
		{
			t.tick(i);
		}
		assertEquals(4, t.nextRow());
		assertEquals(95, t.view(110).split(3).getEndTick());
	}

	@Test
	public void unnamedBossesAreKnownByTheirRow()
	{
		RunTracker t = new RunTracker();
		t.begin(0, 0, 3, null);
		assertNull(t.resolve("Mysterious warden", 300));
		fight(t, Encounter.GUARDIAN, 1, 10);
		fight(t, Encounter.BORK, 20, 30);
		assertEquals(Encounter.NECROMANCER, t.resolve("Mysterious warden", 300));
		assertNull("small NPCs are minions", t.resolve("Skeleton", 60));
		assertEquals(Encounter.DREADNOTH, t.resolve("Dreadnoth the Jellied", 400));
		fight(t, Encounter.DREADNOTH, 40, 50);
		assertNull(t.resolve("Something big", 300));
		fight(t, Encounter.XARTH, 60, 70);
		assertEquals(Encounter.HOODED_FIGURE, t.resolve("<col=ff0000>???</col>", 900));
		assertNull("no run, no guesses", new RunTracker().resolve("Mysterious warden", 300));
	}

	@Test
	public void aMissedFallClosesWhenTheNextBossIsHit()
	{
		RunTracker t = new RunTracker();
		t.begin(0, 0, 3, null);
		t.engage(Encounter.GUARDIAN, 5);
		t.engage(Encounter.BORK, 80);
		Run view = t.view(90);
		assertEquals(80, view.split(0).getEndTick());
		assertEquals(80, view.split(1).getStartTick());
		// A stale hit on an earlier row does nothing.
		t.engage(Encounter.GUARDIAN, 95);
		assertEquals(2, t.view(96).getSplits().size());
	}

	@Test
	public void wipesAndEarlyExits()
	{
		RunTracker t = new RunTracker();
		t.begin(0, 0, 2, null);
		fight(t, Encounter.GUARDIAN, 1, 10);
		t.engage(Encounter.BORK, 20);
		t.died();
		Run wiped = t.finish(60, true);
		assertEquals(Outcome.WIPED, wiped.getOutcome());
		assertEquals(60, wiped.getTicks());
		assertEquals(1, wiped.getDeaths());
		assertFalse(wiped.split(1).isDone());

		t.begin(0, 0, 2, null);
		Run left = t.finish(30, false);
		assertEquals(Outcome.LEFT, left.getOutcome());
	}

	@Test
	public void partySizeGrowsAsRaidersArrive()
	{
		RunTracker t = new RunTracker();
		t.begin(0, 0, 1, null);
		t.seeTeam(4);
		t.seeTeam(2);
		assertEquals(4, t.view(1).getTeamSize());
		fight(t, Encounter.GUARDIAN, 1, 10);
		t.seeTeam(6);
		assertEquals(4, t.view(20).getTeamSize());
	}

	@Test
	public void aLongPhaseChangeReopensTheSplit()
	{
		RunTracker t = new RunTracker();
		t.begin(0, 0, 3, null);
		t.engage(Encounter.GUARDIAN, 5);
		t.fell(Encounter.GUARDIAN, 60, false);
		// A cutscene between forms, longer than the confirmation.
		for (int i = 60; i < 80; i++)
		{
			t.tick(i);
		}
		assertEquals(1, t.nextRow());
		t.engage(Encounter.GUARDIAN, 85);
		assertEquals("the second form reopens the split", 0, t.nextRow());
		fight(t, Encounter.GUARDIAN, 86, 150);
		Run view = t.view(170);
		assertEquals(1, view.getSplits().size());
		assertEquals(150, view.split(0).getEndTick());
		assertEquals(5, view.split(0).getEngageTick());
	}

	@Test
	public void aBossLongDownIsNotReopened()
	{
		RunTracker t = new RunTracker();
		t.begin(0, 0, 3, null);
		fight(t, Encounter.GUARDIAN, 5, 60);
		for (int i = 70; i < 70 + RunTracker.REOPEN_TICKS + 10; i++)
		{
			t.tick(i);
		}
		t.engage(Encounter.GUARDIAN, 200);
		assertEquals(1, t.nextRow());
		assertEquals(60, t.view(201).split(0).getEndTick());
	}

	@Test
	public void aGuessAtAnUnnamedBossGivesWayToTheNamedOne()
	{
		RunTracker t = new RunTracker();
		t.begin(0, 0, 3, null);
		fight(t, Encounter.GUARDIAN, 1, 10);
		fight(t, Encounter.BORK, 20, 30);
		// A big add in the Dreadnoth room is hit first and taken for the Necromancer.
		Encounter guess = t.resolve("Jellied spawn", 200);
		assertEquals(Encounter.NECROMANCER, guess);
		t.engage(guess, 45);
		t.engage(Encounter.DREADNOTH, 50);
		fight(t, Encounter.DREADNOTH, 51, 120);
		Split s = t.view(140).split(2);
		assertEquals(Encounter.DREADNOTH, s.getEncounter());
		assertEquals(50, s.getEngageTick());
		assertEquals(120, s.getEndTick());
	}

	@Test
	public void theGameSayingCompleteEndsTheRunEvenWithTheLastBossUnseen()
	{
		RunTracker t = new RunTracker();
		t.begin(0, 100, 2, null);
		fight(t, Encounter.GUARDIAN, 110, 200);
		fight(t, Encounter.EZEKIEL, 230, 330);
		fight(t, Encounter.DREADNOTH, 360, 500);
		fight(t, Encounter.XARTH, 520, 650);
		// The final boss is never recognised; the completion message comes.
		t.complete(900);
		assertTrue(t.isFinished());
		assertTrue(t.isAnnounced());
		Run run = t.finish(950, false);
		assertEquals(Outcome.COMPLETED, run.getOutcome());
		assertEquals(800, run.getTicks());
		assertEquals(4, run.getSplits().size());
	}

	@Test
	public void theGameSayingCompleteKeepsTheFinalFall()
	{
		RunTracker t = new RunTracker();
		t.begin(0, 0, 2, null);
		fight(t, Encounter.GUARDIAN, 1, 10);
		fight(t, Encounter.BORK, 20, 30);
		fight(t, Encounter.DREADNOTH, 40, 50);
		fight(t, Encounter.TRIO, 60, 70);
		t.engage(Encounter.HOODED_FIGURE, 90);
		t.fell(Encounter.HOODED_FIGURE, 200, false);
		t.tick(201);
		assertEquals("the clock stops at the fall while it is confirmed", 200, t.elapsed(203));
		t.complete(202);
		Run run = t.finish(260, false);
		assertEquals(200, run.getTicks());
		assertEquals(200, run.split(4).getEndTick());
		// Nothing reopens once the game has spoken.
		t.engage(Encounter.HOODED_FIGURE, 205);
		assertFalse(t.isActive());
	}

	@Test
	public void anOpenEarlierBossIsDroppedWhenTheRunCompletes()
	{
		RunTracker t = new RunTracker();
		t.begin(0, 0, 2, null);
		fight(t, Encounter.GUARDIAN, 1, 10);
		t.engage(Encounter.BORK, 20);
		t.complete(400);
		Run run = t.finish(410, false);
		assertEquals(Outcome.COMPLETED, run.getOutcome());
		assertNull(run.split(1));
		assertEquals(400, run.getTicks());
	}

	@Test
	public void theFirstFallReportedIsTheOneKept()
	{
		RunTracker t = new RunTracker();
		t.begin(0, 0, 2, null);
		t.engage(Encounter.GUARDIAN, 5);
		t.fell(Encounter.GUARDIAN, 40, false);
		// The body leaving a few ticks later reports again.
		t.fell(Encounter.GUARDIAN, 44, false);
		for (int i = 40; i < 60; i++)
		{
			t.tick(i);
		}
		assertEquals(40, t.view(60).split(0).getEndTick());
	}

	@Test
	public void everyPurpleOfARunIsKept()
	{
		RunTracker t = new RunTracker();
		t.begin(0, 0, 2, null);
		t.purple("Rondache");
		t.purple("Zeal");
		assertEquals(2, t.purples().size());
		assertEquals("Rondache", t.view(1).getPurple());
		t.begin(0, 0, 2, null);
		assertTrue(t.purples().isEmpty());
	}
}
