package com.codex.archive.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.codex.archive.core.Archive;
import com.codex.archive.core.Encounter;
import com.codex.archive.core.Run;
import com.codex.archive.core.RunTracker;
import com.codex.archive.core.Sessions;
import com.codex.archive.core.StandingTestRuns;
import com.codex.archive.core.Supply;
import com.codex.archive.overlay.PackPlaque;
import com.codex.archive.overlay.SplitsPlaque;
import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * The one saved key, "archive", read back damaged every way a save can be: empty, cut short,
 * the wrong shape, figures too big or below zero, a later version's, or not JSON at all. None
 * may throw, freeze the panel or the cards, or lose the runs that still read.
 */
public class DamagedSaveTest
{
	private static final Gson GSON = new Gson();
	private static final long MIN = 60_000L;

	private static Archive good(int runs)
	{
		Archive a = new Archive();
		for (int i = 0; i < runs; i++)
		{
			boolean wipe = i % 7 == 3;
			Run r = wipe ? StandingTestRuns.run(i * 20 * MIN, 1 + i % 5, true, 90, 310)
				: StandingTestRuns.run(i * 20 * MIN, 1 + i % 5, false, 90, 310, 480, 690, 950 + i % 40);
			a.record(r, i % 9 == 4 ? java.util.Collections.singletonList("Zorya's tome") : null, i * 20 * MIN + 1);
		}
		a.setGoal(2, 900);
		return a;
	}

	private static List<String> damaged(String save)
	{
		List<String> out = new ArrayList<>();
		out.add("");
		out.add("   ");
		out.add("null");
		out.add("{");
		out.add("}");
		out.add("[]");
		out.add("5");
		out.add("\"an archive\"");
		out.add("true");
		out.add("{\"runs\":5}");
		out.add("{\"runs\":\"many\",\"drops\":{}}");
		out.add("{\"runs\":[5,null,\"x\",[1,2],{}],\"kc\":\"lots\"}");
		out.add("{\"kc\":1e30,\"dry\":-40,\"clears\":[-1,-2],\"wipes\":\"no\",\"goals\":[99999999999]}");
		out.add("{\"kc\":-5,\"dry\":2147483647,\"clears\":[2147483647,2147483647,2147483647,2147483647,2147483647]}");
		out.add("{\"runs\":[{\"startedAt\":-1,\"teamSize\":-3,\"outcome\":\"EXPLODED\",\"ticks\":-90,\"splits\":[{\"encounter\":\"NOBODY\"},null,{\"encounter\":\"BORK\",\"startTick\":-5,\"endTick\":-9}],\"used\":{\"FOOD\":null,\"BREWS\":-3},\"crew\":[null,\"\",\"A\",\"B\",\"C\",\"D\",\"E\",\"F\",\"G\",\"H\",\"I\",\"J\"]}]}");
		out.add("{\"runs\":[{\"startedAt\":9223372036854775807,\"teamSize\":2147483647,\"outcome\":\"COMPLETED\",\"ticks\":2147483647}]}");
		out.add("{\"runs\":[{\"startedAt\":1,\"teamSize\":3,\"outcome\":\"COMPLETED\",\"ticks\":1e40}]}");
		out.add("{\u0000￿}{][");
		out.add("﻿" + save);
		out.add(save.replace("{\"runs\":", "{\"version\":99,\"shelf\":{\"a\":[1,{\"b\":2}]},\"runs\":"));
		out.add(save.replaceFirst("\"ticks\":\\d+", "\"ticks\":\"slow\""));
		out.add(save.replaceFirst("\"teamSize\":\\d+", "\"teamSize\":{}"));
		// Every number in turn too big, below zero, a fraction, a word or nothing; every string a number or nonsense; every list and object the wrong shape.
		for (String n : new String[]{"99999999999999999999", "-5", "-9223372036854775808", "9223372036854775807", "2147483648", "1e308", "0.5", "\"many\"", "null", "{}", "[]"})
		{
			out.add(save.replaceAll("(?<=[:\\[,])-?\\d+(\\.\\d+)?(?=[,\\]}])", n));
			out.add(save.replaceFirst("(?<=[:\\[,])-?\\d+(\\.\\d+)?(?=[,\\]}])", n));
		}
		out.add(save.replaceAll("(?<=:)\"[^\"]*\"", "7"));
		out.add(save.replaceAll("(?<=:)\"[^\"]*\"", "\"\""));
		out.add(save.replaceAll("(?<=:)\"[^\"]*\"", "\"NOT_A_THING\""));
		out.add(save.replaceAll("\\[", "{").replaceAll("]", "}"));
		out.add(save.replaceAll("(?<=:)\\{", "[").replaceAll("}(?=[,}])", "]"));
		for (int cut = 1; cut < 20; cut++)
		{
			out.add(save.substring(0, save.length() * cut / 20));
		}
		return out;
	}

	@Test
	public void everyDamagedSaveReadsWithoutThrowingAndDrawsEverywhere()
	{
		String save = GSON.toJson(good(30));
		for (String bad : damaged(save))
		{
			Archive.Loaded read = Archive.read(GSON, bad);
			assertNotNull(bad, read);
			Archive a = read.archive != null ? read.archive : new Archive();
			draw(a, bad);
			// What was read saves and reads back whole.
			Archive.Loaded again = Archive.read(GSON, GSON.toJson(a));
			assertFalse(bad, again.damaged);
			assertEquals(bad, a.getRuns().size(), again.archive.getRuns().size());
		}
	}

	@Test
	public void oneDamagedRunNeverCostsTheRest()
	{
		Archive a = good(30);
		String save = GSON.toJson(a);
		// The third run's time is no longer a number.
		int at = nth(save, "\"ticks\":", 3);
		String bad = save.substring(0, at) + "\"ticks\":\"slow\"" + save.substring(save.indexOf(',', at));
		Archive.Loaded read = Archive.read(GSON, bad);
		assertTrue(read.damaged);
		assertEquals(29, read.archive.getRuns().size());
		assertEquals(a.getDrops().size(), read.archive.getDrops().size());
		assertEquals(a.getKc(), read.archive.getKc());
		assertEquals(900, read.archive.goal(2));

		// A save cut off in its runs keeps every run before the cut.
		String cut = save.substring(0, nth(save, "\"startedAt\":", 21));
		Archive.Loaded part = Archive.read(GSON, cut);
		assertTrue(part.damaged);
		assertEquals(20, part.archive.getRuns().size());

		// Nothing that reads at all is no archive, so the plugin starts afresh and keeps the save aside.
		assertNull(Archive.read(GSON, "[1,2,3]").archive);
		assertTrue(Archive.read(GSON, "[1,2,3]").damaged);
		assertFalse(Archive.read(GSON, "").damaged);
		assertFalse(Archive.read(GSON, save).damaged);
	}

	@Test
	public void aLaterVersionsSaveIsSetAsideNotQuietlyDropped()
	{
		String save = GSON.toJson(good(30));
		assertFalse(Archive.read(GSON, save).damaged);
		// Each reads without an error, but the next save would drop what this version does not know.
		String[] later = {
			save.replaceFirst("^\\{", "{\"version\":2,\"shelf\":{\"a\":[1,{\"b\":2}]},"),
			save.replaceFirst("\"runs\":\\[\\{", "\"runs\":[{\"mood\":\"glad\","),
			save.replaceFirst("\"encounter\":\"[A-Z_]+\"", "\"encounter\":\"FIFTH_BOSS\""),
			save.replaceFirst("\"clears\":\\[", "\"clears\":[4,"),
		};
		for (String s : later)
		{
			Archive.Loaded read = Archive.read(GSON, s);
			assertTrue(s.substring(0, 80), read.damaged);
			assertEquals(30, read.archive.getRuns().size());
			assertFalse(Archive.read(GSON, GSON.toJson(read.archive)).damaged);
		}
	}

	@Test
	public void figuresBelowZeroOrTooBigAreSetRight()
	{
		Archive a = Archive.read(GSON, "{\"kc\":-5,\"dry\":-1,\"clears\":[-4,1,2,3,4],\"wipes\":[0,0,0,0,-2],\"goals\":[-60,0,0,0,0],"
			+ "\"runs\":[{\"startedAt\":1000,\"teamSize\":3,\"outcome\":\"COMPLETED\",\"ticks\":-90,\"kc\":-3,\"deaths\":-1,\"used\":{\"FOOD\":-2,\"BREWS\":4}}]}").archive;
		assertEquals(0, a.getKc());
		assertEquals(0, a.getDry());
		assertEquals(0, a.clears(0));
		assertEquals(0, a.wipes(4));
		assertEquals(0, a.goal(0));
		Run r = a.getRuns().get(0);
		assertEquals(0, r.getTicks());
		assertEquals(0, r.getKc());
		assertEquals(0, r.getDeaths());
		assertEquals(1, r.getUsed().size());
	}

	@Test
	public void aFullArchiveIsQuickOnTheClientThread()
	{
		Archive a = good(Archive.MAX_RUNS);
		String save = GSON.toJson(a);
		RunTracker t = new RunTracker();
		t.begin(System.currentTimeMillis(), 0, 3, null);
		t.engage(Encounter.GUARDIAN, 20);
		Run live = t.view(200);
		Map<Supply, Integer> pack = new EnumMap<>(Supply.class);
		// Warm up, then time what runs on the client thread: the load, the copy handed to the
		// panel after a change, and the view made every tick of a run.
		for (int i = 0; i < 5; i++)
		{
			Archive.read(GSON, save);
			new View("Gilded Owl", true, a, live, false, pack, pack, 0);
		}
		long load = time(() -> Archive.read(GSON, save));
		long copy = time(a::copy);
		// The copy is the archive whole: it saves the very same.
		assertEquals(save, GSON.toJson(a.copy()));
		long tick = time(() -> new View("Gilded Owl", true, a, live, false, pack, pack, 0));
		System.out.println("Full archive: load " + load / 1000 + "us, copy " + copy / 1000 + "us, tick " + tick / 1000 + "us");
		assertTrue("load " + load / 1000 + "us", load < 60_000_000L);
		assertTrue("copy " + copy / 1000 + "us", copy < 3_000_000L);
		assertTrue("tick " + tick / 1000 + "us", tick < 3_000_000L);
	}

	private static long time(Runnable r)
	{
		long best = Long.MAX_VALUE;
		for (int i = 0; i < 5; i++)
		{
			long t0 = System.nanoTime();
			r.run();
			best = Math.min(best, System.nanoTime() - t0);
		}
		return best;
	}

	private static int nth(String s, String what, int n)
	{
		int at = -1;
		for (int i = 0; i < n; i++)
		{
			at = s.indexOf(what, at + 1);
		}
		return at;
	}

	/** Every page of the panel, both plaques and every card, from this archive. */
	private static void draw(Archive a, String why)
	{
		Map<Supply, Integer> targets = new EnumMap<>(Supply.class);
		targets.put(Supply.FOOD, 10);
		Map<Supply, Integer> pack = new EnumMap<>(Supply.class);
		RunTracker t = new RunTracker();
		t.begin(System.currentTimeMillis(), 0, 3, pack);
		t.engage(Encounter.GUARDIAN, 20);
		Run live = t.view(300);
		long now = System.currentTimeMillis();
		for (Run shownRun : new Run[]{null, live})
		{
			View v = new View("Gilded Owl", true, a, shownRun, shownRun == null, pack, targets, now);
			ArchivePanel p = new ArchivePanel();
			p.update(v);
			p.onActivate();
			for (int tab = 0; tab < 3; tab++)
			{
				p.showTab(tab);
				paint(p);
			}
			SplitsPlaque sp = new SplitsPlaque();
			sp.setEnabled(true);
			sp.show(v);
			PackPlaque pp = new PackPlaque();
			pp.setEnabled(true);
			pp.show(v);
			BufferedImage img = new BufferedImage(300, 300, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = img.createGraphics();
			sp.render(g);
			pp.render(g);
			g.dispose();
		}
		List<Run> all = a.getRuns();
		for (Run r : all)
		{
			assertNotNull(why, RunCard.render(com.codex.archive.core.Standing.of(all, r), "Gilded Owl", ZoneOffset.UTC));
		}
		for (Sessions.Session s : Sessions.of(all))
		{
			assertNotNull(why, SessionCard.render(s, all, null, ZoneOffset.UTC));
		}
	}

	private static void paint(Component c)
	{
		c.setSize(225, 2400);
		layout(c);
		BufferedImage img = new BufferedImage(225, 2400, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		c.paint(g);
		g.dispose();
	}

	private static void layout(Component c)
	{
		c.doLayout();
		if (c instanceof Container)
		{
			for (Component k : ((Container) c).getComponents())
			{
				layout(k);
			}
		}
	}
}
