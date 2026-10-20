package com.codex.archive.core;

import java.util.Locale;

/**
 * The Fractured Archive's eight encounters. The Guardian always opens the raid and Ocellus, the
 * hooded figure, always ends it; each of the three rows between offers a left and a right door,
 * and both doors of a row are worth the same points, so a run is always five encounters long.
 *
 * <p>Bosses are known by the game's NPC ids, so minions (Smorks, jellies, undead binders, imps)
 * and scenery (inert guardians, swords, rifts, the Guardian's husk) never open or close a split.
 * The name is a fallback for forms the game adds later. The constants of the two bosses that had
 * no name before launch keep their old names, since saved runs refer to them.
 */
public enum Encounter
{
	GUARDIAN(0, "Archive Guardian", "Guardian", new String[]{"archive guardian"}),
	BORK(1, "Bork", "Bork", new String[]{"bork"}),
	EZEKIEL(1, "Ezekiel", "Ezekiel", new String[]{"ezekiel"}),
	DREADNOTH(2, "Dreadnoth the Jellied", "Dreadnoth", new String[]{"dreadnoth"}),
	NECROMANCER(2, "Moia", "Moia", new String[]{"moia"}),
	XARTH(3, "Xarth", "Xarth", new String[]{"xarth"}),
	TRIO(3, "Dagon'hai Trio", "Trio", new String[]{"dagon'hai", "dagonhai"}),
	HOODED_FIGURE(4, "Ocellus", "Ocellus", new String[]{"ocellus"});

	/** The ids of each boss's fighting forms, in the order of the constants above. */
	private static final int[][] IDS = {
		{16659, 16660},
		{16640},
		{16652},
		{16665},
		{16667},
		{16684},
		{16643, 16644, 16645, 16646, 16647, 16648},
		{16674, 16675, 16677},
	};
	/** Ocellus fights in three phases; only the third falling ends the raid. */
	private static final int OCELLUS_LAST_PHASE = 16677;
	private static final int OCELLUS_FIRST_PHASES_FROM = 16674;
	private static final int OCELLUS_FIRST_PHASES_TO = 16675;
	/** The forms a boss leaves behind once beaten: the Guardian's husk and Xarth's corpse. */
	private static final int GUARDIAN_DEAD = 16658;
	private static final int XARTH_CORPSE = 16687;
	private static final int XARTH_CORPSE_DOUSED = 16688;
	/** The block of ids the raid's NPCs were given; inside it, only the ids above are bosses. */
	private static final int RAID_IDS_FIRST = 16630;
	private static final int RAID_IDS_LAST = 16710;

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

	/**
	 * True when the boss of this row has no name the plugin can know, so it is recognised by its
	 * row instead. Since launch every boss has one; the row guess stays for any the game renames.
	 */
	public boolean isUnnamed()
	{
		return false;
	}

	/**
	 * The encounter an NPC is the boss of, by id first and by name for forms the game adds later.
	 *
	 * @return the encounter, or null for minions, scenery and every other NPC
	 */
	public static Encounter of(int npcId, String npcName)
	{
		Encounter[] all = values();
		for (int i = 0; i < all.length; i++)
		{
			for (int id : IDS[i])
			{
				if (id == npcId)
				{
					return all[i];
				}
			}
		}
		return npcId >= RAID_IDS_FIRST && npcId <= RAID_IDS_LAST ? null : byName(npcName);
	}

	/** Whether this NPC falling can be its boss falling: Ocellus's first two phases only become the next. */
	public static boolean isFinalForm(int npcId)
	{
		return npcId < OCELLUS_FIRST_PHASES_FROM || npcId > OCELLUS_FIRST_PHASES_TO || npcId == OCELLUS_LAST_PHASE;
	}

	/**
	 * Some bosses leave a form behind once beaten; its arrival is a sure sign of the fall.
	 *
	 * @return the encounter whose fall this NPC marks, or null
	 */
	public static Encounter fallMarker(int npcId)
	{
		switch (npcId)
		{
			case GUARDIAN_DEAD:
				return GUARDIAN;
			case XARTH_CORPSE:
			case XARTH_CORPSE_DOUSED:
				return XARTH;
			default:
				return null;
		}
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
				return "Dreadnoth / Moia";
			case 3:
				return "Xarth / Trio";
			default:
				return "Ocellus";
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
		if (name.startsWith("the "))
		{
			name = name.substring(4);
		}
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
		return name.replaceAll("<[^>]*>", "").replace('\u00A0', ' ').trim().toLowerCase(Locale.ENGLISH);
	}
}
