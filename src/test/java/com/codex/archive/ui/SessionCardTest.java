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
		assertEquals("Furthest: Dreadnoth", SessionCard.verdict(night, all));
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
}
