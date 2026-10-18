package com.codex.archive.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One trip into the archive, kept in the run history. */
public class Run
{
	private long startedAt;
	private int teamSize;
	private Outcome outcome;
	/** Ticks from entering the archive to the final boss falling, or to leaving when it did not. */
	private int ticks;
	private List<Split> splits = new ArrayList<>();
	/** The completion count the game announced, or 0 when it said none. */
	private int kc;
	private int deaths;
	/** Supply units used, by kind: pieces of food, potion doses. */
	private Map<String, Integer> used = new LinkedHashMap<>();
	/** The unique the run gave, or null. */
	private String purple;

	public Run()
	{
	}

	Run(long startedAt, int teamSize)
	{
		this.startedAt = startedAt;
		this.teamSize = teamSize;
	}

	public long getStartedAt()
	{
		return startedAt;
	}

	public int getTeamSize()
	{
		return teamSize;
	}

	public Outcome getOutcome()
	{
		return outcome;
	}

	public int getTicks()
	{
		return ticks;
	}

	public List<Split> getSplits()
	{
		return Collections.unmodifiableList(splits);
	}

	public int getKc()
	{
		return kc;
	}

	public int getDeaths()
	{
		return deaths;
	}

	public Map<String, Integer> getUsed()
	{
		return Collections.unmodifiableMap(used);
	}

	public String getPurple()
	{
		return purple;
	}

	public boolean isCompleted()
	{
		return outcome == Outcome.COMPLETED;
	}

	/** The split for a row, or null when the run never reached it. */
	public Split split(int row)
	{
		for (Split s : splits)
		{
			if (s.getEncounter() != null && s.getEncounter().getRow() == row)
			{
				return s;
			}
		}
		return null;
	}

	/** The doors this run took, as short names: "Bork, Dreadnoth, Trio". */
	public String route()
	{
		StringBuilder sb = new StringBuilder();
		for (int row = 1; row <= 3; row++)
		{
			Split s = split(row);
			if (s == null)
			{
				break;
			}
			if (sb.length() > 0)
			{
				sb.append(", ");
			}
			sb.append(s.getEncounter().getShortName());
		}
		return sb.toString();
	}

	void setTeamSize(int teamSize)
	{
		this.teamSize = teamSize;
	}

	void setKc(int kc)
	{
		this.kc = kc;
	}

	void setPurple(String purple)
	{
		this.purple = purple;
	}

	void finish(Outcome outcome, int ticks, int deaths, Map<String, Integer> used)
	{
		this.outcome = outcome;
		this.ticks = ticks;
		this.deaths = deaths;
		this.used = new LinkedHashMap<>(used);
	}

	List<Split> splitsMutable()
	{
		return splits;
	}

	/** Gson leaves fields it did not find null; this puts them right after a load. */
	Run repair()
	{
		if (splits == null)
		{
			splits = new ArrayList<>();
		}
		splits.removeIf(s -> s == null || s.getEncounter() == null);
		if (used == null)
		{
			used = new LinkedHashMap<>();
		}
		if (outcome == null)
		{
			outcome = Outcome.LEFT;
		}
		return this;
	}

	Run copy()
	{
		Run r = new Run(startedAt, teamSize);
		r.outcome = outcome;
		r.ticks = ticks;
		for (Split s : splits)
		{
			r.splits.add(s.copy());
		}
		r.kc = kc;
		r.deaths = deaths;
		r.used = new LinkedHashMap<>(used);
		r.purple = purple;
		return r;
	}
}
