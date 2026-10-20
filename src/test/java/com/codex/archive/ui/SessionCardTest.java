package com.codex.archive.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.codex.archive.core.Run;
import com.codex.archive.core.Sessions;
import com.codex.archive.core.StandingTestRuns;
import java.awt.image.BufferedImage;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class SessionCardTest
{
	private static final long MIN = 60_000L;

	private static Sessions.Session only(List<Run> runs)
	{
		List<Sessions.Session> all = Sessions.of(runs);
		assertEquals(1, all.size());
		return all.get(0);
	}

	@Test
	public void saysWhatTheNightBrought()
	{
		// An earlier night sets the best, so this one is measured against it.
		Run before = StandingTestRuns.run(0, 3, false, 90, 310, 480, 690, 948);
		Run a = StandingTestRuns.run(10 * 60 * MIN, 3, false, 90, 310, 480, 690, 1018);
		Run b = StandingTestRuns.run(10 * 60 * MIN + 15 * MIN, 3, false, 90, 310, 480, 690, 990);
		List<Run> all = Arrays.asList(before, a, b);
		Sessions.Session night = only(Arrays.asList(a, b));
		assertEquals("Within 25.2s of your best", SessionCard.verdict(night, all));
		assertEquals("2 clears, no wipes", SessionCard.tally(night));
		assertEquals("Trio", SessionCard.party(night));

		// A purple is told before anything else.
		StandingTestRuns.withPurple(b, "Zorya's tome");
		assertEquals("A purple: Zorya's tome", SessionCard.verdict(night, all));
		assertEquals("Zorya's tome", SessionCard.rows(night, all).get(1).end);
	}

	@Test
	public void tellsANewBestApartFromAFirstClear()
	{
		Run first = StandingTestRuns.run(0, 4, false, 100, 300, 500, 700, 1000);
		Run faster = StandingTestRuns.run(15 * MIN, 4, false, 90, 300, 480, 690, 950);
		List<Run> all = Arrays.asList(first, faster);
		Sessions.Session night = only(all);
		assertEquals("A new personal best", SessionCard.verdict(night, all));
		assertEquals("FIRST", SessionCard.rows(night, all).get(0).tag);
		assertEquals("PB", SessionCard.rows(night, all).get(1).tag);

		// A party of four is no "4 clear".
		Sessions.Session alone = only(Arrays.asList(first));
		assertEquals("A first 4-raider clear", SessionCard.verdict(alone, Arrays.asList(first)));
		assertEquals("6+ raider clear", Format.clear("6+"));
		assertEquals("trio clear", Format.clear("Trio"));
	}

	@Test
	public void aNightOfWipesSaysHowFarItGot()
	{
		Run w1 = StandingTestRuns.run(0, 2, true, 90);
		Run w2 = StandingTestRuns.run(15 * MIN, 2, true, 90, 310);
		List<Run> all = Arrays.asList(w1, w2);
		Sessions.Session night = only(all);
		assertEquals("Furthest: Dreadnoth the Jellied", SessionCard.verdict(night, all));
		// The clears are not counted when there are none.
		assertEquals("2 wipes", SessionCard.tally(night));
		assertNull(SessionCard.bestRun(night));
		BufferedImage card = SessionCard.render(night, all, null, ZoneOffset.UTC);
		assertEquals(RunCard.WIDTH * RunCard.SCALE, card.getWidth());
	}

	@Test
	public void aLongNightShowsItsLatestRuns()
	{
		List<Run> runs = new ArrayList<>();
		for (int i = 0; i < SessionCard.RUNS + 3; i++)
		{
			runs.add(StandingTestRuns.run(i * 20 * MIN, i % 2 == 0 ? 3 : 4, false, 90, 310, 480, 690, 1000 + i));
		}
		Sessions.Session night = only(runs);
		assertEquals("Mixed parties", SessionCard.party(night));
		BufferedImage longer = SessionCard.render(night, runs, "Gilded Owl", ZoneOffset.UTC);
		BufferedImage shorter = SessionCard.render(only(runs.subList(0, SessionCard.RUNS)), runs, "Gilded Owl", ZoneOffset.UTC);
		// Only the latest twelve are drawn, with one line saying the rest are not.
		assertTrue(longer.getHeight() - shorter.getHeight() < 40);
	}

	@Test
	public void aLongSittingKeepsItsBestInSightAndScalesToWhatIsShown()
	{
		// The sitting's best is its very first run, and the slowest is the second: both would be hidden.
		List<Run> runs = new ArrayList<>();
		for (int i = 0; i < SessionCard.RUNS + 3; i++)
		{
			int end = i == 0 ? 900 : i == 1 ? 1400 : 1000 + i;
			runs.add(StandingTestRuns.run(i * 20 * MIN, 3, false, 90, 310, 480, 690, end));
		}
		Sessions.Session night = only(runs);
		List<SessionCard.Row> rows = SessionCard.rows(night, runs);
		Run best = SessionCard.bestRun(night);
		List<Integer> shown = SessionCard.shown(rows, best);
		assertEquals(SessionCard.RUNS, shown.size());
		assertTrue("the best clear is drawn", shown.contains(0));
		assertTrue("the latest run is drawn", shown.contains(rows.size() - 1));
		assertTrue("the slowest hidden run is not", !shown.contains(1));
		// Bars end at the slowest run drawn, not at one left off the card.
		assertEquals(rows.get(rows.size() - 1).run.getTicks(), SessionCard.slowest(rows, shown));

		// A sitting that fits shows every run in order.
		List<SessionCard.Row> few = rows.subList(0, 5);
		assertEquals(Arrays.asList(0, 1, 2, 3, 4), SessionCard.shown(few, best));
	}

	@Test
	public void aSittingWithoutAClearSaysSoOnce()
	{
		Run w1 = StandingTestRuns.run(0, 2, true, 90, 310, 480);
		Run w2 = StandingTestRuns.run(15 * MIN, 2, true, 90, 310, 480, 690);
		List<Run> all = Arrays.asList(w1, w2);
		Sessions.Session night = only(all);
		// The big figure says No clear; nothing beneath it says it again.
		for (String[] tile : SessionCard.tiles(night))
		{
			for (String text : tile)
			{
				assertTrue(text, !text.toLowerCase().contains("clear"));
			}
		}
		String verdict = SessionCard.verdict(night, all);
		assertTrue(verdict, !verdict.toLowerCase().contains("clear"));
		// The deepest room is named in full, never only "Trio", which reads as a party size.
		assertEquals("Furthest: Ocellus", verdict);
		// What the runs did get done instead: three bosses then four.
		assertEquals(Arrays.asList("7", "Bosses", "felled"), Arrays.asList(SessionCard.tiles(night)[0]));

		// A duo stopped by the Dagon'hai is never "at Trio".
		Run duo = StandingTestRuns.via(new com.codex.archive.core.Encounter[]{com.codex.archive.core.Encounter.GUARDIAN,
			com.codex.archive.core.Encounter.EZEKIEL, com.codex.archive.core.Encounter.NECROMANCER, com.codex.archive.core.Encounter.TRIO},
			40 * MIN, 2, true, 90, 310, 480);
		assertEquals("Wiped at the Trio", RunCard.verdict(com.codex.archive.core.Standing.of(Arrays.asList(duo), duo)));
		assertEquals("Furthest: Dagon'hai Trio", SessionCard.verdict(only(Arrays.asList(duo)), Arrays.asList(duo)));
	}

	@Test
	public void timeAtTheArchiveAgreesWithTheClockLine()
	{
		// 15:04:50 to 16:15:10 reads "15:04 to 16:15" on the card: 71 minutes by the clock line.
		long start = (15 * 60 + 4) * MIN + 50_000;
		long end = (16 * 60 + 15) * MIN + 10_000;
		assertEquals("1h 11m", SessionCard.span(start, end));
		assertEquals("1m", SessionCard.span(start, start + 5_000));
		assertEquals("48m", SessionCard.span(0, 48 * MIN + 59_000));
	}
}
