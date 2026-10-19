package com.codex.archive.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Where a goal time stands against the runs so far: how far the best run is from it, how often a
 * clear has beaten it, and which rooms a typical run loses the most in against the goal's share.
 * The share follows your best rooms, so a room you have done quickly before but usually do slowly
 * stands out, since that is where the time is to be found.
 */
public final class Goal
{
	/** One row against the goal: the room the goal allows and the room a typical run takes. */
	public static final class Row
	{
		public final int row;
		public final int allowed;
		public final int typical;

		Row(int row, int allowed, int typical)
		{
			this.row = row;
			this.allowed = allowed;
			this.typical = typical;
		}

		/** Ticks a typical room takes over the goal's share; negative when it is already under. */
		public int over()
		{
			return typical - allowed;
		}
	}

	private final int goal;
	private final int best;
	private final int beaten;
	private final Run first;
	private final List<Row> rows;

	private Goal(int goal, int best, int beaten, Run first, List<Row> rows)
	{
		this.goal = goal;
		this.best = best;
		this.beaten = beaten;
		this.first = first;
		this.rows = rows;
	}

	/** @return where the goal stands, or null when none is set for the party size */
	public static Goal of(List<Run> runs, int bucket, int goal)
	{
		if (goal <= 0)
		{
			return null;
		}
		Run best = Records.best(runs, bucket);
		int beaten = 0;
		Run first = null;
		for (Run r : runs)
		{
			if (r.isCompleted() && Records.bucket(r.getTeamSize()) == bucket && r.getTicks() <= goal)
			{
				beaten++;
				if (first == null || r.getStartedAt() < first.getStartedAt())
				{
					first = r;
				}
			}
		}
		// The goal shared out like your best rooms: each row's share of what you have shown you can do.
		// A typical room furthest over its share is where the most time is to be found.
		Comparison golds = Comparison.of(runs, bucket, Comparison.Mode.BEST_ROOMS, null, 0);
		Comparison typical = Comparison.of(runs, bucket, Comparison.Mode.TYPICAL, null, 0);
		List<Row> rows = new ArrayList<>();
		int[] shape = new int[Encounter.ROWS];
		boolean whole = true;
		for (int row = 0; row < Encounter.ROWS; row++)
		{
			shape[row] = golds.room(row);
			whole &= shape[row] > 0;
		}
		if (whole)
		{
			int[] allowed = Comparison.share(shape, goal);
			for (int row = 0; row < Encounter.ROWS; row++)
			{
				if (typical.room(row) > 0)
				{
					rows.add(new Row(row, allowed[row], typical.room(row)));
				}
			}
		}
		rows.sort(Comparator.comparingInt((Row r) -> -r.over()).thenComparingInt(r -> r.row));
		return new Goal(goal, best == null ? -1 : best.getTicks(), beaten, first, rows);
	}

	public int getGoal()
	{
		return goal;
	}

	/** The best clear in ticks, or -1 with none. */
	public int getBest()
	{
		return best;
	}

	/** Clears at or under the goal. */
	public int getBeaten()
	{
		return beaten;
	}

	/** The first clear that beat it, or null. */
	public Run getFirst()
	{
		return first;
	}

	/** Every row with a typical room, the one furthest over the goal's share first. */
	public List<Row> getRows()
	{
		return rows;
	}

	/** Ticks the best run is over the goal; 0 or less once it is beaten. */
	public int gap()
	{
		return best < 0 ? -1 : best - goal;
	}

	/**
	 * Parses a time the player typed: "20:00", "19:42.6", "1:02:30", or a bare number of minutes
	 * ("20"), since the prompt asks for minutes and a raid goal is never a matter of seconds.
	 *
	 * @return ticks, rounded to the nearest, or -1 when it is not a time
	 */
	public static int parse(String text)
	{
		if (text == null)
		{
			return -1;
		}
		String t = text.trim();
		if (t.isEmpty() || !t.matches("\\d{1,3}(:\\d{1,2}){0,2}(\\.\\d+)?"))
		{
			return -1;
		}
		String[] parts = t.split(":");
		double seconds = 0;
		try
		{
			for (int i = 0; i < parts.length; i++)
			{
				double v = Double.parseDouble(parts[i]);
				if (i > 0 && v >= 60)
				{
					return -1;
				}
				seconds = seconds * 60 + v;
			}
		}
		catch (NumberFormatException e)
		{
			return -1;
		}
		if (parts.length == 1)
		{
			seconds *= 60;
		}
		int ticks = (int) Math.round(seconds / 0.6);
		return ticks > 0 ? ticks : -1;
	}
}
