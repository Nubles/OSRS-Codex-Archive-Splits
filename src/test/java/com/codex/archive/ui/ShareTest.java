package com.codex.archive.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.codex.archive.core.Comparison;
import com.codex.archive.core.Encounter;
import com.codex.archive.core.Run;
import com.codex.archive.core.RunTracker;
import java.util.Arrays;
import org.junit.Test;

public class ShareTest
{
	private static Run run(long at, int... falls)
	{
		Encounter[] route = {Encounter.GUARDIAN, Encounter.BORK, Encounter.DREADNOTH, Encounter.XARTH, Encounter.HOODED_FIGURE};
		RunTracker t = new RunTracker();
		t.begin(at, 0, 3, null);
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

	@Test
	public void splitsLineUpWithTheirDeltas()
	{
		Run best = run(1, 100, 200, 300, 400, 500);
		Run now = run(2, 95, 210, 300, 390, 480);
		String text = Share.splits(now, Comparison.of(Arrays.asList(best, now), 2, Comparison.Mode.PERSONAL_BEST, now));
		String[] lines = text.split("\n");
		assertEquals(7, lines.length);
		assertEquals("Guardian   0:57.0   room 0:57.0   -3.0", lines[1]);
		assertEquals("Bork       2:06.0   room 1:09.0   +6.0", lines[2]);
		assertEquals("Cleared in 4:48.0, a new best by 12.0s", lines[6]);
		assertTrue(text.endsWith("\n"));
	}

	@Test
	public void versusNamesTheMark()
	{
		assertEquals("4.2s ahead of your best rooms", Format.versus(-7, "your best rooms"));
		assertEquals("level with your typical run", Format.versus(0, "your typical run"));
		assertEquals("1.2s behind your best", Format.versus(2));
	}
}
