package com.codex.archive.core;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The archive's uniques, the purples. They are matched by the game's item ids, and by name for
 * any form the game adds later; the shared loot (ascension shards and bolts, the folio, living
 * records, regular drops) never counts.
 */
public final class Spoils
{
	/** Name fragments of the uniques, most specific first. */
	private static final String[][] UNIQUES = {
		{"the obligator", "The Obligator"},
		{"obligator", "The Obligator"},
		{"ascension crossbow", "Ascension crossbow"},
		{"zorya's tome", "Zorya's tome"},
		{"zorya", "Zorya's tome"},
		{"rondache", "Rondache"},
		{"zeal", "Zeal"},
		{"elemental fragment", "Elemental fragment"},
		{"horn of bran", "Zeal"},
		{"fragment of living earth", "Elemental fragment"},
		{"fragment of deep water", "Elemental fragment"},
		{"fragment of whispering wind", "Elemental fragment"},
		{"fragment of burning fire", "Elemental fragment"},
		{"fragment of air", "Elemental fragment"},
		{"fragment of water", "Elemental fragment"},
		{"fragment of earth", "Elemental fragment"},
		{"fragment of fire", "Elemental fragment"},
	};

	private static final Map<Integer, String> BY_ID = new HashMap<>();

	static
	{
		BY_ID.put(34715, "The Obligator");
		BY_ID.put(34717, "The Obligator");
		BY_ID.put(34681, "Ascension crossbow");
		BY_ID.put(34725, "Zorya's tome");
		BY_ID.put(34727, "Zorya's tome");
		BY_ID.put(34719, "Rondache");
		// Zeal is unlocked from the Horn of Bran.
		BY_ID.put(34679, "Zeal");
		// Fragments of living earth, deep water, whispering wind and burning fire.
		BY_ID.put(34671, "Elemental fragment");
		BY_ID.put(34673, "Elemental fragment");
		BY_ID.put(34675, "Elemental fragment");
		BY_ID.put(34677, "Elemental fragment");
	}

	/** The uniques in the order the panel lists them. */
	public static final String[] ALL = {"The Obligator", "Ascension crossbow", "Zorya's tome", "Rondache", "Zeal", "Elemental fragment"};

	private Spoils()
	{
	}

	/**
	 * @return the unique this item is, by the panel's name for it, or null for anything else
	 */
	public static String unique(int itemId, String itemName)
	{
		String known = BY_ID.get(itemId);
		return known != null ? known : unique(itemName);
	}

	/**
	 * @return the unique an item of this name is, or null for anything else
	 */
	public static String unique(String itemName)
	{
		if (itemName == null)
		{
			return null;
		}
		String name = Encounter.clean(itemName);
		if (name.contains("ornament") || name.endsWith("(broken)") || name.contains("bolts"))
		{
			return null;
		}
		for (String[] u : UNIQUES)
		{
			// Whole words only, so a Zealot's robe is not Zeal.
			if ((" " + name.replaceAll("[^a-z' ]", " ") + " ").contains(" " + u[0].toLowerCase(Locale.ENGLISH) + " "))
			{
				return u[1];
			}
		}
		return null;
	}

	/** A short name that fits a small tablet. */
	public static String shortName(String unique)
	{
		switch (unique)
		{
			case "The Obligator":
				return "Obligator";
			case "Ascension crossbow":
				return "Crossbows";
			case "Zorya's tome":
				return "Tome";
			case "Elemental fragment":
				return "Fragment";
			default:
				return unique;
		}
	}

	/** The megarare has its own colour in the panel. */
	public static boolean isMegarare(String unique)
	{
		return "The Obligator".equals(unique);
	}
}
