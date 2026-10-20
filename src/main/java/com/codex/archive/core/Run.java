package com.codex.archive.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One trip into the archive, kept in the run history. */
public class Run
{
	/** The roles a raider can note for a run; free text goes in the note. Kept here so there is one list. */
	public static final String[] ROLES = {"Tank", "Melee", "Ranged", "Magic", "Support", "Learner"};
	/** Longest note kept, so one run cannot bloat the save. */
	public static final int NOTE_LENGTH = 80;
	/** Most teammates kept for one run. */
	public static final int CREW = 9;

	private long startedAt;
	private int teamSize;
	private Outcome outcome;
	/** Ticks from entering the archive to the final boss falling, or to leaving when it did not. */
	private int ticks;
	private List<Split> splits = new ArrayList<>();
	/** The completion count the game announced, or 0 when it said none. */
	private int kc;
	private int deaths;
	/** The game's own completion time for the run, in ticks, or 0 when it gave none. */
	private int gameTicks;
	/** Supply units used, by kind: pieces of food, potion doses. */
	private Map<String, Integer> used = new LinkedHashMap<>();
	/** The unique the run gave, or null. */
	private String purple;
	/** The other raiders seen at the start, by name. */
	private List<String> crew = new ArrayList<>();
	/** The role the player noted for this run, or null. */
	private String role;
	/** The player's own note on the run, or null. */
	private String note;
	/**
	 * Big NPCs fought in the run that matched no boss, as "Name (level)", the most hit first;
	 * null when there were none. They show what the run saw when a boss was not recognised.
	 */
	private List<String> unknown;
	/** Most unrecognised NPCs kept for one run. */
	public static final int UNKNOWN = 4;

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

	public List<String> getCrew()
	{
		return crew == null ? Collections.emptyList() : Collections.unmodifiableList(crew);
	}

	public String getRole()
	{
		return role;
	}

	public String getNote()
	{
		return note;
	}

	/** Big NPCs fought that matched no boss, "Name (level)", the most hit first. */
	public List<String> getUnknown()
	{
		return unknown == null ? Collections.emptyList() : Collections.unmodifiableList(unknown);
	}

	void setUnknown(List<String> names)
	{
		unknown = names == null || names.isEmpty() ? null : new ArrayList<>(names.subList(0, Math.min(UNKNOWN, names.size())));
	}

	/** Whether any boss of the run was guessed, or was not recognised at all. */
	public boolean hasDoubts()
	{
		for (Split s : splits)
		{
			if (s.getSeen() != null)
			{
				return true;
			}
		}
		// Unrecognised foes matter only when a boss went untimed: an add in a timed room is no doubt.
		return !getUnknown().isEmpty() && !untimedRows().isEmpty();
	}

	/**
	 * The rows up to the one the run ended in that have no split, so a boss the run reached went
	 * untimed; empty when every boss reached was timed, as in a wipe to a boss known by name.
	 */
	public List<Integer> untimedRows()
	{
		List<Integer> rows = new ArrayList<>();
		for (int row = 0; row < Math.min(Encounter.ROWS, reached() + 1); row++)
		{
			if (split(row) == null)
			{
				rows.add(row);
			}
		}
		return rows;
	}

	/** Forgets the detection notes, which only matter while the run is fresh. */
	void forgetDoubts()
	{
		unknown = null;
		for (Split s : splits)
		{
			s.forgetSeen();
		}
	}

	/** Adds a raider seen in the run, keeping the first few distinct names. */
	void addCrew(String name)
	{
		if (crew == null)
		{
			crew = new ArrayList<>();
		}
		if (name != null && !name.isEmpty() && crew.size() < CREW && !crew.contains(name))
		{
			crew.add(name);
		}
	}

	void setRole(String role)
	{
		this.role = role == null || role.trim().isEmpty() ? null : role.trim();
	}

	/** @return the game's completion time in ticks, or -1 when it gave none */
	public int getGameTicks()
	{
		return gameTicks > 0 ? gameTicks : -1;
	}

	public void setGameTicks(int ticks)
	{
		this.gameTicks = Math.max(0, ticks);
	}

	void setNote(String note)
	{
		String n = note == null ? "" : note.replaceAll("[\\r\\n\\t]+", " ").trim();
		this.note = n.isEmpty() ? null : n.length() > NOTE_LENGTH ? n.substring(0, NOTE_LENGTH) : n;
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

	/**
	 * How far the run has come: one past the furthest boss down. A row the client missed does not
	 * hold the count back, so the row after the furthest fall is always the one being fought.
	 */
	public int reached()
	{
		int n = 0;
		for (Split s : splits)
		{
			if (s.isDone() && s.getEncounter() != null)
			{
				n = Math.max(n, s.getEncounter().getRow() + 1);
			}
		}
		return n;
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
				if (row < reached())
				{
					sb.append(sb.length() > 0 ? ", " : "").append("?");
				}
				continue;
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
		used.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null || e.getValue() < 0);
		ticks = Math.max(0, ticks);
		kc = Math.max(0, kc);
		deaths = Math.max(0, deaths);
		if (note != null && note.length() > NOTE_LENGTH)
		{
			note = note.substring(0, NOTE_LENGTH);
		}
		if (outcome == null)
		{
			outcome = Outcome.LEFT;
		}
		if (crew == null)
		{
			crew = new ArrayList<>();
		}
		crew.removeIf(n -> n == null || n.isEmpty());
		while (crew.size() > CREW)
		{
			crew.remove(crew.size() - 1);
		}
		if (unknown != null)
		{
			unknown.removeIf(n -> n == null);
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
		r.crew = new ArrayList<>(getCrew());
		r.role = role;
		r.note = note;
		r.unknown = unknown == null ? null : new ArrayList<>(unknown);
		return r;
	}
}
