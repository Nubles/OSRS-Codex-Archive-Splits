package com.codex.archive.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Everything kept for one account: the run history, the purples and the completion count. Saved
 * as one blob per RuneScape profile.
 */
public class Archive
{
	/**
	 * Runs kept in the history. The oldest go first, except any run holding a record, so a
	 * personal best is never lost to age. Clears and wipes are counted apart, so they never drop.
	 */
	public static final int MAX_RUNS = 500;
	/** How long after a run its loot still counts as that run's, ten minutes. */
	private static final long LOOT_WINDOW_MS = 10 * 60_000L;

	private List<Run> runs = new ArrayList<>();
	private List<Drop> drops = new ArrayList<>();
	/** The last completion count the game announced. */
	private int kc;
	/** Completions since the last purple. */
	private int dry;
	/** Clears and wipes by party-size group, for the whole history, kept runs or not. */
	private int[] clears;
	private int[] wipes;

	public List<Run> getRuns()
	{
		return Collections.unmodifiableList(runs);
	}

	public List<Drop> getDrops()
	{
		return Collections.unmodifiableList(drops);
	}

	public int getKc()
	{
		return kc;
	}

	public int getDry()
	{
		return dry;
	}

	/** Clears in a party-size group, over the whole history. */
	public int clears(int bucket)
	{
		return bucket >= 0 && clears != null && bucket < clears.length ? clears[bucket] : 0;
	}

	/** Wipes in a party-size group, over the whole history. */
	public int wipes(int bucket)
	{
		return bucket >= 0 && wipes != null && bucket < wipes.length ? wipes[bucket] : 0;
	}

	/** The longest it has taken to see a purple, the current streak included. */
	public int longestDry()
	{
		int longest = dry;
		for (Drop d : drops)
		{
			longest = Math.max(longest, d.getDry());
		}
		return longest;
	}

	/** The newest run, or null. */
	public Run last()
	{
		return runs.isEmpty() ? null : runs.get(runs.size() - 1);
	}

	public void record(Run run)
	{
		record(run, null, 0);
	}

	/**
	 * Adds a finished run to the history.
	 *
	 * @param purples the uniques that landed during the run, logged once the run has counted towards the dry streak
	 * @param now when the run was recorded, for the purple log
	 */
	public void record(Run run, List<String> purples, long now)
	{
		if (run == null)
		{
			return;
		}
		ensureCounts();
		runs.add(run);
		count(run, 1);
		trim();
		if (run.isCompleted())
		{
			dry++;
			if (run.getKc() > kc)
			{
				kc = run.getKc();
			}
			else if (run.getKc() == 0 && kc > 0)
			{
				kc++;
			}
		}
		if (purples != null)
		{
			for (String unique : purples)
			{
				if (unique != null)
				{
					drops.add(new Drop(unique, now, kc, dry));
					dry = 0;
				}
			}
		}
	}

	/** The game said the completion count; it is the truth, whatever was counted before. */
	public void kc(int count, long now)
	{
		Run last = last();
		if (last != null && last.isCompleted() && last.getKc() == 0 && now - last.getStartedAt() < LOOT_WINDOW_MS * 6)
		{
			last.setKc(count);
		}
		kc = count;
	}

	/** A purple came; it belongs to the newest run when that ended moments ago. */
	public void purple(String unique, long now)
	{
		Run last = last();
		if (last != null && last.getPurple() == null && now - last.getStartedAt() < LOOT_WINDOW_MS * 6)
		{
			last.setPurple(unique);
		}
		drops.add(new Drop(unique, now, kc, dry));
		dry = 0;
	}

	/**
	 * Takes a run out of the history, for one the plugin timed wrongly. A completion since the last
	 * purple also comes off the dry streak.
	 *
	 * @return whether a run started at that moment was found
	 */
	public boolean removeRun(long startedAt)
	{
		ensureCounts();
		for (int i = runs.size() - 1; i >= 0; i--)
		{
			Run r = runs.get(i);
			if (r.getStartedAt() == startedAt)
			{
				runs.remove(i);
				count(r, -1);
				long lastDrop = drops.isEmpty() ? Long.MIN_VALUE : drops.get(drops.size() - 1).getAt();
				if (r.isCompleted() && r.getStartedAt() > lastDrop && dry > 0)
				{
					dry--;
				}
				return true;
			}
		}
		return false;
	}

	/**
	 * Takes a purple out of the log, for one the plugin mistook. The clears it took go back onto
	 * the streak it ended: the next purple's, or the current dry streak when it was the newest.
	 *
	 * @return whether the purple was found
	 */
	public boolean removeDrop(long at, String unique)
	{
		for (int i = drops.size() - 1; i >= 0; i--)
		{
			Drop d = drops.get(i);
			if (d.getAt() == at && d.getUnique().equals(unique))
			{
				drops.remove(i);
				if (i < drops.size())
				{
					drops.get(i).addDry(d.getDry());
				}
				else
				{
					dry += d.getDry();
				}
				for (Run r : runs)
				{
					if (unique.equals(r.getPurple()) && Math.abs(r.getStartedAt() - at) < LOOT_WINDOW_MS * 6)
					{
						r.setPurple(null);
					}
				}
				return true;
			}
		}
		return false;
	}

	private void count(Run run, int by)
	{
		int b = Records.bucket(run.getTeamSize());
		if (run.isCompleted())
		{
			clears[b] = Math.max(0, clears[b] + by);
		}
		else if (run.getOutcome() == Outcome.WIPED)
		{
			wipes[b] = Math.max(0, wipes[b] + by);
		}
	}

	/** Counts clears and wipes from the runs kept, for a save from before they were counted apart. */
	private void recount()
	{
		clears = new int[Records.BUCKETS.length];
		wipes = new int[Records.BUCKETS.length];
		for (Run r : runs)
		{
			count(r, 1);
		}
	}

	private void ensureCounts()
	{
		if (clears == null || wipes == null || clears.length != Records.BUCKETS.length || wipes.length != Records.BUCKETS.length)
		{
			recount();
		}
	}

	/** Lets the oldest runs go past the limit, keeping every run that holds a record. */
	private void trim()
	{
		if (runs.size() <= MAX_RUNS)
		{
			return;
		}
		Set<Run> keep = Records.holders(runs);
		for (int i = 0; i < runs.size() && runs.size() > MAX_RUNS; )
		{
			if (keep.contains(runs.get(i)))
			{
				i++;
			}
			else
			{
				runs.remove(i);
			}
		}
	}

	public Archive repair()
	{
		if (runs == null)
		{
			runs = new ArrayList<>();
		}
		runs.removeIf(r -> r == null);
		runs.forEach(Run::repair);
		if (drops == null)
		{
			drops = new ArrayList<>();
		}
		drops.removeIf(d -> d == null || d.getUnique() == null);
		kc = Math.max(0, kc);
		dry = Math.max(0, dry);
		ensureCounts();
		trim();
		return this;
	}
}
