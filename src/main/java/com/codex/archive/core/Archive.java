package com.codex.archive.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Everything kept for one account: the run history, the purples and the completion count. Saved
 * as one blob per RuneScape profile.
 */
public class Archive
{
	/** Runs kept in the history; the oldest go first. */
	public static final int MAX_RUNS = 1000;
	/** How long after a run its loot still counts as that run's, ten minutes. */
	private static final long LOOT_WINDOW_MS = 10 * 60_000L;

	private List<Run> runs = new ArrayList<>();
	private List<Drop> drops = new ArrayList<>();
	/** The last completion count the game announced. */
	private int kc;
	/** Completions since the last purple. */
	private int dry;

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

	/** The newest run, or null. */
	public Run last()
	{
		return runs.isEmpty() ? null : runs.get(runs.size() - 1);
	}

	public void record(Run run)
	{
		if (run == null)
		{
			return;
		}
		runs.add(run);
		while (runs.size() > MAX_RUNS)
		{
			runs.remove(0);
		}
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
		return this;
	}
}
