package com.codex.archive.core;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Personal bests and averages worked out from the run history. Times are only compared between
 * runs of the same party size, since a trio and a five clear the archive at different speeds.
 */
public final class Records
{
	public static final String[] BUCKETS = {"Solo", "Duo", "Trio", "4", "5", "6+"};

	private Records()
	{
	}

	/** The party-size group a run belongs to. */
	public static int bucket(int teamSize)
	{
		return Math.max(0, Math.min(BUCKETS.length - 1, teamSize - 1));
	}

	/** The fastest completed run in a party-size group, or null. */
	public static Run best(List<Run> runs, int bucket)
	{
		Run best = null;
		for (Run r : runs)
		{
			if (r.isCompleted() && bucket(r.getTeamSize()) == bucket && (best == null || r.getTicks() < best.getTicks()))
			{
				best = r;
			}
		}
		return best;
	}

	/** The fastest a row has been cleared in a party-size group, counting the walk in, or -1. */
	public static int bestRoom(List<Run> runs, int bucket, int row)
	{
		int best = -1;
		for (Run r : runs)
		{
			if (bucket(r.getTeamSize()) != bucket)
			{
				continue;
			}
			Split s = r.split(row);
			if (s != null && s.isDone())
			{
				int t = s.roomTicks(0);
				if (best < 0 || t < best)
				{
					best = t;
				}
			}
		}
		return best;
	}

	/** The fastest fight against one boss in a party-size group, from first hit to its fall, or -1. */
	public static int bestFight(List<Run> runs, int bucket, Encounter encounter)
	{
		int best = -1;
		for (Run r : runs)
		{
			if (bucket(r.getTeamSize()) != bucket)
			{
				continue;
			}
			Split s = r.split(encounter.getRow());
			if (s != null && s.isDone() && s.getEncounter() == encounter)
			{
				int t = s.fightTicks(0);
				if (best < 0 || t < best)
				{
					best = t;
				}
			}
		}
		return best;
	}

	/** Every row's best room added up: the run that is possible on a perfect day. -1 until every row has a time. */
	public static int sumOfBest(List<Run> runs, int bucket)
	{
		int sum = 0;
		for (int row = 0; row < Encounter.ROWS; row++)
		{
			int t = bestRoom(runs, bucket, row);
			if (t < 0)
			{
				return -1;
			}
			sum += t;
		}
		return sum;
	}

	/** The mean of the last few completed runs in a group, or -1. */
	public static int recentAverage(List<Run> runs, int bucket, int count)
	{
		long sum = 0;
		int n = 0;
		for (int i = runs.size() - 1; i >= 0 && n < count; i--)
		{
			Run r = runs.get(i);
			if (r.isCompleted() && bucket(r.getTeamSize()) == bucket)
			{
				sum += r.getTicks();
				n++;
			}
		}
		return n == 0 ? -1 : (int) (sum / n);
	}

	/** Every run that holds a record: a best time, a best room or a best fight, in any party size. */
	public static Set<Run> holders(List<Run> runs)
	{
		Set<Run> out = Collections.newSetFromMap(new IdentityHashMap<>());
		for (int bucket = 0; bucket < BUCKETS.length; bucket++)
		{
			Run best = best(runs, bucket);
			if (best != null)
			{
				out.add(best);
			}
			for (int row = 0; row < Encounter.ROWS; row++)
			{
				Run holder = null;
				for (Run r : runs)
				{
					Split s = r.split(row);
					if (bucket(r.getTeamSize()) == bucket && s != null && s.isDone()
						&& (holder == null || s.roomTicks(0) < holder.split(row).roomTicks(0)))
					{
						holder = r;
					}
				}
				if (holder != null)
				{
					out.add(holder);
				}
			}
			for (Encounter e : Encounter.values())
			{
				Run holder = null;
				for (Run r : runs)
				{
					Split s = r.split(e.getRow());
					if (bucket(r.getTeamSize()) == bucket && s != null && s.isDone() && s.getEncounter() == e
						&& (holder == null || s.fightTicks(0) < holder.split(e.getRow()).fightTicks(0)))
					{
						holder = r;
					}
				}
				if (holder != null)
				{
					out.add(holder);
				}
			}
		}
		return out;
	}

	public static int count(List<Run> runs, Outcome outcome)
	{
		int n = 0;
		for (Run r : runs)
		{
			if (r.getOutcome() == outcome)
			{
				n++;
			}
		}
		return n;
	}

	/** The party-size group with the most completions, so the panel opens on the one played most. */
	public static int favouriteBucket(List<Run> runs)
	{
		int[] counts = new int[BUCKETS.length];
		for (Run r : runs)
		{
			if (r.isCompleted())
			{
				counts[bucket(r.getTeamSize())]++;
			}
		}
		int best = 2;
		int most = 0;
		for (int i = 0; i < counts.length; i++)
		{
			if (counts[i] > most)
			{
				most = counts[i];
				best = i;
			}
		}
		return best;
	}

	/**
	 * How far ahead or behind a run is against a best run at the boss just fallen.
	 *
	 * @return ticks behind (negative when ahead), or null when either has no time for that row
	 */
	public static Integer delta(Run run, Run best, int row)
	{
		if (run == null || best == null)
		{
			return null;
		}
		Split a = run.split(row);
		Split b = best.split(row);
		if (a == null || b == null || !a.isDone() || !b.isDone())
		{
			return null;
		}
		return a.getEndTick() - b.getEndTick();
	}
}
