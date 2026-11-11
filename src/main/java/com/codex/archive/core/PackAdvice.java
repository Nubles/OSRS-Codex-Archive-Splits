package com.codex.archive.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The pack check, learned from the player's own runs. Each run keeps what it ate and drank, so
 * the amounts to carry in can come from the most a recent clear used, with a little to spare; and
 * each wipe keeps what the pack still held when the player went down, so the lobby can say which
 * supply wiped runs had run out of. Nothing here is a game figure: it is all the player's own.
 */
public final class PackAdvice
{
	/** Clears a suggestion is learned from: the latest this many. */
	public static final int CLEARS = 10;
	/** The fewest clears worth learning from. */
	public static final int MIN_CLEARS = 3;
	/** Wipes the notes look back over: the latest this many that kept what was left. */
	public static final int WIPES = 10;
	/**
	 * Doses in a full potion, so a suggestion is a number of whole potions: "Saradomin brew(4)"
	 * is RuneLite's {@code ItemID._4DOSEPOTIONOFSARADOMIN}, and so for every potion the check counts.
	 */
	public static final int DOSES = 4;
	/** The most each setting takes: food in pieces, potions in doses. */
	static final int MAX_FOOD = 28;
	static final int MAX_DOSES = 112;

	private PackAdvice()
	{
	}

	/** Amounts to carry in, learned from recent clears. */
	public static final class Suggestion
	{
		/** The party-size group it was learned from, or -1 when from every party size. */
		public final int bucket;
		/** How many clears it rests on. */
		public final int clears;
		private final Map<Supply, Integer> most;
		private final Map<Supply, Integer> amounts;

		Suggestion(int bucket, int clears, Map<Supply, Integer> most, Map<Supply, Integer> amounts)
		{
			this.bucket = bucket;
			this.clears = clears;
			this.most = most;
			this.amounts = amounts;
		}

		/** The most of a supply one of the clears used. */
		public int most(Supply s)
		{
			return most.getOrDefault(s, 0);
		}

		/** What to carry in of a supply; 0 leaves it out of the check. */
		public int amount(Supply s)
		{
			return amounts.getOrDefault(s, 0);
		}

		/** Every supply's amount, ready to adopt as the pack check's targets. */
		public Map<Supply, Integer> amounts()
		{
			Map<Supply, Integer> out = new EnumMap<>(Supply.class);
			for (Supply s : Supply.values())
			{
				out.put(s, amount(s));
			}
			return out;
		}

		/** Whether the targets set now are already these amounts. */
		public boolean matches(Map<Supply, Integer> targets)
		{
			for (Supply s : Supply.values())
			{
				int now = targets == null || targets.get(s) == null ? 0 : targets.get(s);
				if (now != amount(s))
				{
					return false;
				}
			}
			return true;
		}
	}

	/** What the latest wipes had run out of. */
	public static final class Wipes
	{
		/** The party-size group they are from, or -1 when from every party size. */
		public final int bucket;
		/** How many wipes were looked at. */
		public final int wipes;
		private final Map<Supply, Integer> ranOut;

		Wipes(int bucket, int wipes, Map<Supply, Integer> ranOut)
		{
			this.bucket = bucket;
			this.wipes = wipes;
			this.ranOut = ranOut;
		}

		/** How many of the wipes had none of a supply left that they had carried in. */
		public int ranOut(Supply s)
		{
			return ranOut.getOrDefault(s, 0);
		}

		/** The supplies some wipe ran out of, the most often first. */
		public List<Supply> shortOf()
		{
			List<Supply> out = new ArrayList<>();
			for (Supply s : Supply.values())
			{
				if (ranOut(s) > 0)
				{
					out.add(s);
				}
			}
			// Stable, so a tie keeps the order of the pack check.
			out.sort((a, b) -> Integer.compare(ranOut(b), ranOut(a)));
			return Collections.unmodifiableList(out);
		}
	}

	/**
	 * The party size the lobby advises for: the party is not known until the doors close, and the
	 * last run's is the likeliest. -1, every size, before any run.
	 */
	public static int lobbyBucket(List<Run> runs)
	{
		for (int i = runs.size() - 1; i >= 0; i--)
		{
			if (runs.get(i) != null)
			{
				return Records.bucket(runs.get(i).getTeamSize());
			}
		}
		return -1;
	}

	/**
	 * Amounts learned from the latest clears of a party size, or of every size when that one has
	 * too few. Null when there are too few clears, or none of them used anything.
	 *
	 * @param bucket the party-size group to learn from first, or -1 for every size
	 */
	public static Suggestion suggest(List<Run> runs, int bucket)
	{
		Suggestion s = suggestFrom(runs, bucket);
		return s == null && bucket >= 0 ? suggestFrom(runs, -1) : s;
	}

	private static Suggestion suggestFrom(List<Run> runs, int bucket)
	{
		Map<Supply, Integer> most = new EnumMap<>(Supply.class);
		int clears = 0;
		for (int i = runs.size() - 1; i >= 0 && clears < CLEARS; i--)
		{
			Run r = runs.get(i);
			if (r == null || !r.isCompleted() || (bucket >= 0 && Records.bucket(r.getTeamSize()) != bucket))
			{
				continue;
			}
			clears++;
			for (Supply s : Supply.values())
			{
				most.merge(s, Math.max(0, used(r, s)), Math::max);
			}
		}
		if (clears < MIN_CLEARS)
		{
			return null;
		}
		Map<Supply, Integer> amounts = new EnumMap<>(Supply.class);
		boolean any = false;
		for (Supply s : Supply.values())
		{
			int n = amount(s, most.getOrDefault(s, 0));
			amounts.put(s, n);
			any |= n > 0;
		}
		return any ? new Suggestion(bucket, clears, most, amounts) : null;
	}

	/**
	 * What to carry of a supply when the most a clear used was this much: that, plus a tenth of it
	 * rounded up (always at least one more), and potions rounded up to whole potions. Nothing used
	 * is nothing to bring.
	 */
	public static int amount(Supply s, int most)
	{
		if (most <= 0)
		{
			return 0;
		}
		int n = most + Math.max(1, (most + 9) / 10);
		if (s != Supply.FOOD)
		{
			n = (n + DOSES - 1) / DOSES * DOSES;
		}
		return Math.min(n, s == Supply.FOOD ? MAX_FOOD : MAX_DOSES);
	}

	/**
	 * What the latest wipes of a party size had run out of, or of every size when that one has no
	 * wipe that kept what was left. Null when there is no such wipe at all.
	 */
	public static Wipes wipes(List<Run> runs, int bucket)
	{
		Wipes w = wipesFrom(runs, bucket);
		return w == null && bucket >= 0 ? wipesFrom(runs, -1) : w;
	}

	private static Wipes wipesFrom(List<Run> runs, int bucket)
	{
		Map<Supply, Integer> ranOut = new EnumMap<>(Supply.class);
		int wipes = 0;
		for (int i = runs.size() - 1; i >= 0 && wipes < WIPES; i--)
		{
			Run r = runs.get(i);
			if (r == null || r.getOutcome() != Outcome.WIPED || !r.knowsLeft()
				|| (bucket >= 0 && Records.bucket(r.getTeamSize()) != bucket))
			{
				continue;
			}
			wipes++;
			Map<String, Integer> left = r.getLeft();
			for (Supply s : Supply.values())
			{
				Integer n = left.get(s.name());
				// Carried in (it is listed) and none left; a supply never brought is not run out of.
				if (n != null && n == 0)
				{
					ranOut.merge(s, 1, Integer::sum);
				}
			}
		}
		return wipes == 0 ? null : new Wipes(bucket, wipes, ranOut);
	}

	private static int used(Run r, Supply s)
	{
		Integer n = r.getUsed().get(s.name());
		return n == null ? 0 : n;
	}
}
