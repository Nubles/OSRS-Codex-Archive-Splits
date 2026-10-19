package com.codex.archive.core;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
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
	/** The latest runs that keep what each boss was told from; older runs keep only their times. */
	public static final int DOUBT_RUNS = 30;
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
	/** The goal time for each party-size group, in ticks; 0 where none is set. */
	private int[] goals;

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

	/** The goal time set for a party-size group, in ticks, or 0 when there is none. */
	public int goal(int bucket)
	{
		return goals != null && bucket >= 0 && bucket < goals.length ? Math.max(0, goals[bucket]) : 0;
	}

	/** Sets the goal time for a party-size group, or clears it with 0. */
	public void setGoal(int bucket, int ticks)
	{
		if (bucket < 0 || bucket >= Records.BUCKETS.length)
		{
			return;
		}
		if (goals == null || goals.length != Records.BUCKETS.length)
		{
			int[] grown = new int[Records.BUCKETS.length];
			if (goals != null)
			{
				System.arraycopy(goals, 0, grown, 0, Math.min(goals.length, grown.length));
			}
			goals = grown;
		}
		goals[bucket] = Math.max(0, ticks);
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

	/** Notes a role on a run, or clears it with null. */
	public boolean setRole(long startedAt, String role)
	{
		Run r = find(startedAt);
		if (r != null)
		{
			r.setRole(role);
		}
		return r != null;
	}

	/** Writes the player's own note on a run, or clears it with null or blank. */
	public boolean setNote(long startedAt, String note)
	{
		Run r = find(startedAt);
		if (r != null)
		{
			r.setNote(note);
		}
		return r != null;
	}

	private Run find(long startedAt)
	{
		for (int i = runs.size() - 1; i >= 0; i--)
		{
			if (runs.get(i).getStartedAt() == startedAt)
			{
				return runs.get(i);
			}
		}
		return null;
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
		// What each guess rested on matters while a run is fresh; older runs keep only their times.
		for (int i = 0; i < runs.size() - DOUBT_RUNS; i++)
		{
			runs.get(i).forgetDoubts();
		}
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

	/** A saved archive as read back: what could be kept, and whether anything had to be left out. */
	public static final class Loaded
	{
		/** The archive, or null when nothing in the save could be read at all. */
		public final Archive archive;
		/** True when the save was not read whole: a run, a purple or a figure in it was damaged. */
		public final boolean damaged;

		Loaded(Archive archive, boolean damaged)
		{
			this.archive = archive;
			this.damaged = damaged;
		}
	}

	/**
	 * Reads a saved archive. A save that reads whole is repaired and kept; one that does not (a
	 * run of the wrong shape, a figure too big for its field, a save cut short) keeps every run,
	 * purple and figure that still reads, so one damaged run never costs the rest.
	 */
	public static Loaded read(Gson gson, String saved)
	{
		if (saved == null || saved.trim().isEmpty())
		{
			return new Loaded(new Archive(), false);
		}
		try
		{
			Archive whole = gson.fromJson(saved, Archive.class);
			if (whole != null)
			{
				// It read, but a field, a boss or a purple a later version keeps would be dropped by the next save.
				whole.repair();
				return new Loaded(whole, Unread.any(gson, saved, whole));
			}
		}
		catch (RuntimeException e)
		{
			// Read it piece by piece below.
		}
		return new Loaded(salvage(gson, saved), true);
	}

	/** Every piece of a damaged save that still reads, or null when none does. */
	private static Archive salvage(Gson gson, String saved)
	{
		Archive a = new Archive();
		boolean any = false;
		JsonReader in = new JsonReader(new StringReader(saved));
		in.setLenient(true);
		try
		{
			if (in.peek() != JsonToken.BEGIN_OBJECT)
			{
				return null;
			}
			in.beginObject();
			while (in.hasNext())
			{
				String name = in.nextName();
				boolean list = "runs".equals(name) || "drops".equals(name);
				if (list && in.peek() == JsonToken.BEGIN_ARRAY)
				{
					in.beginArray();
					while (in.hasNext())
					{
						JsonElement item = gson.fromJson(in, JsonElement.class);
						if ("runs".equals(name))
						{
							Run r = piece(gson, item, Run.class);
							any |= r != null && a.runs.add(r);
						}
						else
						{
							Drop d = piece(gson, item, Drop.class);
							any |= d != null && a.drops.add(d);
						}
					}
					in.endArray();
					continue;
				}
				JsonElement value = gson.fromJson(in, JsonElement.class);
				any |= a.figure(gson, name, value);
			}
		}
		catch (IOException | RuntimeException e)
		{
			// A save cut short keeps everything before the cut.
		}
		return any ? a.repair() : null;
	}

	/** One saved run or purple, or null when it does not read. */
	private static <T> T piece(Gson gson, JsonElement item, Class<T> type)
	{
		try
		{
			return item == null || !item.isJsonObject() ? null : gson.fromJson(item, type);
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	/** Sets one of the archive's own figures from a damaged save, when it reads; true when it did. */
	private boolean figure(Gson gson, String name, JsonElement value)
	{
		try
		{
			switch (name)
			{
				case "kc":
					kc = gson.fromJson(value, int.class);
					return true;
				case "dry":
					dry = gson.fromJson(value, int.class);
					return true;
				case "clears":
					clears = gson.fromJson(value, int[].class);
					return true;
				case "wipes":
					wipes = gson.fromJson(value, int[].class);
					return true;
				case "goals":
					goals = gson.fromJson(value, int[].class);
					return true;
				default:
					// A figure a later version keeps, unknown here.
					return false;
			}
		}
		catch (RuntimeException e)
		{
			return false;
		}
	}

	/**
	 * A copy to hand the panel, sharing nothing it could change: quicker on the client thread than
	 * writing the archive out and reading it back, as the copy after each run once was.
	 */
	public Archive copy()
	{
		Archive a = new Archive();
		for (Run r : runs)
		{
			a.runs.add(r.copy());
		}
		// Removing a purple gives its clears to the next one, so each is copied too.
		for (Drop d : drops)
		{
			a.drops.add(new Drop(d.getUnique(), d.getAt(), d.getKc(), d.getDry()));
		}
		a.kc = kc;
		a.dry = dry;
		a.clears = clears == null ? null : clears.clone();
		a.wipes = wipes == null ? null : wipes.clone();
		a.goals = goals == null ? null : goals.clone();
		return a;
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
		for (int b = 0; b < clears.length; b++)
		{
			clears[b] = Math.max(0, clears[b]);
			wipes[b] = Math.max(0, wipes[b]);
		}
		trim();
		return this;
	}
}
