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
		return s;
	}
}
