package com.codex.archive.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spots uniques arriving. It keeps the most of each unique the player has held (pack and worn
 * together) since the watch opened, and only more than that counts as a new one, so swapping a
 * weapon in and out, dropping one and picking it back up or charging it never makes a purple.
 */
public final class LootWatch
{
	private final Map<String, Integer> high = new HashMap<>();
	private boolean open;
	private int closesAt = Integer.MAX_VALUE;

	/** Opens the watch with what is held now, which is not new. */
	public void open(Map<String, Integer> held)
	{
		high.clear();
		if (held != null)
		{
			high.putAll(held);
		}
		open = true;
		closesAt = Integer.MAX_VALUE;
	}

	/** Keeps watching until a tick, then closes by itself. */
	public void closeAt(int tick)
	{
		closesAt = tick;
	}

	public void close()
	{
		open = false;
		high.clear();
	}

	public boolean isOpen(int tick)
	{
		if (open && tick > closesAt)
		{
			close();
		}
		return open;
	}

	/**
	 * @param held how many of each unique is held now
	 * @return each unique that arrived, once per copy
	 */
	public List<String> arrivals(Map<String, Integer> held, int tick)
	{
		List<String> out = new ArrayList<>();
		if (!isOpen(tick) || held == null)
		{
			return out;
		}
		for (Map.Entry<String, Integer> e : held.entrySet())
		{
			int before = high.getOrDefault(e.getKey(), 0);
			for (int i = before; i < e.getValue(); i++)
			{
				out.add(e.getKey());
			}
			if (e.getValue() > before)
			{
				high.put(e.getKey(), e.getValue());
			}
		}
		return out;
	}
}
