package com.codex.archive.ui;

import com.codex.archive.core.Comparison;
import com.codex.archive.core.Encounter;
import com.codex.archive.core.Outcome;
import com.codex.archive.core.Records;
import com.codex.archive.core.Run;
import com.codex.archive.core.Split;

/** A run's splits as plain text, lined up for pasting into a chat. */
public final class Share
{
	private Share()
	{
	}

	/**
	 * @param pb the best run before this one, to show each boss against; or null
	 */
	public static String splits(Run run, Comparison pb)
	{
		StringBuilder sb = new StringBuilder();
		String party = Format.party(Records.BUCKETS[Records.bucket(run.getTeamSize())]);
		sb.append("Fractured Archive, ").append(party).append(", ").append(Format.day(run.getStartedAt())).append('\n');
		for (int row = 0; row < Encounter.ROWS; row++)
		{
			Split s = run.split(row);
			if (s == null || !s.isDone())
			{
				continue;
			}
			sb.append(pad(s.getEncounter().getShortName(), 11))
				.append(pad(Format.ticks(s.getEndTick()), 9))
				.append("room ").append(pad(Format.ticks(s.roomTicks(0)), 8));
			Integer delta = pb == null ? null : pb.delta(run, row);
			if (delta != null)
			{
				sb.append(' ').append(Format.delta(delta));
			}
			sb.append('\n');
		}
		if (run.getOutcome() == Outcome.COMPLETED)
		{
			sb.append("Cleared in ").append(Format.ticks(run.getTicks()));
			int best = pb == null ? -1 : pb.total();
			if (best > 0)
			{
				sb.append(run.getTicks() < best ? ", a new best by " + Format.span(best - run.getTicks())
					: run.getTicks() == best ? ", level with the best"
					: ", " + Format.span(run.getTicks() - best) + " off the best");
			}
		}
		else
		{
			sb.append(run.getOutcome() == Outcome.WIPED ? "Wiped" : "Left").append(" at ").append(Format.ticks(run.getTicks()));
		}
		if (run.getDeaths() > 0)
		{
			sb.append(", ").append(run.getDeaths()).append(run.getDeaths() == 1 ? " death" : " deaths");
		}
		return sb.append('\n').toString();
	}

	private static String pad(String text, int width)
	{
		StringBuilder sb = new StringBuilder(text);
		while (sb.length() < width)
		{
			sb.append(' ');
		}
		return sb.toString();
	}
}
