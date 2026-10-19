package com.codex.archive.ui;

import com.codex.archive.core.Archive;
import com.codex.archive.core.Comparison;
import com.codex.archive.core.Encounter;
import com.codex.archive.core.Records;
import com.codex.archive.core.Run;
import com.codex.archive.core.Supply;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** One moment of the plugin's state, handed to the panel and overlays so Swing never reads what the client thread writes. */
public final class View
{
	public final String player;
	public final boolean loggedIn;
	/** A copy of the account's archive, or null with no account open. */
	public final Archive archive;
	/** The run in progress, or null. */
	public final Run live;
	public final boolean inLobby;
	/** What the pack holds now, by supply. */
	public final Map<Supply, Integer> pack;
	/** What the player wants to carry in, by supply. */
	public final Map<Supply, Integer> targets;
	public final long now;
	/** The best run for the live run's party size, or null. */
	public final Run liveBest;
	/** A run cleared moments ago while the player is still inside, for the plaque's last word; or null. */
	public final Run cleared;
	/** What runs are measured against. */
	public final Comparison.Mode compare;
	/** The marks for the live or just-cleared run, never itself; or null with neither. */
	public final Comparison comparison;
	/** Each row's best room for that run's party size, not counting the run itself; -1 where none. */
	public final int[] bestRooms;

	public View(String player, boolean loggedIn, Archive archive, Run live, boolean inLobby,
		Map<Supply, Integer> pack, Map<Supply, Integer> targets, long now)
	{
		this(player, loggedIn, archive, live, inLobby, pack, targets, now, null, Comparison.Mode.PERSONAL_BEST);
	}

	public View(String player, boolean loggedIn, Archive archive, Run live, boolean inLobby,
		Map<Supply, Integer> pack, Map<Supply, Integer> targets, long now, Run cleared)
	{
		this(player, loggedIn, archive, live, inLobby, pack, targets, now, cleared, Comparison.Mode.PERSONAL_BEST);
	}

	public View(String player, boolean loggedIn, Archive archive, Run live, boolean inLobby,
		Map<Supply, Integer> pack, Map<Supply, Integer> targets, long now, Run cleared, Comparison.Mode compare)
	{
		this.player = player;
		this.loggedIn = loggedIn;
		this.archive = archive;
		this.live = live;
		this.inLobby = inLobby;
		this.pack = pack == null ? Collections.emptyMap() : new EnumMap<>(pack);
		this.targets = targets == null ? Collections.emptyMap() : new EnumMap<>(targets);
		this.now = now;
		this.cleared = cleared;
		Run shownRun = live != null ? live : cleared;
		this.liveBest = archive == null || shownRun == null ? null : bestBefore(archive, shownRun);
		this.compare = compare == null ? Comparison.Mode.PERSONAL_BEST : compare;
		this.comparison = archive == null || shownRun == null ? null
			: Comparison.of(archive.getRuns(), Records.bucket(shownRun.getTeamSize()), this.compare, shownRun);
		this.bestRooms = archive == null || shownRun == null ? new int[0] : bestRooms(archive, shownRun);
	}

	/** Each row's best room in a run's party size, from every run but that one. */
	static int[] bestRooms(Archive archive, Run run)
	{
		Comparison golds = Comparison.of(archive.getRuns(), Records.bucket(run.getTeamSize()), Comparison.Mode.BEST_ROOMS, run);
		int[] rooms = new int[Encounter.ROWS];
		for (int row = 0; row < rooms.length; row++)
		{
			rooms[row] = golds.room(row);
		}
		return rooms;
	}

	/** The best run of the same party size, other than the run itself. */
	private static Run bestBefore(Archive archive, Run run)
	{
		Run best = null;
		int bucket = Records.bucket(run.getTeamSize());
		for (Run r : archive.getRuns())
		{
			if (r != run && r.getStartedAt() != run.getStartedAt() && r.isCompleted()
				&& Records.bucket(r.getTeamSize()) == bucket && (best == null || r.getTicks() < best.getTicks()))
			{
				best = r;
			}
		}
		return best;
	}

	/** Whether any supply has a target at all; with none, there is no pack check. */
	public boolean checksPack()
	{
		for (int v : targets.values())
		{
			if (v > 0)
			{
				return true;
			}
		}
		return false;
	}

	public static View loggedOut()
	{
		return new View(null, false, null, null, false, null, null, System.currentTimeMillis());
	}

	/** Supplies the pack is short of, against the targets. */
	public Map<Supply, Integer> shortfall()
	{
		Map<Supply, Integer> out = new EnumMap<>(Supply.class);
		for (Supply s : Supply.values())
		{
			int want = targets.getOrDefault(s, 0);
			int have = pack.getOrDefault(s, 0);
			if (have < want)
			{
				out.put(s, want - have);
			}
		}
		return out;
	}
}
