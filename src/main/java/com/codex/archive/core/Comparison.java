package com.codex.archive.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * What a run is measured against: when each boss should be down by. Your best run is the
 * classic mark; your best rooms strung together is the run of a perfect day; your typical run
 * (the median of each room over recent clears) says whether today is a good day or a bad one.
 */
public final class Comparison
{
	/** Completed runs the typical run is taken from. */
	public static final int TYPICAL_RUNS = 10;

	public enum Mode
	{
		PERSONAL_BEST("Personal best", "PB", "your best"),
		BEST_ROOMS("Best rooms", "Golds", "your best rooms"),
		TYPICAL("Typical run", "Typical", "your typical run");

		private final String label;
		private final String shortLabel;
		private final String noun;

		Mode(String label, String shortLabel, String noun)
		{
			this.label = label;
			this.shortLabel = shortLabel;
			this.noun = noun;
		}

		/** A few letters for the crest: "PB". */
		public String getShortLabel()
		{
			return shortLabel;
		}

		/** For the chat box: "4.2s ahead of your best rooms". */
		public String getNoun()
		{
			return noun;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	private final Mode mode;
	/** For each row, when its boss should be down, in ticks from the start, or -1. */
	private final int[] ends;
	/** For each row, the room time aimed at, or -1. */
	private final int[] rooms;

	private Comparison(Mode mode, int[] ends, int[] rooms)
	{
		this.mode = mode;
		this.ends = ends;
		this.rooms = rooms;
	}

	/**
	 * @param runs    the history
	 * @param bucket  the party-size group
	 * @param exclude a run never compared with itself, such as the one being shown; or null
	 */
	public static Comparison of(List<Run> runs, int bucket, Mode mode, Run exclude)
	{
		int[] ends = new int[Encounter.ROWS];
		int[] rooms = new int[Encounter.ROWS];
		Arrays.fill(ends, -1);
		Arrays.fill(rooms, -1);
		List<Run> pool = new ArrayList<>();
		for (Run r : runs)
		{
			if (r != exclude && (exclude == null || r.getStartedAt() != exclude.getStartedAt())
				&& Records.bucket(r.getTeamSize()) == bucket)
			{
				pool.add(r);
			}
		}
		if (mode == Mode.PERSONAL_BEST)
		{
			Run best = Records.best(pool, bucket);
			if (best != null)
			{
				for (int row = 0; row < Encounter.ROWS; row++)
				{
					Split s = best.split(row);
					if (s != null && s.isDone())
					{
						ends[row] = s.getEndTick();
						rooms[row] = s.roomTicks(0);
					}
				}
			}
			return new Comparison(mode, ends, rooms);
		}
		for (int row = 0; row < Encounter.ROWS; row++)
		{
			rooms[row] = mode == Mode.BEST_ROOMS ? Records.bestRoom(pool, bucket, row) : typicalRoom(pool, row);
		}
		int sum = 0;
		for (int row = 0; row < Encounter.ROWS; row++)
		{
			if (rooms[row] < 0)
			{
				// A row with no time breaks the chain: every boss after it has no mark either.
				break;
			}
			sum += rooms[row];
			ends[row] = sum;
		}
		return new Comparison(mode, ends, rooms);
	}

	/** The median room over the newest completed runs that timed this row, or -1. */
	private static int typicalRoom(List<Run> pool, int row)
	{
		List<Integer> times = new ArrayList<>();
		for (int i = pool.size() - 1; i >= 0 && times.size() < TYPICAL_RUNS; i--)
		{
			Run r = pool.get(i);
			Split s = r.split(row);
			if (r.isCompleted() && s != null && s.isDone())
			{
				times.add(s.roomTicks(0));
			}
		}
		return Stats.median(times);
	}

	public Mode getMode()
	{
		return mode;
	}

	/** When this row's boss should be down, or -1. */
	public int end(int row)
	{
		return row >= 0 && row < ends.length ? ends[row] : -1;
	}

	/** The room time aimed at for this row, or -1. */
	public int room(int row)
	{
		return row >= 0 && row < rooms.length ? rooms[row] : -1;
	}

	/** The whole run's mark: the last row's end, or -1. */
	public int total()
	{
		return ends[Encounter.ROWS - 1];
	}

	/**
	 * How far behind the mark a run was when this row's boss fell.
	 *
	 * @return ticks behind (negative when ahead), or null when either side has no time for the row
	 */
	public Integer delta(Run run, int row)
	{
		if (run == null)
		{
			return null;
		}
		Split s = run.split(row);
		int mark = end(row);
		if (s == null || !s.isDone() || mark < 0)
		{
			return null;
		}
		return s.getEndTick() - mark;
	}

	/**
	 * The best a run can still finish in: what it has taken so far, the room being fought at its
	 * best or its time so far if that is already longer, and every room after it at its best.
	 *
	 * @param bestRooms each row's best room in this party size, or -1
	 * @param now       ticks since the run began
	 * @return ticks, or -1 while any room still to come has no best
	 */
	public static int bestPossible(Run run, int[] bestRooms, int now)
	{
		if (run == null)
		{
			return -1;
		}
		int row = run.reached();
		if (row >= Encounter.ROWS || run.isCompleted())
		{
			return run.getTicks();
		}
		int base = 0;
		for (Split s : run.getSplits())
		{
			if (s.isDone())
			{
				base = Math.max(base, s.getEndTick());
			}
		}
		int total = base;
		for (int r = row; r < Encounter.ROWS; r++)
		{
			int best = bestRooms != null && r < bestRooms.length ? bestRooms[r] : -1;
			if (best < 0)
			{
				return -1;
			}
			total += r == row ? Math.max(best, now - base) : best;
		}
		return total;
	}
}
