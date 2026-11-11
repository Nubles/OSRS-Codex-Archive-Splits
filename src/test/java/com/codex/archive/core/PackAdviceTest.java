package com.codex.archive.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class PackAdviceTest
{
	private static Map<Supply, Integer> pack(int food, int brews, int restores, int boosts)
	{
		Map<Supply, Integer> m = new EnumMap<>(Supply.class);
		m.put(Supply.FOOD, food);
		m.put(Supply.BREWS, brews);
		m.put(Supply.RESTORES, restores);
		m.put(Supply.BOOSTS, boosts);
		return m;
	}

	/** A run that carried 20 food, 16 brews, 16 restores and 4 boosts in and ends holding what is given. */
	private static Run run(long at, int team, boolean clear, Map<Supply, Integer> end)
	{
		RunTracker t = new RunTracker();
		t.begin(at, 0, team, pack(20, 16, 16, 4));
		t.engage(Encounter.GUARDIAN, 10);
		t.pack(end);
		if (clear)
		{
			t.fell(Encounter.GUARDIAN, 100, false);
			for (int i = 100; i <= 100 + RunTracker.FINAL_CONFIRM_TICKS; i++)
			{
				t.tick(i);
			}
			t.complete(200);
			return t.finish(200, false);
		}
		t.died();
		// The wipe takes the pack to the gravestone outside; none of it was eaten.
		t.pack(pack(0, 0, 0, 0));
		return t.finish(300, true);
	}

	@Test
	public void suggestsTheMostAClearUsedWithASpare()
	{
		List<Run> runs = new ArrayList<>();
		// Used food 8, 11, 9; brews 10, 13, 6; restores 7, 9, 4; boosts 4, 4, 2.
		runs.add(run(1, 3, true, pack(12, 6, 9, 0)));
		runs.add(run(2, 3, true, pack(9, 3, 7, 0)));
		runs.add(run(3, 3, true, pack(11, 10, 12, 2)));
		PackAdvice.Suggestion s = PackAdvice.suggest(runs, Records.bucket(3));
		assertNotNull(s);
		assertEquals(Records.bucket(3), s.bucket);
		assertEquals(3, s.clears);
		assertEquals(11, s.most(Supply.FOOD));
		assertEquals(13, s.most(Supply.BREWS));
		// 11 food and a tenth of it rounded up: 13.
		assertEquals(13, s.amount(Supply.FOOD));
		// 13 doses and 2 to spare is 15, so four whole potions.
		assertEquals(16, s.amount(Supply.BREWS));
		// 9 and 1 is 10: three potions.
		assertEquals(12, s.amount(Supply.RESTORES));
		// 4 and 1 is 5: two potions.
		assertEquals(8, s.amount(Supply.BOOSTS));
		assertFalse(s.matches(pack(10, 12, 12, 4)));
		assertTrue(s.matches(s.amounts()));
	}

	@Test
	public void theSpareRoundsUpAndNeverPassesWhatTheSettingsTake()
	{
		assertEquals(0, PackAdvice.amount(Supply.FOOD, 0));
		assertEquals(2, PackAdvice.amount(Supply.FOOD, 1));
		assertEquals(11, PackAdvice.amount(Supply.FOOD, 10));
		assertEquals(28, PackAdvice.amount(Supply.FOOD, 27));
		assertEquals(4, PackAdvice.amount(Supply.RESTORES, 1));
		assertEquals(8, PackAdvice.amount(Supply.RESTORES, 4));
		assertEquals(112, PackAdvice.amount(Supply.BREWS, 110));
	}

	@Test
	public void onlyTheLatestTenClearsOfTheLastPartySizeCount()
	{
		List<Run> runs = new ArrayList<>();
		// An old trio clear that ate everything, then ten lighter ones.
		runs.add(run(0, 3, true, pack(0, 0, 0, 0)));
		for (int i = 1; i <= 10; i++)
		{
			runs.add(run(i, 3, true, pack(15, 12, 12, 4)));
		}
		// A solo clear in between is another party size.
		runs.add(run(11, 1, true, pack(2, 0, 0, 0)));
		PackAdvice.Suggestion s = PackAdvice.suggest(runs, Records.bucket(3));
		assertEquals(10, s.clears);
		assertEquals(5, s.most(Supply.FOOD));
		assertEquals(0, s.amount(Supply.BOOSTS));
	}

	@Test
	public void tooFewClearsOfThePartyFallBackOnEverySize()
	{
		List<Run> runs = new ArrayList<>();
		runs.add(run(1, 1, true, pack(10, 8, 8, 4)));
		runs.add(run(2, 2, true, pack(10, 8, 8, 4)));
		runs.add(run(3, 3, true, pack(14, 8, 8, 4)));
		PackAdvice.Suggestion s = PackAdvice.suggest(runs, Records.bucket(3));
		assertEquals(-1, s.bucket);
		assertEquals(10, s.most(Supply.FOOD));
		assertNull(PackAdvice.suggest(runs.subList(0, 2), Records.bucket(3)));
		assertNull(PackAdvice.suggest(new ArrayList<>(), -1));
	}

	@Test
	public void clearsThatUsedNothingSuggestNothing()
	{
		List<Run> runs = new ArrayList<>();
		for (int i = 0; i < 4; i++)
		{
			runs.add(run(i, 3, true, pack(20, 16, 16, 4)));
		}
		assertNull(PackAdvice.suggest(runs, Records.bucket(3)));
	}

	@Test
	public void aWipeKeepsWhatThePackHeldWhenThePlayerWentDown()
	{
		Run wipe = run(1, 3, false, pack(6, 0, 3, 4));
		assertEquals(Outcome.WIPED, wipe.getOutcome());
		assertTrue(wipe.knowsLeft());
		assertEquals(Integer.valueOf(0), wipe.getLeft().get("BREWS"));
		assertEquals(Integer.valueOf(6), wipe.getLeft().get("FOOD"));
		// The gravestone took the rest; only what went before the fall was used.
		assertEquals(Integer.valueOf(14), wipe.getUsed().get("FOOD"));
		assertEquals(Integer.valueOf(16), wipe.getUsed().get("BREWS"));
		assertNull(wipe.getUsed().get("BOOSTS"));
	}

	@Test
	public void aWipeWithNoFallSeenLeavesWhatWasLeftUnknown()
	{
		RunTracker t = new RunTracker();
		t.begin(0, 0, 3, pack(20, 16, 16, 4));
		t.engage(Encounter.GUARDIAN, 10);
		Run r = t.finish(100, true);
		assertEquals(Outcome.WIPED, r.getOutcome());
		assertFalse(r.knowsLeft());
		assertTrue(r.getLeft().isEmpty());
	}

	@Test
	public void aClearKeepsWhatWasLeftOfWhatItCarried()
	{
		RunTracker t = new RunTracker();
		t.begin(0, 0, 3, pack(20, 16, 0, 0));
		t.engage(Encounter.GUARDIAN, 10);
		t.pack(pack(8, 0, 0, 0));
		t.complete(100);
		Run r = t.finish(100, false);
		assertEquals(Integer.valueOf(8), r.getLeft().get("FOOD"));
		assertEquals(Integer.valueOf(0), r.getLeft().get("BREWS"));
		// Never carried, so never run out of.
		assertFalse(r.getLeft().containsKey("RESTORES"));
	}

	@Test
	public void wipeNotesCountTheSuppliesWipesRanOutOf()
	{
		List<Run> runs = new ArrayList<>();
		runs.add(run(1, 3, false, pack(6, 0, 3, 4)));
		runs.add(run(2, 3, true, pack(6, 0, 3, 4)));
		runs.add(run(3, 3, false, pack(0, 0, 5, 4)));
		runs.add(run(4, 3, false, pack(2, 4, 5, 4)));
		PackAdvice.Wipes w = PackAdvice.wipes(runs, Records.bucket(3));
		assertEquals(3, w.wipes);
		assertEquals(2, w.ranOut(Supply.BREWS));
		assertEquals(1, w.ranOut(Supply.FOOD));
		assertEquals(0, w.ranOut(Supply.RESTORES));
		assertEquals(Arrays.asList(Supply.BREWS, Supply.FOOD), w.shortOf());
	}

	@Test
	public void wipesFromBeforeTheLeftoversWereKeptAreNotCounted()
	{
		Run old = new Gson().fromJson("{\"startedAt\":5,\"teamSize\":3,\"outcome\":\"WIPED\",\"ticks\":900,\"used\":{\"FOOD\":20}}", Run.class).repair();
		assertFalse(old.knowsLeft());
		assertNull(PackAdvice.wipes(Arrays.asList(old), -1));
		// A wipe of another party size is better than none.
		Run duo = run(6, 2, false, pack(0, 4, 4, 4));
		PackAdvice.Wipes w = PackAdvice.wipes(Arrays.asList(old, duo), Records.bucket(3));
		assertEquals(-1, w.bucket);
		assertEquals(1, w.ranOut(Supply.FOOD));
	}

	@Test
	public void leftoversSurviveASaveAndACopy()
	{
		Archive a = new Archive();
		a.record(run(1, 3, false, pack(6, 0, 3, 4)));
		Gson gson = new Gson();
		Archive.Loaded back = Archive.read(gson, gson.toJson(a));
		assertFalse(back.damaged);
		Run r = back.archive.last();
		assertEquals(Integer.valueOf(0), r.getLeft().get("BREWS"));
		assertEquals(r.getLeft(), back.archive.copy().last().getLeft());
		// A damaged figure is dropped, not kept below zero.
		Run bad = gson.fromJson("{\"outcome\":\"WIPED\",\"left\":{\"FOOD\":-2,\"BREWS\":null,\"RESTORES\":1}}", Run.class).repair();
		assertEquals(1, bad.getLeft().size());
	}

	@Test
	public void theLobbyAdvisesForTheLastRunsPartySize()
	{
		assertEquals(-1, PackAdvice.lobbyBucket(new ArrayList<>()));
		List<Run> runs = new ArrayList<>();
		runs.add(run(1, 3, true, pack(12, 6, 9, 0)));
		runs.add(run(2, 5, false, pack(12, 6, 9, 0)));
		assertEquals(Records.bucket(5), PackAdvice.lobbyBucket(runs));
	}
}
