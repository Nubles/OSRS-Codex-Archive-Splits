package com.codex.archive.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the run history says beyond the bests: where time is usually lost, which door is quicker
 * for you, how steady your clears are, and who you raid with.
 */
public final class Insights
{
	/** How many recent runs the typical figures come from. */
	public static final int RECENT = 20;
	/** Runs through a door before it is worth comparing with the other. */
	public static final int DOOR_RUNS = 3;

	private Insights()
	{
	}

	/** One row of the raid: its best room, a typical room lately and how much they vary. */
	public static final class RowStat
	{
		public final int row;
		public final int best;
		public final int typical;
		/** The middle half of recent rooms spans this many ticks. */
		public final int spread;
		public final int runs;

		RowStat(int row, int best, int typical, int spread, int runs)
		{
			this.row = row;
			this.best = best;
			this.typical = typical;
			this.spread = spread;
			this.runs = runs;
		}

		/** What a typical run loses here against the best. */
		public int loss()
		{
			return Math.max(0, typical - best);
		}
	}

	/** One boss behind a door: how often you took it and how quick its room is for you. */
	public static final class DoorStat
	{
		public final Encounter encounter;
		public final int runs;
		public final int typical;
		public final int best;

		DoorStat(Encounter encounter, int runs, int typical, int best)
		{
			this.encounter = encounter;
			this.runs = runs;
			this.typical = typical;
			this.best = best;
		}
	}

	/** A teammate: runs together, clears together and the best clear together. */
	public static final class Mate
	{
		public final String name;
		public final int runs;
		public final int clears;
		public final int best;

		Mate(String name, int runs, int clears, int best)
		{
			this.name = name;
			this.runs = runs;
			this.clears = clears;
			this.best = best;
		}
	}

	/** Every row with a time lately, the one losing most against its best first. */
	public static List<RowStat> rows(List<Run> runs, int bucket)
	{
		List<RowStat> out = new ArrayList<>();
		for (int row = 0; row < Encounter.ROWS; row++)
		{
			List<Integer> times = new ArrayList<>();
			for (int i = runs.size() - 1; i >= 0 && times.size() < RECENT; i--)
			{
				Run r = runs.get(i);
				Split s = r.split(row);
				if (Records.bucket(r.getTeamSize()) == bucket && s != null && s.isDone())
				{
					times.add(s.roomTicks(0));
				}
			}
			int best = Records.bestRoom(runs, bucket, row);
			if (times.isEmpty() || best < 0)
			{
				continue;
			}
			out.add(new RowStat(row, best, Stats.median(times),
				Stats.percentile(times, 0.75) - Stats.percentile(times, 0.25), times.size()));
		}
		out.sort(Comparator.comparingInt((RowStat r) -> -r.loss()).thenComparingInt(r -> r.row));
		return out;
	}

	/** Both doors of a row, for rows where each has been taken often enough to compare. */
	public static List<DoorStat[]> doors(List<Run> runs, int bucket)
	{
		List<DoorStat[]> out = new ArrayList<>();
		for (int row = 1; row <= 3; row++)
		{
			Encounter[] pair = Encounter.ofRow(row);
			DoorStat[] stats = new DoorStat[pair.length];
			boolean enough = true;
			for (int i = 0; i < pair.length; i++)
			{
				List<Integer> times = new ArrayList<>();
				int best = -1;
				for (Run r : runs)
				{
					Split s = r.split(row);
					if (Records.bucket(r.getTeamSize()) == bucket && s != null && s.isDone() && s.getEncounter() == pair[i])
					{
						int t = s.roomTicks(0);
						times.add(t);
						best = best < 0 ? t : Math.min(best, t);
					}
				}
				stats[i] = new DoorStat(pair[i], times.size(), Stats.median(times), best);
				enough &= times.size() >= DOOR_RUNS;
			}
			if (enough)
			{
				out.add(stats);
			}
		}
		return out;
	}

	/** The newest completed run times in a group, oldest first. */
	public static List<Integer> trend(List<Run> runs, int bucket)
	{
		List<Integer> out = new ArrayList<>();
		for (int i = runs.size() - 1; i >= 0 && out.size() < RECENT; i--)
		{
			Run r = runs.get(i);
			if (r.isCompleted() && Records.bucket(r.getTeamSize()) == bucket)
			{
				out.add(0, r.getTicks());
			}
		}
		return out;
	}

	/** How many times a clear beat every clear before it: each new personal best, the first clear included. */
	public static int personalBests(List<Run> runs, int bucket)
	{
		List<Run> sorted = new ArrayList<>(runs);
		sorted.sort(Comparator.comparingLong(Run::getStartedAt));
		int best = Integer.MAX_VALUE;
		int n = 0;
		for (Run r : sorted)
		{
			if (r.isCompleted() && Records.bucket(r.getTeamSize()) == bucket && r.getTicks() < best)
			{
				best = r.getTicks();
				n++;
			}
		}
		return n;
	}

	/** Teammates in a group, the most raided with first. */
	public static List<Mate> crew(List<Run> runs, int bucket)
	{
		Map<String, int[]> byName = new LinkedHashMap<>();
		for (Run r : runs)
		{
			if (Records.bucket(r.getTeamSize()) != bucket)
			{
				continue;
			}
			for (String name : r.getCrew())
			{
				int[] m = byName.computeIfAbsent(name, k -> new int[]{0, 0, -1});
				m[0]++;
				if (r.isCompleted())
				{
					m[1]++;
					m[2] = m[2] < 0 ? r.getTicks() : Math.min(m[2], r.getTicks());
				}
			}
		}
		List<Mate> out = new ArrayList<>();
		byName.forEach((name, m) -> out.add(new Mate(name, m[0], m[1], m[2])));
		out.sort(Comparator.comparingInt((Mate m) -> -m.runs).thenComparingInt(m -> m.best < 0 ? Integer.MAX_VALUE : m.best));
		return out;
	}
}
