package com.codex.archive.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the run history says beyond the bests: where time is usually lost, which door is quicker
 * for you, which whole route is, how steady your clears are and how your best came down, where
 * runs wipe, and who you raid with.
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

	/** One way through the three rows of doors, over the clears that took it. */
	public static final class RouteStat
	{
		/** The boss behind each of the three doors, in row order. */
		public final List<Encounter> doors;
		public final int clears;
		public final int best;
		/** The median clear on this route. */
		public final int typical;

		RouteStat(List<Encounter> doors, int clears, int best, int typical)
		{
			this.doors = doors;
			this.clears = clears;
			this.best = best;
			this.typical = typical;
		}

		/** "Bork, Dreadnoth, Trio". */
		public String name()
		{
			StringBuilder sb = new StringBuilder();
			for (Encounter e : doors)
			{
				sb.append(sb.length() > 0 ? ", " : "").append(e.getShortName());
			}
			return sb.toString();
		}
	}

	/** A new personal best: when, the time, and how much it took off the one before. */
	public static final class Best
	{
		public final long at;
		public final int ticks;
		/** Ticks taken off the previous best, or -1 for the first clear. */
		public final int saved;

		Best(long at, int ticks, int saved)
		{
			this.at = at;
			this.ticks = ticks;
			this.saved = saved;
		}
	}

	/** A boss runs have wiped at: how often, out of how many times it was reached. */
	public static final class Wall
	{
		public final Encounter encounter;
		public final int wipes;
		public final int tries;

		Wall(Encounter encounter, int wipes, int tries)
		{
			this.encounter = encounter;
			this.wipes = wipes;
			this.tries = tries;
		}
	}

	/**
	 * Every route cleared in a group, the fastest best first. Only clears with all three doors
	 * timed count, since a route with a gap cannot be told from another.
	 */
	public static List<RouteStat> routes(List<Run> runs, int bucket)
	{
		Map<List<Encounter>, List<Integer>> byRoute = new LinkedHashMap<>();
		for (Run r : runs)
		{
			if (!r.isCompleted() || Records.bucket(r.getTeamSize()) != bucket)
			{
				continue;
			}
			List<Encounter> doors = doors(r);
			if (doors != null)
			{
				byRoute.computeIfAbsent(doors, k -> new ArrayList<>()).add(r.getTicks());
			}
		}
		List<RouteStat> out = new ArrayList<>();
		byRoute.forEach((doors, times) ->
		{
			int best = times.stream().mapToInt(Integer::intValue).min().orElse(-1);
			out.add(new RouteStat(java.util.Collections.unmodifiableList(doors), times.size(), best, Stats.median(times)));
		});
		out.sort(Comparator.comparingInt((RouteStat r) -> r.best).thenComparingInt(r -> -r.clears));
		return out;
	}

	/** The bosses behind a run's three doors, or null when one of them was not timed. */
	public static List<Encounter> doors(Run run)
	{
		List<Encounter> doors = new ArrayList<>();
		for (int row = 1; row <= 3; row++)
		{
			Split s = run.split(row);
			if (s == null)
			{
				return null;
			}
			doors.add(s.getEncounter());
		}
		return doors;
	}

	/**
	 * The best clear on the same route as a run, other than the run itself, and how many there
	 * are: {best ticks, clears}, best -1 when the route was never cleared before.
	 */
	public static int[] routeBest(List<Run> runs, int bucket, Run run)
	{
		List<Encounter> mine = doors(run);
		int best = -1;
		int clears = 0;
		if (mine == null)
		{
			return new int[]{-1, 0};
		}
		for (Run r : runs)
		{
			if (r != run && r.isCompleted() && Records.bucket(r.getTeamSize()) == bucket && mine.equals(doors(r)))
			{
				clears++;
				best = best < 0 ? r.getTicks() : Math.min(best, r.getTicks());
			}
		}
		return new int[]{best, clears};
	}

	/** Each new personal best in a group, oldest first: the first clear, then every clear that beat all before it. */
	public static List<Best> ladder(List<Run> runs, int bucket)
	{
		List<Run> sorted = new ArrayList<>(runs);
		sorted.sort(Comparator.comparingLong(Run::getStartedAt));
		List<Best> out = new ArrayList<>();
		int best = Integer.MAX_VALUE;
		for (Run r : sorted)
		{
			if (r.isCompleted() && Records.bucket(r.getTeamSize()) == bucket && r.getTicks() > 0 && r.getTicks() < best)
			{
				out.add(new Best(r.getStartedAt(), r.getTicks(), best == Integer.MAX_VALUE ? -1 : best - r.getTicks()));
				best = r.getTicks();
			}
		}
		return out;
	}

	/**
	 * The bosses runs wipe at in a group, the most wipes first. A wipe is put on the boss of the
	 * row the run was fighting; a run that wiped before any boss of that row was seen is put on
	 * the row's only boss where there is one, and left out where there were two doors.
	 */
	public static List<Wall> walls(List<Run> runs, int bucket)
	{
		Map<Encounter, int[]> by = new LinkedHashMap<>();
		for (Run r : runs)
		{
			if (Records.bucket(r.getTeamSize()) != bucket)
			{
				continue;
			}
			// Every boss the run reached was a try at it.
			for (Split s : r.getSplits())
			{
				by.computeIfAbsent(s.getEncounter(), k -> new int[2])[1]++;
			}
			if (r.getOutcome() != Outcome.WIPED)
			{
				continue;
			}
			int row = Math.min(r.reached(), Encounter.ROWS - 1);
			Split at = r.split(row);
			Encounter e = at != null ? at.getEncounter() : Encounter.ofRow(row).length == 1 ? Encounter.ofRow(row)[0] : null;
			if (e == null)
			{
				continue;
			}
			int[] c = by.computeIfAbsent(e, k -> new int[2]);
			c[0]++;
			if (at == null)
			{
				c[1]++;
			}
		}
		List<Wall> out = new ArrayList<>();
		by.forEach((e, c) ->
		{
			if (c[0] > 0)
			{
				out.add(new Wall(e, c[0], c[1]));
			}
		});
		out.sort(Comparator.comparingInt((Wall w) -> -w.wipes).thenComparingInt(w -> w.encounter.getRow()));
		return out;
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
		return ladder(runs, bucket).size();
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
