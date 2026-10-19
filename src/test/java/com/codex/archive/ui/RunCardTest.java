package com.codex.archive.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.codex.archive.core.Run;
import com.codex.archive.core.Standing;
import com.codex.archive.core.StandingTestRuns;
import java.awt.image.BufferedImage;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class RunCardTest
{
	@Test
	public void saysWhatTheRunWas()
	{
		Run first = StandingTestRuns.run(1_000, 3, false, 100, 300, 500, 700, 1000);
		Run best = StandingTestRuns.run(2_000, 3, false, 90, 310, 480, 690, 948);
		Run slower = StandingTestRuns.run(3_000, 3, false, 90, 310, 480, 690, 1018);
		Run level = StandingTestRuns.run(4_000, 3, false, 90, 310, 480, 690, 948);
		Run wipe = StandingTestRuns.run(5_000, 3, true, 90, 310);
		List<Run> runs = Arrays.asList(first, best, slower, level, wipe);
		assertEquals("First trio clear", RunCard.verdict(Standing.of(runs, first)));
		assertEquals("New personal best, 31.2s off", RunCard.verdict(Standing.of(runs, best)));
		assertEquals("+42.0 on your best", RunCard.verdict(Standing.of(runs, slower)));
		assertEquals("Level with your best", RunCard.verdict(Standing.of(runs, level)));
		assertEquals("Wiped at Dreadnoth", RunCard.verdict(Standing.of(runs, wipe)));

		// Drawn at twice the panel's scale, the full width of a card, for any of them.
		for (Run r : runs)
		{
			BufferedImage card = RunCard.render(Standing.of(runs, r), "Gilded Owl", ZoneOffset.UTC);
			assertEquals(RunCard.WIDTH * RunCard.SCALE, card.getWidth());
			assertTrue(card.getHeight() > 300);
		}
	}
}
