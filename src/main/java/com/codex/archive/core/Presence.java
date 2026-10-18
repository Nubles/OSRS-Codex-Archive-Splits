package com.codex.archive.core;

/**
 * Where the player is, as far as the archive goes: in its lobby, gone through its doors, or back
 * out. It works from what any client can see each tick (whether the scene is an instance, when
 * a scene loaded and whether the lobby's own NPCs are about), so it holds whether the lobby is
 * itself an instance or open world.
 */
public final class Presence
{
	/** What a tick means for the run. */
	public enum Action
	{
		NONE,
		BEGIN,
		END
	}

	/** How long after the lobby was last seen an instance still counts as the archive, about a minute. */
	public static final int LOBBY_TICKS = 100;
	/** Ticks after a scene load that still count as arriving in it. */
	static final int ARRIVAL_TICKS = 3;
	/** A run must be this old before meeting the lobby's NPCs again ends it, so their leaving at the start does not. */
	static final int RETURN_GRACE_TICKS = 20;

	private boolean inInstance;
	private int instanceSince = -1;
	private int lobbySeen = Integer.MIN_VALUE / 2;
	private int lobbyNpcs;
	private int loadedAt = Integer.MIN_VALUE / 2;

	/** One of the lobby's NPCs came into view, or left it. */
	public void lobbyNpc(boolean spawned, int tick)
	{
		lobbyNpcs = spawned ? lobbyNpcs + 1 : Math.max(0, lobbyNpcs - 1);
		lobbySeen = tick;
	}

	/** The lobby NPCs in view when the plugin starts, since it missed their arrival. */
	public void lobbyNpcsInView(int count, int tick)
	{
		lobbyNpcs = Math.max(0, count);
		if (count > 0)
		{
			lobbySeen = tick;
		}
	}

	/** The client loaded a new scene: a teleport, a door into an instance, a hop. */
	public void sceneLoaded(int tick)
	{
		loadedAt = tick;
	}

	/** Logged out or hopped: nothing seen before counts any more. */
	public void reset()
	{
		inInstance = false;
		instanceSince = -1;
		lobbyNpcs = 0;
	}

	public boolean isInInstance()
	{
		return inInstance;
	}

	/** In the lobby: its NPCs are in view. */
	public boolean isInLobby()
	{
		return lobbyNpcs > 0;
	}

	/**
	 * Called once a tick. A new scene is judged a few ticks after it loads, once the NPCs of the
	 * place left behind have gone and those of the new one have come: an instance with none of the
	 * lobby's NPCs, loaded just after the lobby was seen, is the archive.
	 *
	 * @param instance whether the scene is an instance this tick
	 * @param active whether a run is being timed
	 * @param elapsed the run's ticks so far, when one is
	 */
	public Action tick(int tick, boolean instance, boolean active, int elapsed)
	{
		if (instance && !inInstance)
		{
			instanceSince = tick;
		}
		int sinceLoad = tick - loadedAt;
		Action action = Action.NONE;
		if (active)
		{
			if (inInstance && !instance)
			{
				action = Action.END;
			}
			else if (instance && sinceLoad <= ARRIVAL_TICKS && lobbyNpcs > 0 && elapsed >= RETURN_GRACE_TICKS)
			{
				// Back among the lobby's NPCs after a load: out of the archive, even if the lobby is an instance too.
				action = Action.END;
			}
		}
		else if (instance && sinceLoad == ARRIVAL_TICKS && lobbyNpcs == 0 && tick - lobbySeen <= LOBBY_TICKS)
		{
			action = Action.BEGIN;
		}
		inInstance = instance;
		return action;
	}

	/** The tick a run judged to have begun now really began: the first tick in the new scene. */
	public int beginTick()
	{
		return loadedAt + 1;
	}

	/**
	 * When a run the plugin only noticed late, at the first boss, most likely began: when the
	 * lobby was left, or when the instance was entered, whichever came later.
	 */
	public int likelyStart(int tick)
	{
		int start = instanceSince >= 0 ? instanceSince : tick;
		if (lobbySeen > start && lobbySeen <= tick)
		{
			start = lobbySeen;
		}
		return Math.min(start, tick);
	}
}
