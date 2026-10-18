package com.codex.archive.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class LootWatchTest
{
	private static Map<String, Integer> held(String unique, int n)
	{
		Map<String, Integer> m = new HashMap<>();
		if (unique != null)
		{
			m.put(unique, n);
		}
		return m;
	}

	@Test
	public void swappingAndDroppingIsNotLoot()
	{
		LootWatch w = new LootWatch();
		w.open(held("Zorya's tome", 1));
		// Unequipped into the pack, dropped, picked up: never more than one held.
		assertTrue(w.arrivals(held("Zorya's tome", 1), 1).isEmpty());
		assertTrue(w.arrivals(held(null, 0), 2).isEmpty());
		assertTrue(w.arrivals(held("Zorya's tome", 1), 3).isEmpty());
		assertEquals(Collections.singletonList("Zorya's tome"), w.arrivals(held("Zorya's tome", 2), 4));
		assertTrue(w.arrivals(held("Zorya's tome", 2), 5).isEmpty());
	}

	@Test
	public void theWatchCloses()
	{
		LootWatch w = new LootWatch();
		assertTrue("never opened", w.arrivals(held("Zeal", 1), 0).isEmpty());
		w.open(null);
		w.closeAt(100);
		assertTrue(w.isOpen(100));
		assertFalse(w.isOpen(101));
		assertTrue(w.arrivals(held("Zeal", 1), 102).isEmpty());
		w.open(held(null, 0));
		w.close();
		assertTrue(w.arrivals(held("Zeal", 1), 103).isEmpty());
	}
}
