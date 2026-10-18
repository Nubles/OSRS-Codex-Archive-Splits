package com.codex.archive.core;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Times a run as it happens. The plugin feeds it what the client sees, in game ticks: the party
 * entering, the first hit on each boss, bosses falling, deaths and what is left in the pack. It
 * keeps every tick relative to the moment the party entered, which is what the splits show.
 *
 * <p>Several bosses change form part way through a fight, and a form "dying" is not the boss
 * falling, so a fall only closes the split once nothing of that encounter has been hit or seen
 * alive for a few ticks.
 */
public class RunTracker
{
	/** Ticks a fallen boss must stay fallen before its split closes, about four seconds. */
	public static final int CONFIRM_TICKS = 6;
	/** The lowest combat level the plugin treats as the boss of an unnamed row. */
	public static final int UNNAMED_BOSS_LEVEL = 150;

	private Run run;
	private int base;
	private int lastEnd;
	private Split open;
	private int pendingTick = -1;
	private int finalTick = -1;
	private int deaths;
	private final Map<Supply, Integer> last = new EnumMap<>(Supply.class);
	private final Map<Supply, Integer> used = new EnumMap<>(Supply.class);

	/** Starts a run. Any run already going is dropped, as the client can only be in one. */
	public void begin(long now, int tick, int teamSize, Map<Supply, Integer> pack)
	{
		run = new Run(now, Math.max(1, teamSize));
		base = tick;
		lastEnd = 0;
		open = null;
		pendingTick = -1;
		finalTick = -1;
		deaths = 0;
		last.clear();
		used.clear();
		if (pack != null)
		{
			last.putAll(pack);
		}
	}

	public boolean isActive()
	{
		return run != null;
	}

	/** The row the next boss will be in: the number of bosses already down. */
	public int nextRow()
	{
		if (run == null)
		{
			return 0;
		}
		int done = 0;
		for (Split s : run.splitsMutable())
		{
			if (s.isDone())
			{
				done = Math.max(done, s.getEncounter().getRow() + 1);
			}
		}
		return done;
	}

	public boolean isFinished()
	{
		return finalTick >= 0;
	}

	/** Raises the party size when more raiders are seen than at the start. */
	public void seeTeam(int size)
	{
		if (run != null && size > run.getTeamSize() && open == null && nextRow() == 0)
		{
			run.setTeamSize(size);
		}
	}

	/**
	 * Names the encounter an NPC belongs to. Two bosses have no name in the game's data yet, so a
	 * big NPC that matches nothing, met in the row one of them guards, is taken to be that boss.
	 *
	 * @return the encounter, or null when the NPC is not a boss of this run
	 */
	public Encounter resolve(String npcName, int combatLevel)
	{
		Encounter named = Encounter.byName(npcName);
		if (named != null || run == null || combatLevel < UNNAMED_BOSS_LEVEL)
		{
			return named;
		}
		if (open != null)
		{
			return open.getEncounter().isUnnamed() ? open.getEncounter() : null;
		}
		for (Encounter e : Encounter.ofRow(nextRow()))
		{
			if (e.isUnnamed())
			{
				return e;
			}
		}
		return null;
	}

	/** A boss was hit, or seen fighting. The first time opens its split. */
	public void engage(Encounter encounter, int tick)
	{
		if (run == null || encounter == null || finalTick >= 0)
		{
			return;
		}
		int t = tick - base;
		if (open != null)
		{
			if (open.getEncounter() == encounter)
			{
				// Still fighting: a form that fell was only a phase.
				pendingTick = -1;
				return;
			}
			if (encounter.getRow() <= open.getEncounter().getRow())
			{
				return;
			}
			// A later boss is up, so the open one fell without the client seeing it.
			close(pendingTick >= 0 ? pendingTick : t);
		}
		if (encounter.getRow() < nextRow())
		{
			return;
		}
		open = new Split(encounter, lastEnd, t);
		run.splitsMutable().add(open);
	}

	/**
	 * A boss NPC fell.
	 *
	 * @param anyAlive whether another NPC of the same encounter is still standing, as with the trio
	 */
	public void fell(Encounter encounter, int tick, boolean anyAlive)
	{
		if (run == null || open == null || open.getEncounter() != encounter)
		{
			return;
		}
		if (anyAlive)
		{
			pendingTick = -1;
			return;
		}
		pendingTick = tick - base;
	}

	/** Closes a fallen boss's split once it has stayed down. */
	public void tick(int tick)
	{
		if (run != null && open != null && pendingTick >= 0 && tick - base - pendingTick >= CONFIRM_TICKS)
		{
			close(pendingTick);
		}
	}

	private void close(int t)
	{
		open.end(t);
		lastEnd = t;
		if (open.getEncounter().getRow() == Encounter.ROWS - 1)
		{
			finalTick = t;
		}
		open = null;
		pendingTick = -1;
	}

	public void died()
	{
		if (run != null)
		{
			deaths++;
		}
	}

	/** What the pack holds now. Anything less than before was eaten or drunk; a resupply adding more is not counted. */
	public void pack(Map<Supply, Integer> units)
	{
		if (run == null || units == null)
		{
			return;
		}
		for (Supply s : Supply.values())
		{
			int now = units.getOrDefault(s, 0);
			int before = last.getOrDefault(s, now);
			if (now < before)
			{
				used.merge(s, before - now, Integer::sum);
			}
			last.put(s, now);
		}
	}

	public void kc(int kc)
	{
		if (run != null)
		{
			run.setKc(kc);
		}
	}

	public void purple(String unique)
	{
		if (run != null && unique != null)
		{
			run.setPurple(unique);
		}
	}

	/**
	 * Ends the run as the player leaves the archive.
	 *
	 * @param wiped whether the game sent the party out for falling
	 * @return the finished run, or null when none was going
	 */
	public Run finish(int tick, boolean wiped)
	{
		if (run == null)
		{
			return null;
		}
		if (open != null && pendingTick >= 0)
		{
			close(pendingTick);
		}
		Outcome outcome = finalTick >= 0 ? Outcome.COMPLETED : wiped || deaths > 0 ? Outcome.WIPED : Outcome.LEFT;
		int ticks = finalTick >= 0 ? finalTick : tick - base;
		run.splitsMutable().removeIf(s -> !s.isDone() && outcome == Outcome.COMPLETED);
		run.finish(outcome, ticks, deaths, usedByTitle());
		Run done = run;
		run = null;
		open = null;
		return done;
	}

	/** A copy of the run so far, for the panel and overlay. */
	public Run view(int tick)
	{
		if (run == null)
		{
			return null;
		}
		Run copy = run.copy();
		int t = finalTick >= 0 ? finalTick : tick - base;
		copy.finish(finalTick >= 0 ? Outcome.COMPLETED : null, t, deaths, usedByTitle());
		return copy;
	}

	/** Ticks since the run began. */
	public int elapsed(int tick)
	{
		return run == null ? 0 : finalTick >= 0 ? finalTick : tick - base;
	}

	private Map<String, Integer> usedByTitle()
	{
		Map<String, Integer> out = new LinkedHashMap<>();
		for (Supply s : Supply.values())
		{
			int n = used.getOrDefault(s, 0);
			if (n > 0)
			{
				out.put(s.name(), n);
			}
		}
		return out;
	}
}
