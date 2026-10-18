package com.codex.archive.core;

import java.util.Locale;

/**
 * The Fractured Archive's eight encounters. The Guardian always opens the raid and the hooded
 * figure always ends it; each of the three rows between offers a left and a right door, and both
 * doors of a row are worth the same points, so a run is always five encounters long.
 */
public enum Encounter
{
	GUARDIAN(0, "Archive Guardian", "Guardian", new String[]{"archive guardian"}),
	BORK(1, "Bork", "Bork", new String[]{"bork"}),
	EZEKIEL(1, "Ezekiel", "Ezekiel", new String[]{"ezekiel"}),
	DREADNOTH(2, "Dreadnoth the Jellied", "Dreadnoth", new String[]{"dreadnoth"}),
	NECROMANCER(2, "The Necromancer", "Necromancer", new String[]{"necromancer"}),
	XARTH(3, "Xarth", "Xarth", new String[]{"xarth"}),
	TRIO(3, "Dagon'hai Trio", "Trio", new String[]{"dagon'hai", "dagonhai"}),
	HOODED_FIGURE(4, "The Hooded Figure", "Final", new String[]{"hooded figure"});

	/** Five encounters in every run. */
	public static final int ROWS = 5;

	private final int row;
	private final String title;
	private final String shortName;
	private final String[] names;

	Encounter(int row, String title, String shortName, String[] names)
	{
		this.row = row;
		this.title = title;
		this.shortName = shortName;
		this.names = names;
	}

	public int getRow()
	{
		return row;
	}

	public String getTitle()
	{
		return title;
	}

	public String getShortName()
	{
		return shortName;
	}

	/** True when the boss of this row is still unnamed in the game's data, so it is recognised by its row instead. */
	public boolean isUnnamed()
	{
		return this == NECROMANCER || this == HOODED_FIGURE;
	}

	/** The two encounters a row can hold, or one for the first and last rows. */
	public static Encounter[] ofRow(int row)
	{
		switch (row)
		{
			case 0:
				return new Encounter[]{GUARDIAN};
			case 1:
				return new Encounter[]{BORK, EZEKIEL};
			case 2:
				return new Encounter[]{DREADNOTH, NECROMANCER};
			case 3:
				return new Encounter[]{XARTH, TRIO};
			case 4:
				return new Encounter[]{HOODED_FIGURE};
			default:
				return new Encounter[0];
		}
	}

	/** The row's name in the panel when no run has chosen a door yet. */
	public static String rowLabel(int row)
	{
		switch (row)
		{
			case 0:
				return "Guardian";
			case 1:
				return "Bork / Ezekiel";
			case 2:
				return "Dreadnoth / Necromancer";
			case 3:
				return "Xarth / Trio";
			default:
				return "Final";
		}
	}

	/**
	 * Matches an NPC by name. Minions of an encounter (Bork's Smorks, say) are not the boss and
	 * do not match, so only the boss's own name opens and closes a split.
	 *
	 * @return the encounter, or null for any other NPC
	 */
	public static Encounter byName(String npcName)
	{
		if (npcName == null)
		{
			return null;
		}
		String name = clean(npcName);
		for (Encounter e : values())
		{
			for (String n : e.names)
			{
				if (name.equals(n) || name.startsWith(n + " ") || (e == TRIO && name.startsWith(n)))
				{
					return e;
				}
			}
		}
		return null;
	}

	/** Strips the colour tags the game puts in some names, and lowercases what is left. */
	static String clean(String name)
	{
		return name.replaceAll("<[^>]*>", "").replace(' ', ' ').trim().toLowerCase(Locale.ENGLISH);
	}
}
