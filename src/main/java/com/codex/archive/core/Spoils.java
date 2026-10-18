package com.codex.archive.core;

import java.util.Locale;

/**
 * The archive's uniques, the purples. They are matched by name, since the raid's item ids are
 * new, and the shared ones (ascension shards, regular loot) never count.
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
		{"fragment of air", "Elemental fragment"},
		{"fragment of water", "Elemental fragment"},
		{"fragment of earth", "Elemental fragment"},
		{"fragment of fire", "Elemental fragment"},
	};

	/** The uniques in the order the panel lists them. */
	public static final String[] ALL = {"The Obligator", "Ascension crossbow", "Zorya's tome", "Rondache", "Zeal", "Elemental fragment"};

	private Spoils()
	{
	}

	/**
	 * @return the unique this item is, by the panel's name for it, or null for anything else
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
