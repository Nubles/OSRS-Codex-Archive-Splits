package com.codex.archive.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class NamesTest
{
	@Test
	public void bossesByName()
	{
		assertEquals(Encounter.GUARDIAN, Encounter.byName("Archive Guardian"));
		assertEquals(Encounter.BORK, Encounter.byName("Bork"));
		assertNull("Bork's minions are not Bork", Encounter.byName("Smork"));
		assertEquals(Encounter.DREADNOTH, Encounter.byName("Dreadnoth the Jellied"));
		assertEquals(Encounter.TRIO, Encounter.byName("Dagon'hai Elder"));
		assertEquals(Encounter.XARTH, Encounter.byName("<col=ffff00>Xarth</col>"));
		assertNull(Encounter.byName(null));
	}

	@Test
	public void suppliesByName()
	{
		assertEquals(Supply.FOOD, Supply.of("Shark"));
		assertEquals(Supply.BREWS, Supply.of("Saradomin brew(4)"));
		assertEquals(3, Supply.BREWS.units("Saradomin brew(3)"));
		assertEquals(Supply.RESTORES, Supply.of("Super restore(2)"));
		assertEquals(Supply.RESTORES, Supply.of("Prayer potion(4)"));
		assertEquals(Supply.BOOSTS, Supply.of("Divine super combat potion(4)"));
		assertEquals(Supply.BOOSTS, Supply.of("Ranging potion(1)"));
		assertNull(Supply.of("Antidote++(4)"));
		assertNull(Supply.of("Coins"));
		assertEquals(0, Supply.FOOD.units("Saradomin brew(4)"));
	}

	@Test
	public void uniquesByName()
	{
		assertEquals("The Obligator", Spoils.unique("The Obligator"));
		assertEquals("Ascension crossbow", Spoils.unique("Ascension crossbow"));
		assertEquals("Zorya's tome", Spoils.unique("Zorya's tome"));
		assertEquals("Zeal", Spoils.unique("Scroll of zeal"));
		assertNull("not a Zeal", Spoils.unique("Zealot's robe top"));
		assertNull(Spoils.unique("Ascension shard"));
		assertNull(Spoils.unique("Ascension bolts"));
		assertEquals("Elemental fragment", Spoils.unique("Elemental fragment (fire)"));
	}
}
