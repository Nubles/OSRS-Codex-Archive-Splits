package com.codex.archive.core;

/**
 * One encounter of a run. Times are game ticks from the moment the party entered the archive, so
 * the room time (since the last boss fell, walking included) and the fight itself both fall out.
 */
public class Split
{
	private Encounter encounter;
	/** When the previous boss fell, or the run began for the first encounter. */
	private int startTick;
	/** The first hit on this boss. */
	private int engageTick;
	/** When this boss fell; -1 while the fight goes on. */
	private int endTick = -1;
	/**
	 * For a boss told by its row and size rather than its name, the NPC it was told from and its
	 * combat level, so the player can see what the guess rested on; null when the name matched.
	 */
	private String seen;
	private Integer lvl;

	public Split()
	{
	}

	public Split(Encounter encounter, int startTick, int engageTick)
	{
		this.encounter = encounter;
		this.startTick = startTick;
		this.engageTick = engageTick;
	}

	public Encounter getEncounter()
	{
		return encounter;
	}

	public int getStartTick()
	{
		return startTick;
	}

	public int getEngageTick()
	{
		return engageTick;
	}

	public int getEndTick()
	{
		return endTick;
	}

	public boolean isDone()
	{
		return endTick >= 0;
	}

	void end(int tick)
	{
		endTick = tick;
	}

	void reopen()
	{
		endTick = -1;
	}

	/** A guessed boss turned out to be the row's other, named boss: the split is theirs, from their first hit. */
	void retarget(Encounter encounter, int engageTick)
	{
		this.encounter = encounter;
		this.engageTick = engageTick;
		// The name matched in the end, so nothing was guessed.
		this.seen = null;
		this.lvl = null;
	}

	/** Notes the NPC a guessed boss was told from. */
	void guessedFrom(String npcName, int combatLevel)
	{
		if (npcName != null && !npcName.isEmpty())
		{
			seen = npcName.length() > 40 ? npcName.substring(0, 40) : npcName;
			lvl = combatLevel > 0 ? combatLevel : null;
		}
	}

	/** The NPC this boss was guessed from, or null when it was known by name. */
	public String getSeen()
	{
		return seen;
	}

	/** That NPC's combat level, or 0 when not known. */
	public int getSeenLevel()
	{
		return lvl == null ? 0 : lvl;
	}

	/** Forgets what a guess rested on, to keep an old run's save small. */
	void forgetSeen()
	{
		seen = null;
		lvl = null;
	}

	/** The whole room, from the last boss falling to this one falling. */
	public int roomTicks(int now)
	{
		return (isDone() ? endTick : now) - startTick;
	}

	/** The fight alone, from the first hit. */
	public int fightTicks(int now)
	{
		return (isDone() ? endTick : now) - engageTick;
	}

	Split copy()
	{
		Split s = new Split(encounter, startTick, engageTick);
		s.endTick = endTick;
		s.seen = seen;
		s.lvl = lvl;
		return s;
	}
}
