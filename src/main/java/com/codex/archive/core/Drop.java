package com.codex.archive.core;

/** A purple, as the spoils page lists it. */
public class Drop
{
	private String unique;
	private long at;
	/** The completion count it came on, or 0 when unknown. */
	private int kc;
	/** Completions since the purple before it. */
	private int dry;

	public Drop()
	{
	}

	Drop(String unique, long at, int kc, int dry)
	{
		this.unique = unique;
		this.at = at;
		this.kc = kc;
		this.dry = dry;
	}

	public String getUnique()
	{
		return unique;
	}

	public long getAt()
	{
		return at;
	}

	public int getKc()
	{
		return kc;
	}

	public int getDry()
	{
		return dry;
	}
}
