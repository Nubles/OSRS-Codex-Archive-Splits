package com.codex.archive.core;

/** How a run ended. */
public enum Outcome
{
	/** Ocellus fell. */
	COMPLETED,
	/** The whole party fell and was sent back to the lobby. */
	WIPED,
	/** The player left the archive before the end without the party falling. */
	LEFT
}
