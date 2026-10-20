package com.codex.archive.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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

	@Test
	public void launchIds()
	{
		assertEquals(Encounter.GUARDIAN, Encounter.of(16659, "Archive Guardian"));
		assertNull("the beaten husk is not the boss", Encounter.of(16658, "Archive Guardian"));
		assertEquals(Encounter.GUARDIAN, Encounter.fallMarker(16658));
		assertNull("Bork's Smorks are not Bork", Encounter.of(16641, "The Smorks"));
		assertEquals(Encounter.NECROMANCER, Encounter.of(16667, "Moia"));
		assertEquals("Moia", Encounter.NECROMANCER.getTitle());
		assertNull(Encounter.of(16670, "Undead binder"));
		assertEquals(Encounter.TRIO, Encounter.of(16646, "Dagon'hai Monk"));
		assertNull("imps are minions", Encounter.of(16650, "Imp"));
		assertNull("Ocellus before the fight is no boss", Encounter.of(16673, "Ocellus"));
		assertEquals(Encounter.HOODED_FIGURE, Encounter.of(16674, "Ocellus"));
		assertTrue(!Encounter.isFinalForm(16674) && !Encounter.isFinalForm(16675));
		assertTrue(Encounter.isFinalForm(16677) && Encounter.isFinalForm(16640));
		assertEquals(Encounter.XARTH, Encounter.fallMarker(16687));
		assertEquals("a later form, known by name", Encounter.XARTH, Encounter.of(17001, "Xarth"));
		assertEquals("The Obligator", Spoils.unique(34715, "Obligator"));
		assertEquals("Zeal", Spoils.unique(34679, "Horn of Bran"));
		assertEquals("Elemental fragment", Spoils.unique(34677, "Fragment of burning fire"));
		assertNull("ascension bolts are shared loot", Spoils.unique(34684, "Ascension bolts"));
		assertNull(Spoils.unique(34669, "Ascension shards"));
		assertEquals(Supply.FOOD, Supply.of("Leftovers"));
	}
}
