package com.codex.archive.core;

import java.util.ArrayList;
import java.util.List;

/**
 * What a run's times rest on, in plain words, for the runs where that is in doubt: a boss told by
 * its row and its size rather than its name, and the big foes a run fought that matched no boss.
 * Two of the raid's bosses have no name the plugin can know before launch, so this lets the player
 * see at a glance whether a split is the boss's or an add's, and delete the run when it is wrong.
 */
public final class Doubts
{
	private Doubts()
	{
	}

	/** One line per doubt, empty when every boss of the run was known by its name. */
	public static List<String> of(Run run)
	{
		List<String> lines = new ArrayList<>();
		if (run == null || !run.hasDoubts())
		{
			return lines;
		}
		for (int row = 0; row < Encounter.ROWS; row++)
		{
			Split s = run.split(row);
			if (s == null || s.getSeen() == null)
			{
				continue;
			}
			String level = s.getSeenLevel() > 0 ? ", level " + s.getSeenLevel() : "";
			lines.add(s.getEncounter().getShortName() + " was told by its size: " + s.getSeen() + level + ".");
		}
		List<String> unknown = run.getUnknown();
		List<Integer> untimed = run.untimedRows();
		if (!unknown.isEmpty() && !untimed.isEmpty())
		{
			StringBuilder missed = new StringBuilder();
			for (int row : untimed)
			{
				missed.append(missed.length() > 0 ? ", " : "").append(Encounter.rowLabel(row));
			}
			lines.add("Not timed: " + missed + ". Big foes no boss name matched: " + String.join(", ", unknown) + ".");
		}
		return lines;
	}
}
