package com.codex.archive.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Times a run as it happens. The plugin feeds it what the client sees, in game ticks: the party
 * entering, the first hit on each boss, bosses falling, deaths and what is left in the pack. It
 * keeps every tick relative to the moment the party entered, which is what the splits show.
 *
 * <p>Several bosses change form part way through a fight, and a form "dying" is not the boss
 * falling, so a fall only closes the split once nothing of that encounter has been hit or seen
 * alive for a few ticks. Should the boss come back after that (a long phase change), the split
 * opens again, so a phase never leaves a short false time behind.
 */
public class RunTracker
{
	/** Ticks a fallen boss must stay fallen before its split closes, about four seconds. */
	public static final int CONFIRM_TICKS = 6;
	/** The final boss waits longer, since closing it ends the run: six seconds. */
	public static final int FINAL_CONFIRM_TICKS = 10;
	/** How long after a split closed its boss being hit again reopens it, thirty seconds. */
	public static final int REOPEN_TICKS = 50;
	/** The lowest combat level the plugin treats as the boss of an unnamed row. */
	public static final int UNNAMED_BOSS_LEVEL = 150;

	private Run run;
	private int base;
	private int lastEnd;
	private Split open;
	/** The split closed most recently, which a boss coming back can reopen. */
	private Split lastClosed;
	private int lastClosedAt = Integer.MIN_VALUE;
	/** Whether the split closed most recently was a guess at an unnamed boss. */
	private boolean lastClosedGuessed;
	/** The newest tick seen, relative to the run's start. */
	private int now;
	/** Whether the open split was opened by a guess at an unnamed boss, which a named boss of the row may correct. */
	private boolean guessed;
	private int pendingTick = -1;
	private int finalTick = -1;
	/** The game announced the completion, so nothing reopens any more. */
	private boolean announced;
	private int deaths;
	private final List<String> purples = new ArrayList<>();
	private final Map<Supply, Integer> last = new EnumMap<>(Supply.class);
	private final Map<Supply, Integer> used = new EnumMap<>(Supply.class);

	/** Starts a run. Any run already going is dropped, as the client can only be in one. */
	public void begin(long now, int tick, int teamSize, Map<Supply, Integer> pack)
	{
		run = new Run(now, Math.max(1, teamSize));
		base = tick;
		this.now = 0;
		lastEnd = 0;
		open = null;
		lastClosed = null;
		lastClosedAt = Integer.MIN_VALUE;
		lastClosedGuessed = false;
		guessed = false;
		pendingTick = -1;
		finalTick = -1;
		announced = false;
		deaths = 0;
		purples.clear();
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

	/** The row the next boss will be in: one past the furthest boss already down. */
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

	/** Whether the game itself said the run was complete, which is final. */
	public boolean isAnnounced()
	{
		return announced;
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
		// Nothing unnamed waits ahead, but an unnamed boss just put down may only have changed form.
		if (canReopen() && lastClosed.getEncounter().isUnnamed())
		{
			return lastClosed.getEncounter();
		}
		return null;
	}

	private boolean canReopen()
	{
		return lastClosed != null && !announced && now - lastClosedAt <= REOPEN_TICKS
			&& lastClosed.getEncounter().getRow() == nextRow() - 1;
	}

	/** A boss was hit, or seen fighting. The first time opens its split. */
	public void engage(Encounter encounter, int tick)
	{
		if (run == null || encounter == null || announced)
		{
			return;
		}
		int t = tick - base;
		now = Math.max(now, t);
		if (open != null)
		{
			if (open.getEncounter() == encounter)
			{
				// Still fighting: a form that fell was only a phase.
				pendingTick = -1;
				return;
			}
			if (encounter.getRow() == open.getEncounter().getRow() && guessed && !encounter.isUnnamed())
			{
				// What looked like the unnamed boss was something else in the room; this is the row's boss.
				open.retarget(encounter, t);
				guessed = false;
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
		else if (lastClosed != null && lastClosedGuessed && !encounter.isUnnamed()
			&& encounter.getRow() == lastClosed.getEncounter().getRow() && lastClosed.getEncounter().getRow() == nextRow() - 1)
		{
			// What was taken for the unnamed boss fell, but it was an add: the row's named boss is only now being fought.
			lastClosed.retarget(encounter, t);
			reopenLast(false);
			return;
		}
		else if (lastClosed != null && lastClosed.getEncounter() == encounter && canReopen())
		{
			// The boss is back: what fell was a form, not the boss.
			reopenLast(lastClosedGuessed);
			return;
		}
		if (encounter.getRow() < nextRow())
		{
			return;
		}
		open = new Split(encounter, lastEnd, t);
		guessed = encounter.isUnnamed();
		run.splitsMutable().add(open);
	}

	private void reopenLast(boolean wasGuessed)
	{
		lastClosed.reopen();
		open = lastClosed;
		lastEnd = open.getStartTick();
		lastClosed = null;
		lastClosedGuessed = false;
		guessed = wasGuessed;
		finalTick = -1;
		pendingTick = -1;
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
		int t = tick - base;
		now = Math.max(now, t);
		// The death and the body leaving can both report a fall; the first is the true one.
		if (pendingTick < 0)
		{
			pendingTick = t;
		}
	}

	/** Closes a fallen boss's split once it has stayed down. */
	public void tick(int tick)
	{
		if (run == null)
		{
			return;
		}
		now = Math.max(now, tick - base);
		if (open != null && pendingTick >= 0)
		{
			int wait = open.getEncounter().getRow() == Encounter.ROWS - 1 ? FINAL_CONFIRM_TICKS : CONFIRM_TICKS;
			if (now - pendingTick >= wait)
			{
				close(pendingTick);
			}
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
		lastClosed = open;
		lastClosedAt = now;
		lastClosedGuessed = guessed;
		open = null;
		guessed = false;
		pendingTick = -1;
	}

	/**
	 * The game announced the completion count, so the run is over whatever the client saw of the
	 * final boss. A final boss that fell closes at its fall; one never recognised leaves no split,
	 * and the run's time is the moment of the announcement.
	 */
	public void complete(int tick)
	{
		if (run == null || announced)
		{
			return;
		}
		int t = tick - base;
		now = Math.max(now, t);
		if (open != null)
		{
			boolean finalRow = open.getEncounter().getRow() == Encounter.ROWS - 1;
			if (pendingTick >= 0)
			{
				close(pendingTick);
			}
			else if (finalRow)
			{
				close(t);
			}
			else
			{
				// An earlier boss still open with no fall seen: its time is unknown, so it is dropped.
				run.splitsMutable().remove(open);
				open = null;
			}
		}
		if (finalTick < 0)
		{
			finalTick = t;
		}
		announced = true;
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

	/** A unique landed during the run. The first names the run; every one is logged when the run is recorded. */
	public void purple(String unique)
	{
		if (run != null && unique != null)
		{
			if (run.getPurple() == null)
			{
				run.setPurple(unique);
			}
			purples.add(unique);
		}
	}

	/** Every unique the run in progress has given, in order. */
	public List<String> purples()
	{
		return Collections.unmodifiableList(new ArrayList<>(purples));
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
		int ticks = finalTick >= 0 ? finalTick : Math.max(0, tick - base);
		run.splitsMutable().removeIf(s -> !s.isDone() && outcome == Outcome.COMPLETED);
		run.finish(outcome, ticks, deaths, usedByTitle());
		Run done = run;
		run = null;
		open = null;
		lastClosed = null;
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
		copy.finish(finalTick >= 0 ? Outcome.COMPLETED : null, elapsed(tick), deaths, usedByTitle());
		return copy;
	}

	/**
	 * Ticks since the run began. Once the final boss has fallen the clock stops at its fall, even
	 * before the fall is confirmed, so it never runs on and then jumps back.
	 */
	public int elapsed(int tick)
	{
		if (run == null)
		{
			return 0;
		}
		if (finalTick >= 0)
		{
			return finalTick;
		}
		if (open != null && pendingTick >= 0 && open.getEncounter().getRow() == Encounter.ROWS - 1)
		{
			return pendingTick;
		}
		return Math.max(0, tick - base);
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
