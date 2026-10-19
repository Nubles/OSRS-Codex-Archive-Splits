package com.codex.archive.core;

import java.util.List;

/**
 * How one run stood on the day it was raided: against the runs of the same party size that came
 * before it, never the ones after, so an old run's card still says "new best" if it was one. Kept
 * for the run card, which a player shares long after the run.
 */
public final class Standing
{
	private final Run run;
	private final Run priorBest;
	private final int clearNumber;
	private final int[] priorRooms = new int[Encounter.ROWS];
	private final boolean[] golds = new boolean[Encounter.ROWS];
	private final Integer[] deltas = new Integer[Encounter.ROWS];
	private int goldCount;

	private Standing(Run run, Run priorBest, int clearNumber)
	{
		this.run = run;
		this.priorBest = priorBest;
		this.clearNumber = clearNumber;
	}

	/** The run's standing among the runs kept; the run itself may or may not be among them. */
	public static Standing of(List<Run> runs, Run run)
	{
		int bucket = Records.bucket(run.getTeamSize());
		Run best = null;
		int clears = 0;
		int[] rooms = new int[Encounter.ROWS];
		for (Run r : runs)
		{
			if (r == run || r.getStartedAt() >= run.getStartedAt() || Records.bucket(r.getTeamSize()) != bucket)
			{
				continue;
			}
			if (r.isCompleted())
			{
				clears++;
				best = best == null || r.getTicks() < best.getTicks() ? r : best;
			}
			// A best room counts from any run that timed it, a wipe's earlier rooms included.
			for (int row = 0; row < Encounter.ROWS; row++)
			{
				Split s = r.split(row);
				if (s != null && s.isDone())
				{
					int t = s.roomTicks(0);
					rooms[row] = rooms[row] == 0 ? t : Math.min(rooms[row], t);
				}
			}
		}
		Standing st = new Standing(run, best, run.isCompleted() ? clears + 1 : 0);
		for (int row = 0; row < Encounter.ROWS; row++)
		{
			st.priorRooms[row] = rooms[row];
			Split s = run.split(row);
			if (s == null || !s.isDone())
			{
				continue;
			}
			// A gold beats a room timed before; the first time a row is timed there is nothing to beat.
			st.golds[row] = rooms[row] > 0 && s.roomTicks(0) < rooms[row];
			st.goldCount += st.golds[row] ? 1 : 0;
			Split b = best == null ? null : best.split(row);
			if (b != null && b.isDone())
			{
				st.deltas[row] = s.getEndTick() - b.getEndTick();
			}
		}
		return st;
	}

	public Run getRun()
	{
		return run;
	}

	/** The best clear of the same party size before this run, or null when there was none. */
	public Run getPriorBest()
	{
		return priorBest;
	}

	/** Whether it was the first clear of its party size. */
	public boolean isFirstClear()
	{
		return run.isCompleted() && priorBest == null;
	}

	/** Whether it beat every clear of its party size before it. A first clear is one too. */
	public boolean isNewBest()
	{
		return run.isCompleted() && (priorBest == null || run.getTicks() < priorBest.getTicks());
	}

	/** Ticks against the best before it: below nought is quicker. Null for a wipe or a first clear. */
	public Integer totalDelta()
	{
		return run.isCompleted() && priorBest != null ? run.getTicks() - priorBest.getTicks() : null;
	}

	/** Which clear of its party size it was, the first being 1; 0 for a run that did not clear. */
	public int getClearNumber()
	{
		return clearNumber;
	}

	/** Whether a row's room was quicker than any before it. */
	public boolean isGold(int row)
	{
		return golds[row];
	}

	public int getGoldCount()
	{
		return goldCount;
	}

	/** Rows the run timed whose room had been timed before, so a gold was there to be had. */
	public int getComparedRooms()
	{
		int n = 0;
		for (int row = 0; row < Encounter.ROWS; row++)
		{
			Split s = run.split(row);
			n += s != null && s.isDone() && priorRooms[row] > 0 ? 1 : 0;
		}
		return n;
	}

	/** The quickest room for a row before this run, or 0 when none was timed. */
	public int priorRoom(int row)
	{
		return priorRooms[row];
	}

	/** The run's time when a row's boss fell, against the best before it at the same boss; or null. */
	public Integer delta(int row)
	{
		return deltas[row];
	}
}
