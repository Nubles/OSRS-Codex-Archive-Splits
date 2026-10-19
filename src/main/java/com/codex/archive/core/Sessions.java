package com.codex.archive.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The run history in sittings: runs with less than a long break between them belong together.
 * Each sitting says how many clears and wipes it held, its best and average clear, the pace in
 * clears an hour, how long was spent between runs, how many personal bests it set and its purples.
 */
public final class Sessions
{
	/** A break longer than this between one run's end and the next one's start ends a sitting. */
	public static final long GAP_MS = 45 * 60_000L;
	private static final long TICK_MS = 600;

	/** One sitting. */
	public static final class Session
	{
		private final List<Run> runs;
		private final List<Run> counted;
		private final long start;
		private final long end;
		private final int clears;
		private final int wipes;
		private final int best;
		private final int average;
		private final int bests;
		private final int purples;
		private final long between;

		/**
		 * @param bucket the party-size group whose runs the figures count, or -1 for every run; the
		 *     sitting's start, end and time between runs always take every run
		 */
		Session(List<Run> runs, int bests, int bucket)
		{
			this.runs = Collections.unmodifiableList(runs);
			List<Run> mine = new ArrayList<>();
			this.start = runs.get(0).getStartedAt();
			this.end = endOf(runs.get(runs.size() - 1));
			int c = 0;
			int w = 0;
			int b = -1;
			long sum = 0;
			int p = 0;
			long gaps = 0;
			for (int i = 0; i < runs.size(); i++)
			{
				Run r = runs.get(i);
				if (i > 0)
				{
					gaps += Math.max(0, r.getStartedAt() - endOf(runs.get(i - 1)));
				}
				if (bucket >= 0 && Records.bucket(r.getTeamSize()) != bucket)
				{
					continue;
				}
				mine.add(r);
				if (r.isCompleted())
				{
					c++;
					sum += r.getTicks();
					b = b < 0 ? r.getTicks() : Math.min(b, r.getTicks());
				}
				else if (r.getOutcome() == Outcome.WIPED)
				{
					w++;
				}
				p += r.getPurple() != null ? 1 : 0;
			}
			this.counted = Collections.unmodifiableList(mine);
			this.clears = c;
			this.wipes = w;
			this.best = b;
			this.average = c == 0 ? -1 : (int) (sum / c);
			this.bests = bests;
			this.purples = p;
			this.between = runs.size() < 2 ? -1 : gaps / (runs.size() - 1);
		}

		public List<Run> getRuns()
		{
			return runs;
		}

		/** The runs the figures count: those of the party size asked for, or every run. */
		public List<Run> getCounted()
		{
			return counted;
		}

		public long getStart()
		{
			return start;
		}

		/** When the last run of the sitting ended. */
		public long getEnd()
		{
			return end;
		}

		public int getClears()
		{
			return clears;
		}

		public int getWipes()
		{
			return wipes;
		}

		/** The best clear in ticks, or -1 with none. */
		public int getBest()
		{
			return best;
		}

		/** The average clear in ticks, or -1 with none. */
		public int getAverage()
		{
			return average;
		}

		/** Clears that were a new personal best for their party size when they were made. */
		public int getBests()
		{
			return bests;
		}

		public int getPurples()
		{
			return purples;
		}

		/** The average time from one run's end to the next one's start, or -1 with one run. */
		public long getBetween()
		{
			return between;
		}

		/** Clears an hour, from the first run's start to the last one's end; 0 with under 15 minutes to go on. */
		public double perHour()
		{
			long span = end - start;
			return span < 15 * 60_000L ? 0 : clears * 3_600_000.0 / span;
		}
	}

	private Sessions()
	{
	}

	static long endOf(Run r)
	{
		return r.getStartedAt() + r.getTicks() * TICK_MS;
	}

	/** Every sitting, oldest first, counting every run. */
	public static List<Session> of(List<Run> runs)
	{
		return of(runs, -1);
	}

	/**
	 * Every sitting that held a run of a party-size group, oldest first, its figures counting only
	 * that group's runs. Sittings are still split by the breaks between all runs.
	 *
	 * @param bucket the party-size group, or -1 for every run
	 */
	public static List<Session> of(List<Run> runs, int bucket)
	{
		List<Run> sorted = new ArrayList<>(runs);
		sorted.sort((a, b) -> Long.compare(a.getStartedAt(), b.getStartedAt()));
		int[] bestSoFar = new int[Records.BUCKETS.length];
		java.util.Arrays.fill(bestSoFar, Integer.MAX_VALUE);
		List<Session> out = new ArrayList<>();
		List<Run> current = new ArrayList<>();
		int bests = 0;
		for (Run r : sorted)
		{
			if (!current.isEmpty() && r.getStartedAt() - endOf(current.get(current.size() - 1)) > GAP_MS)
			{
				add(out, current, bests, bucket);
				current = new ArrayList<>();
				bests = 0;
			}
			current.add(r);
			int b = Records.bucket(r.getTeamSize());
			if (r.isCompleted() && r.getTicks() < bestSoFar[b])
			{
				// The first clear of a party size is its first best, and counts; on a party tab, only that size's.
				bestSoFar[b] = r.getTicks();
				bests += bucket < 0 || b == bucket ? 1 : 0;
			}
		}
		if (!current.isEmpty())
		{
			add(out, current, bests, bucket);
		}
		return out;
	}

	/** Keeps a sitting when it held a run of the party size asked for. */
	private static void add(List<Session> out, List<Run> runs, int bests, int bucket)
	{
		Session s = new Session(runs, bests, bucket);
		if (!s.getCounted().isEmpty())
		{
			out.add(s);
		}
	}

	/** The sitting the newest run belongs to, if it ended within the gap of {@code now}; else null. */
	public static Session current(List<Run> runs, long now)
	{
		List<Session> all = of(runs);
		if (all.isEmpty())
		{
			return null;
		}
		Session last = all.get(all.size() - 1);
		return now - last.getEnd() <= GAP_MS ? last : null;
	}
}
