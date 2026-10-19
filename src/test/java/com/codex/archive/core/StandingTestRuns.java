package com.codex.archive.core;

/** Runs for the standing and run card tests, built through the real tracker. */
public final class StandingTestRuns
{
	private StandingTestRuns()
	{
	}

	/** A run started at a moment, with each boss falling at the given ticks; fewer falls and wiped, a wipe. */
	public static Run run(long at, int team, boolean wiped, int... falls)
	{
		RunTracker t = new RunTracker();
		t.begin(at, 0, team, null);
		Encounter[] route = {Encounter.GUARDIAN, Encounter.BORK, Encounter.DREADNOTH, Encounter.XARTH, Encounter.HOODED_FIGURE};
		int prev = 0;
		for (int i = 0; i < falls.length; i++)
		{
			t.engage(route[i], prev + 5);
			t.fell(route[i], falls[i], false);
			for (int k = falls[i]; k <= falls[i] + RunTracker.FINAL_CONFIRM_TICKS; k++)
			{
				t.tick(k);
			}
			prev = falls[i];
		}
		if (wiped)
		{
			t.engage(route[falls.length], prev + 5);
			t.died();
		}
		return t.finish(prev + 50, wiped);
	}

	/** The run with these others in its party. */
	public static Run withCrew(Run run, String... names)
	{
		for (String name : names)
		{
			run.addCrew(name);
		}
		return run;
	}
}
