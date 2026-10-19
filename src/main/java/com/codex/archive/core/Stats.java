package com.codex.archive.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Small order statistics over tick counts. */
public final class Stats
{
	private Stats()
	{
	}

	/** The middle value, the mean of the two middle ones for an even count, or -1 for none. */
	public static int median(List<Integer> values)
	{
		return percentile(values, 0.5);
	}

	/** The value this share of the way up, interpolated between neighbours, or -1 for none. */
	public static int percentile(List<Integer> values, double share)
	{
		if (values == null || values.isEmpty())
		{
			return -1;
		}
		List<Integer> sorted = new ArrayList<>(values);
		Collections.sort(sorted);
		double at = share * (sorted.size() - 1);
		int lo = (int) Math.floor(at);
		int hi = Math.min(sorted.size() - 1, lo + 1);
		return (int) Math.round(sorted.get(lo) + (sorted.get(hi) - sorted.get(lo)) * (at - lo));
	}

	/** The standard deviation, or -1 with fewer than two values. */
	public static int deviation(List<Integer> values)
	{
		if (values == null || values.size() < 2)
		{
			return -1;
		}
		double mean = 0;
		for (int v : values)
		{
			mean += v;
		}
		mean /= values.size();
		double sum = 0;
		for (int v : values)
		{
			sum += (v - mean) * (v - mean);
		}
		return (int) Math.round(Math.sqrt(sum / (values.size() - 1)));
	}
}
