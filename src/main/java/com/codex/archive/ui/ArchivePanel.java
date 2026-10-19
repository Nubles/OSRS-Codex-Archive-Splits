package com.codex.archive.ui;

import com.codex.archive.core.Archive;
import com.codex.archive.core.Comparison;
import com.codex.archive.core.Drop;
import com.codex.archive.core.Encounter;
import com.codex.archive.core.Goal;
import com.codex.archive.core.Insights;
import com.codex.archive.core.Outcome;
import com.codex.archive.core.Records;
import com.codex.archive.core.Run;
import com.codex.archive.core.Sessions;
import com.codex.archive.core.Spoils;
import com.codex.archive.core.Split;
import com.codex.archive.core.Stats;
import com.codex.archive.core.Supply;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.LongConsumer;
import javax.swing.JComponent;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.PluginPanel;

/**
 * The side panel: the crest, then three pages. Run is the run in progress (or the last one) as a
 * thread of splits against the best, with the pack check while in the lobby. Records holds the
 * bests for each party size, and Spoils the purples and the dry streak.
 */
public class ArchivePanel extends PluginPanel
{
	static final int WIDTH = 200;
	private static final String[] TABS = {"run", "records", "spoils"};
	private static final int HISTORY = 12;
	private static final int DROPS = 10;
	private static final int CREW = 6;
	private static final int SESSIONS = 5;

	private final Crest crest = new Crest();
	private final Segmented tabs = new Segmented(true, "Run", "Records", "Spoils");
	private final Segmented party = new Segmented(false, Records.BUCKETS);
	private final CardLayout cards = new CardLayout();
	private final JPanel content = new JPanel(cards);
	private final JScrollPane runPage = scroller();
	private final JScrollPane recordsPage = scroller();
	private final JScrollPane spoilsPage = scroller();
	private final SplitTable table = new SplitTable();
	private final PackCard packCard = new PackCard("Pack check");
	private final PackCard usedCard = new PackCard("Used this run");

	private View view = View.loggedOut();
	private boolean partyChosen;
	private LongConsumer onDeleteRun = t -> { };
	private BiConsumer<Long, String> onDeleteDrop = (t, u) -> { };
	private BiConsumer<Long, String> onRole = (t, r) -> { };
	private BiConsumer<Long, String> onNote = (t, n) -> { };
	private Runnable onExport = () -> { };
	private BiConsumer<Integer, Integer> onGoal = (b, t) -> { };
	/** What the run page was last built from; while it holds, a tick only repaints the thread. */
	private String runKey;
	/** What the records page was built from, so it is only rebuilt when the history changes. */
	private Archive recordsFor;
	private int recordsBucket = -1;

	public ArchivePanel()
	{
		super(false);
		setLayout(new BorderLayout());
		setBackground(Theme.INK);

		JPanel north = new JPanel(new BorderLayout());
		north.setOpaque(false);
		north.add(crest, BorderLayout.NORTH);
		JPanel tabHolder = new JPanel(new BorderLayout());
		tabHolder.setOpaque(false);
		tabHolder.setBorder(new EmptyBorder(8, 8, 2, 8));
		tabHolder.add(tabs, BorderLayout.CENTER);
		north.add(tabHolder, BorderLayout.SOUTH);
		add(north, BorderLayout.NORTH);

		content.setOpaque(false);
		content.add(runPage, TABS[0]);
		content.add(recordsPage, TABS[1]);
		content.add(spoilsPage, TABS[2]);
		add(content, BorderLayout.CENTER);

		tabs.onChange(this::showTab);
		party.setSelected(2, false);
		party.onChange(i ->
		{
			partyChosen = true;
			rebuildRecords(true);
		});
		rebuild();
	}

	private static JScrollPane scroller()
	{
		JScrollPane scroll = new JScrollPane();
		scroll.setBorder(null);
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
		return scroll;
	}

	/** What the panel asks of the plugin when the player strikes a run or a purple off. */
	public void onDelete(LongConsumer run, BiConsumer<Long, String> drop)
	{
		onDeleteRun = run;
		onDeleteDrop = drop;
	}

	/** What the panel asks of the plugin when the player notes a role or a note on a run. */
	public void onNotes(BiConsumer<Long, String> role, BiConsumer<Long, String> note)
	{
		onRole = role;
		onNote = note;
	}

	/** What the panel asks of the plugin to save the history as a spreadsheet. */
	/** Called with a party-size group and its new goal in ticks, 0 to clear it. */
	public void onGoal(BiConsumer<Integer, Integer> goal)
	{
		onGoal = goal;
	}

	public void onExport(Runnable export)
	{
		onExport = export;
	}

	/** The copy of the account's archive the panel shows, read on the Swing thread; or null. */
	public Archive archive()
	{
		return view.archive;
	}

	/** Called on the Swing thread with each new moment of the plugin's state. */
	public void update(View view)
	{
		boolean historyChanged = this.view.archive != view.archive;
		this.view = view;
		if (!partyChosen && view.archive != null)
		{
			party.setSelected(view.live != null ? Records.bucket(view.live.getTeamSize()) : Records.favouriteBucket(view.archive.getRuns()), false);
		}
		updateCrest();
		rebuildRun();
		// Also catches the party size moving to the live run's, which changes what the page holds.
		rebuildRecords(false);
		if (historyChanged)
		{
			rebuildSpoils();
		}
	}

	/** Opens one tab, for previews and tests. */
	public void showTab(int index)
	{
		tabs.setSelected(index, false);
		cards.show(content, TABS[index]);
	}

	/** Picks a party size on the records page, for previews and tests. */
	public void showParty(int bucket)
	{
		partyChosen = true;
		party.setSelected(bucket, false);
		rebuildRecords(true);
	}

	private void rebuild()
	{
		updateCrest();
		rebuildRun();
		rebuildRecords(true);
		rebuildSpoils();
	}

	private List<Run> runs()
	{
		return view.archive == null ? java.util.Collections.emptyList() : view.archive.getRuns();
	}

	private void updateCrest()
	{
		String heading = view.player != null ? view.player + "  -  ARCHIVE" : "FRACTURED ARCHIVE";
		if (view.archive == null)
		{
			crest.update(heading, "", null, false, "Log in to open the archive", null, null, null);
			return;
		}
		Archive a = view.archive;
		Run live = view.live;
		if (live != null)
		{
			int bucket = Records.bucket(live.getTeamSize());
			int row = live.reached();
			Split split = row < Encounter.ROWS ? live.split(row) : null;
			Comparison cmp = view.comparison;
			Integer delta = row > 0 && cmp != null ? cmp.delta(live, lastTimed(live, row - 1)) : null;
			String room = split == null ? (row >= Encounter.ROWS ? "Cleared" : "Walking") : split.getEncounter().getShortName();
			int possible = Comparison.bestPossible(live, view.bestRooms, live.getTicks());
			crest.update(heading, Format.clock(live.getTicks()), Records.BUCKETS[bucket].toLowerCase(), true, null,
				new String[]{"Room", view.measuredAgainst().getShortLabel(), "Can hit"},
				new String[]{room, delta == null ? "-" : Format.delta(delta), possible < 0 ? "-" : Format.clock(possible)},
				new Color[]{Theme.mix(Theme.TEAL, Theme.TEXT, 0.4), delta == null ? Theme.TEXT_FAINT : Theme.delta(delta), Theme.GOLD});
			return;
		}
		int bucket = party.getSelected();
		Run best = Records.best(a.getRuns(), bucket);
		String status = a.getRuns().isEmpty() ? (view.inLobby ? "In the lobby" : "Enter the archive to begin") : null;
		crest.update(heading, best == null ? "-" : Format.ticks(best.getTicks()),
			best == null ? null : Records.BUCKETS[bucket].toLowerCase(), false, status,
			new String[]{"KC", "Dry", "Purples"},
			new String[]{Format.count(Math.max(a.getKc(), totalClears(a))),
				String.valueOf(a.getDry()), String.valueOf(a.getDrops().size())},
			new Color[]{Theme.TEXT, a.getDry() >= 30 ? Theme.EMBER : Theme.TEXT, a.getDrops().isEmpty() ? Theme.TEXT_FAINT : Theme.PURPLE});
	}

	private void rebuildRun()
	{
		String key = runKey();
		if (key.equals(runKey) && view.live != null)
		{
			// Mid-run the page keeps its shape; only the thread, the clocks and the bars move.
			refreshLive();
			return;
		}
		runKey = key;
		StackPanel stack = new StackPanel(Theme.GUTTER, Theme.GUTTER);
		if (view.archive == null)
		{
			stack.add(note("The archive is closed", "Log in and Codex Archive Splits times every trip into the Fractured Archive: each room, each fight and the whole run, against your best for the same party size."));
			setPage(runPage, stack);
			return;
		}
		if (view.inLobby && view.checksPack())
		{
			stack.add(new SectionHeader("Before you enter", "no bank inside"));
			packCard.update(view.pack, view.targets);
			stack.add(packCard);
		}
		else if (view.inLobby)
		{
			stack.add(note("In the lobby", "Set how much food and how many doses you want to carry in, under Pack check in the plugin's settings, and this page checks your pack before the doors close."));
		}
		Run run = view.live != null ? view.live : view.archive.last();
		if (run == null)
		{
			stack.add(note("No runs yet", "Enter the archive and your first run is timed from the moment the doors close. Each boss gets a split as it falls; the Guardian and the hooded figure always, and whichever door you take in the three rows between."));
			setPage(runPage, stack);
			return;
		}
		boolean live = view.live != null;
		int bucket = Records.bucket(run.getTeamSize());
		// Against itself every delta is nothing; a finished run is measured against the runs before it.
		Run best = bestExcept(runs(), bucket, run);
		Comparison against = live ? view.comparison : Comparison.of(view.archive, bucket, view.compare, run);
		String trailing = (live ? "live, " + Records.BUCKETS[bucket].toLowerCase() : Format.ago(view.now - run.getStartedAt()))
			+ ", vs " + (against == null ? view.compare : against.getMode()).getShortLabel();
		stack.add(new SectionHeader(live ? "This run" : "Last run", trailing));
		if (live)
		{
			table.update(run, view.comparison, view.bestRooms, run.getTicks(), true);
		}
		else
		{
			table.update(run, against, View.bestRooms(view.archive, run), run.getTicks(), false);
		}
		stack.add(table);

		if (!live)
		{
			stack.add(summary(run, best));
			String route = routeLine(run, bucket);
			if (route != null)
			{
				stack.add(Ui.wrapped(route, Theme.small(), Theme.TEXT_MUTED, WIDTH - 16));
			}
		}
		Map<Supply, Integer> used = used(run);
		if (!used.isEmpty())
		{
			usedCard.update(used, null);
			stack.add(usedCard);
		}
		Sessions.Session session = live ? null : Sessions.current(runs(), view.now);
		if (session != null && session.getRuns().size() > 1)
		{
			session(stack, session);
		}
		setPage(runPage, stack);
	}

	/** Everything that decides the run page's shape, so it is only rebuilt when that changes. */
	private String runKey()
	{
		Run run = view.live != null ? view.live : view.archive == null ? null : view.archive.last();
		return System.identityHashCode(view.archive) + "|" + view.compare + "|" + (view.live != null) + "|" + view.inLobby + "|" + view.checksPack()
			+ "|" + (run == null ? "" : run.getStartedAt() + ":" + run.getTeamSize() + ":" + used(run).isEmpty())
			+ "|" + (view.live == null ? view.now / 60_000 : 0);
	}

	/** One tick of a live run on an unchanged page. */
	private void refreshLive()
	{
		Run run = view.live;
		table.update(run, view.comparison, view.bestRooms, run.getTicks(), true);
		Map<Supply, Integer> used = used(run);
		if (!used.isEmpty())
		{
			usedCard.update(used, null);
		}
	}

	private static Map<Supply, Integer> used(Run run)
	{
		Map<Supply, Integer> used = new EnumMap<>(Supply.class);
		for (Map.Entry<String, Integer> e : run.getUsed().entrySet())
		{
			try
			{
				used.put(Supply.valueOf(e.getKey()), e.getValue());
			}
			catch (IllegalArgumentException ignored)
			{
				// A kind of supply a later version no longer counts.
			}
		}
		return used;
	}

	/** The furthest row at or before this one whose boss has a time, so a missed row still leaves a delta. */
	private static int lastTimed(Run run, int row)
	{
		for (int r = row; r >= 0; r--)
		{
			Split s = run.split(r);
			if (s != null && s.isDone())
			{
				return r;
			}
		}
		return row;
	}

	private static Run bestExcept(List<Run> runs, int bucket, Run except)
	{
		Run best = null;
		for (Run r : runs)
		{
			if (r != except && r.isCompleted() && Records.bucket(r.getTeamSize()) == bucket && (best == null || r.getTicks() < best.getTicks()))
			{
				best = r;
			}
		}
		return best;
	}

	/** Three tablets under a finished run: its time, the best before it and the deaths. */
	private JComponent summary(Run run, Run previousBest)
	{
		JPanel grid = new JPanel(new GridLayout(1, 3, 6, 0));
		grid.setOpaque(false);
		if (run.isCompleted())
		{
			boolean pb = previousBest == null || run.getTicks() < previousBest.getTicks();
			grid.add(new StatTile(Format.ticks(run.getTicks()), "Time", pb ? "new best" : "cleared", pb ? Theme.GOLD_BRIGHT : Theme.TEXT));
		}
		else
		{
			grid.add(new StatTile(run.getOutcome() == Outcome.WIPED ? "Wiped" : "Left", "Ended", "at " + Format.ticks(run.getTicks()),
				run.getOutcome() == Outcome.WIPED ? Theme.BLOOD : Theme.TEXT_MUTED));
		}
		if (previousBest != null && run.isCompleted())
		{
			int d = run.getTicks() - previousBest.getTicks();
			grid.add(new StatTile(Format.delta(d), "On best", Format.ticks(previousBest.getTicks()), Theme.delta(d)));
		}
		else
		{
			grid.add(new StatTile(run.getKc() > 0 ? Format.count(run.getKc()) : "-", "KC", "in all", Theme.TEXT));
		}
		grid.add(new StatTile(String.valueOf(run.getDeaths()), "Deaths", Records.BUCKETS[Records.bucket(run.getTeamSize())].toLowerCase(),
			run.getDeaths() == 0 ? Theme.GREEN : Theme.EMBER));
		return grid;
	}

	/** How a clear stands on its own route of doors, or null when that cannot be said. */
	private String routeLine(Run run, int bucket)
	{
		List<Encounter> doors = Insights.doors(run);
		if (!run.isCompleted() || doors == null)
		{
			return null;
		}
		int[] before = Insights.routeBest(runs(), bucket, run);
		String name = doors.get(0).getShortName() + ", " + doors.get(1).getShortName() + ", " + doors.get(2).getShortName();
		if (before[0] < 0)
		{
			return "First clear through " + name + ".";
		}
		int d = run.getTicks() - before[0];
		if (d == 0)
		{
			return "Level with your best through " + name + ".";
		}
		return d < 0 ? "Fastest yet through " + name + ", " + Format.span(-d) + " under the " + before[1] + (before[1] == 1 ? " clear" : " clears") + " before."
			: Format.span(d) + " off your best through " + name + " (" + Format.ticks(before[0]) + ").";
	}

	/** Newest personal bests listed, routes listed, and bosses listed under where runs wipe. */
	private static final int LADDER = 5;
	private static final int ROUTES = 4;
	private static final int WALLS = 3;

	private void rebuildRecords(boolean force)
	{
		int bucket = party.getSelected();
		if (!force && recordsFor == view.archive && recordsBucket == bucket)
		{
			return;
		}
		recordsFor = view.archive;
		recordsBucket = bucket;
		StackPanel stack = new StackPanel(Theme.GUTTER, Theme.GUTTER);
		if (view.archive == null)
		{
			stack.add(note("The archive is closed", "Log in to see your personal bests."));
			setPage(recordsPage, stack);
			return;
		}
		stack.add(party);
		List<Run> runs = runs();
		Run best = Records.best(runs, bucket);
		int sob = Records.sumOfBest(runs, bucket);
		int avg = Records.recentAverage(runs, bucket, 10);
		int done = 0;
		int wiped = 0;
		int left = 0;
		for (Run r : runs)
		{
			if (Records.bucket(r.getTeamSize()) == bucket)
			{
				done += r.isCompleted() ? 1 : 0;
				wiped += r.getOutcome() == Outcome.WIPED ? 1 : 0;
				left += r.getOutcome() == Outcome.LEFT ? 1 : 0;
			}
		}
		JPanel grid = new JPanel(new GridLayout(2, 2, 6, 6));
		grid.setOpaque(false);
		grid.add(new StatTile(best == null ? "-" : Format.ticks(best.getTicks()), "Best", best == null ? "no clear yet" : Format.shortDay(best.getStartedAt()), Theme.GOLD_BRIGHT));
		grid.add(new StatTile(sob < 0 ? "-" : Format.ticks(sob), "Sum of best", sob < 0 || best == null ? "every room" : Format.delta(sob - best.getTicks()) + " on best", Theme.TEAL));
		grid.add(new StatTile(avg < 0 ? "-" : Format.ticks(avg), "Average", "last 10 clears", Theme.TEXT));
		int allClears = view.archive.clears(bucket);
		int allWipes = view.archive.wipes(bucket);
		grid.add(new StatTile(Format.count(allClears), "Clears", allWipes == 0 ? "no wipes" : Format.count(allWipes) + (allWipes == 1 ? " wipe" : " wipes"), Theme.TEXT));
		stack.add(grid);
		goal(stack, runs, bucket);

		stack.add(new SectionHeader("Best rooms", "and a typical one"));
		stack.add(rooms(runs, bucket));

		stack.add(new SectionHeader("Best fights", "first hit to fall"));
		Card fights = new Card(new GridLayout(0, 1, 0, 2));
		for (Encounter e : Encounter.values())
		{
			int t = Records.bestFight(runs, bucket, e);
			fights.add(line(e.getTitle(), t < 0 ? "-" : Format.ticks(t), t < 0 ? Theme.TEXT_FAINT : Theme.TEXT));
		}
		stack.add(fights);

		List<Integer> trend = Insights.trend(runs, bucket);
		if (trend.size() >= 3)
		{
			stack.add(new SectionHeader("Steadiness", "last " + trend.size() + " clears"));
			stack.add(new TrendChart(trend, best == null ? -1 : best.getTicks(), Stats.median(trend)));
			JPanel steady = new JPanel(new GridLayout(1, 3, 6, 0));
			steady.setOpaque(false);
			int spread = Stats.deviation(trend);
			steady.add(new StatTile("±" + Format.span(spread), "Spread", "typical", Theme.TEXT));
			int pbs = Insights.personalBests(runs, bucket);
			steady.add(new StatTile(String.valueOf(pbs), "Bests", pbs == 1 ? "set once" : "times set", Theme.GOLD));
			int attempts = allClears + allWipes;
			// Rounded down once there is a wipe, so 199 clears and a wipe never read as 100%.
			long rate = attempts == 0 ? 0 : allWipes > 0 ? 100L * allClears / attempts : 100;
			steady.add(new StatTile(attempts == 0 ? "-" : rate + "%", "Clears", "of tries",
				allWipes == 0 ? Theme.GREEN : Theme.TEXT));
			stack.add(steady);
		}

		List<Insights.Best> ladder = Insights.ladder(runs, bucket);
		if (ladder.size() >= 2)
		{
			Insights.Best first = ladder.get(0);
			Insights.Best last = ladder.get(ladder.size() - 1);
			stack.add(new SectionHeader("Your best, over time", Format.span(first.ticks - last.ticks) + " off"));
			Card card = new Card(new GridLayout(0, 1, 0, 4));
			for (int i = ladder.size() - 1; i >= Math.max(0, ladder.size() - LADDER); i--)
			{
				Insights.Best b = ladder.get(i);
				card.add(twoLine(Format.day(b.at), b.saved < 0 ? "first clear" : Format.span(b.saved) + " off the one before",
					Format.ticks(b.ticks), i == ladder.size() - 1 ? "best now" : "", i == ladder.size() - 1 ? Theme.GOLD_BRIGHT : Theme.TEXT));
			}
			if (ladder.size() > LADDER)
			{
				card.add(Ui.label((ladder.size() - LADDER) + " more before, from " + Format.ticks(first.ticks),
					Theme.small(), Theme.TEXT_FAINT));
			}
			stack.add(card);
		}

		List<Insights.DoorStat[]> doors = Insights.doors(runs, bucket);
		if (!doors.isEmpty())
		{
			stack.add(new SectionHeader("Doors", "typical room"));
			Card card = new Card(new GridLayout(0, 1, 0, 8));
			for (Insights.DoorStat[] pair : doors)
			{
				card.add(doorLine(pair));
			}
			stack.add(card);
		}

		List<Insights.RouteStat> routes = Insights.routes(runs, bucket);
		if (routes.size() >= 2)
		{
			stack.add(new SectionHeader("Routes", "best clear"));
			Card card = new Card(new GridLayout(0, 1, 0, 4));
			for (int i = 0; i < Math.min(ROUTES, routes.size()); i++)
			{
				Insights.RouteStat r = routes.get(i);
				JComponent row = twoLine(r.name(), Theme.small(), r.clears + (r.clears == 1 ? " clear" : " clears") + (r.clears >= 3 ? ", typical " + Format.ticks(r.typical) : ""),
					Format.ticks(r.best), i == 0 ? "fastest" : "+" + Format.span(r.best - routes.get(0).best), i == 0 ? Theme.GOLD_BRIGHT : Theme.TEXT);
				row.setToolTipText("<html>" + r.name() + "<br>Best clear " + Format.ticks(r.best) + (r.clears >= 2 ? ", middle of " + r.clears + " clears " + Format.ticks(r.typical) : "") + "</html>");
				card.add(row);
			}
			if (routes.size() > ROUTES)
			{
				card.add(Ui.label("and " + (routes.size() - ROUTES) + " slower " + (routes.size() - ROUTES == 1 ? "route" : "routes"), Theme.small(), Theme.TEXT_FAINT));
			}
			stack.add(card);
		}

		List<Insights.Wall> walls = Insights.walls(runs, bucket);
		if (!walls.isEmpty())
		{
			stack.add(new SectionHeader("Where runs wipe", null));
			Card card = new Card(new GridLayout(0, 1, 0, 4));
			for (int i = 0; i < Math.min(WALLS, walls.size()); i++)
			{
				Insights.Wall w = walls.get(i);
				long share = w.tries == 0 ? 0 : Math.round(100.0 * w.wipes / w.tries);
				card.add(twoLine(w.encounter.getTitle(), w.wipes + (w.wipes == 1 ? " wipe" : " wipes") + " in " + w.tries + (w.tries == 1 ? " try" : " tries"),
					share + "%", "of tries", i == 0 ? Theme.BEHIND : Theme.TEXT));
			}
			stack.add(card);
		}

		int inBucket = done + wiped + left;
		String party = Records.BUCKETS[bucket].toLowerCase();
		stack.add(new SectionHeader("Recent " + party + " runs", inBucket > HISTORY ? "latest " + HISTORY : null));
		Card history = new Card(new GridLayout(0, 1, 0, 0));
		history.setBorder(new EmptyBorder(2, 4, 2, 4));
		int shown = 0;
		for (int i = runs.size() - 1; i >= 0 && shown < HISTORY; i--)
		{
			Run r = runs.get(i);
			if (Records.bucket(r.getTeamSize()) != bucket)
			{
				continue;
			}
			RunRow row = new RunRow(r, r == best, best == null ? -1 : best.getTicks(), view.now);
			row.setComponentPopupMenu(runMenu(r, bucket));
			history.add(row);
			shown++;
		}
		if (shown == 0)
		{
			history.setBorder(new EmptyBorder(8, 10, 8, 10));
			history.add(Ui.wrapped(runs.isEmpty() ? "Your runs appear here as you finish them."
				: "No " + party + " runs yet. Pick another party size above.", Theme.small(), Theme.TEXT_FAINT, WIDTH - 24));
		}
		stack.add(history);
		if (shown > 0)
		{
			stack.add(Ui.wrapped("Right-click a run to note your role, write a note, copy its splits or delete one the plugin timed wrongly.",
				Theme.small(), Theme.TEXT_FAINT, WIDTH - 16));
		}

		// Only this party size's runs count, and a sitting without one is not listed here.
		List<Sessions.Session> sittings = Sessions.of(runs, bucket);
		if (sittings.size() > 1)
		{
			stack.add(new SectionHeader("Sessions", sittings.size() > SESSIONS ? "latest " + SESSIONS : null));
			Card card = new Card(new GridLayout(0, 1, 0, 4));
			for (int i = sittings.size() - 1; i >= Math.max(0, sittings.size() - SESSIONS); i--)
			{
				Sessions.Session t = sittings.get(i);
				String pace = t.perHour() > 0 ? String.format(java.util.Locale.ENGLISH, ", %.1f an hour", t.perHour()) : "";
				String sub = t.getBest() < 0 ? "no clear" : t.getBests() == 1 ? "new best" : t.getBests() > 1 ? t.getBests() + " new bests"
					: "best of " + t.getClears();
				card.add(twoLine(Format.day(t.getStart()), t.getCounted().size() + (t.getCounted().size() == 1 ? " run" : " runs") + pace,
					t.getBest() < 0 ? "-" : Format.ticks(t.getBest()), sub,
					t.getBests() > 0 ? Theme.GOLD_BRIGHT : Theme.TEXT));
			}
			stack.add(card);
		}

		List<Insights.Mate> crew = Insights.crew(runs, bucket);
		if (!crew.isEmpty())
		{
			stack.add(new SectionHeader("Crew", crew.size() > CREW ? "top " + CREW : null));
			Card card = new Card(new GridLayout(0, 1, 0, 4));
			for (int i = 0; i < Math.min(CREW, crew.size()); i++)
			{
				Insights.Mate m = crew.get(i);
				card.add(twoLine(m.name, m.runs + (m.runs == 1 ? " run" : " runs") + ", " + m.clears + (m.clears == 1 ? " clear" : " clears"),
					m.best < 0 ? "-" : Format.ticks(m.best), m.best < 0 ? "no clear" : "best together", Theme.TEXT));
			}
			stack.add(card);
		}

		if (!runs.isEmpty())
		{
			JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
			buttons.setOpaque(false);
			buttons.add(new CodexButton("Save runs as a spreadsheet", false, onExport));
			stack.add(buttons);
		}
		setPage(recordsPage, stack);
	}

	private void rebuildSpoils()
	{
		StackPanel stack = new StackPanel(Theme.GUTTER, Theme.GUTTER);
		Archive a = view.archive;
		if (a == null)
		{
			stack.add(note("The archive is closed", "Log in to see your purples."));
			setPage(spoilsPage, stack);
			return;
		}
		// The crest already gives the KC; these say how the luck has gone.
		JPanel grid = new JPanel(new GridLayout(1, 3, 6, 0));
		grid.setOpaque(false);
		int clears = totalClears(a);
		int longest = a.longestDry();
		grid.add(new StatTile(String.valueOf(a.getDry()), "Dry", longest > a.getDry() ? "max " + longest : "clears",
			a.getDry() > 0 && a.getDry() >= longest && !a.getDrops().isEmpty() ? Theme.EMBER : Theme.TEXT));
		int purples = a.getDrops().size();
		String rate = purples == 0 ? "none yet" : clears >= purples ? "1 in " + Math.round(clears / (double) purples) : "rate unknown";
		grid.add(new StatTile(String.valueOf(purples), "Purples", rate, purples == 0 ? Theme.TEXT_FAINT : Theme.PURPLE));
		Map<String, Integer> counts = new HashMap<>();
		for (Drop d : a.getDrops())
		{
			counts.merge(d.getUnique(), 1, Integer::sum);
		}
		int owned = 0;
		for (String u : Spoils.ALL)
		{
			owned += counts.containsKey(u) ? 1 : 0;
		}
		grid.add(new StatTile(owned + "/" + Spoils.ALL.length, "Log", owned == Spoils.ALL.length ? "complete" : "uniques",
			owned == Spoils.ALL.length ? Theme.GOLD_BRIGHT : owned == 0 ? Theme.TEXT_FAINT : Theme.TEXT));
		stack.add(grid);

		stack.add(new SectionHeader("Uniques"));
		JPanel uniques = new JPanel(new GridLayout(0, 3, 6, 6));
		uniques.setOpaque(false);
		for (String u : Spoils.ALL)
		{
			uniques.add(new UniqueTile(u, counts.getOrDefault(u, 0)));
		}
		stack.add(uniques);

		stack.add(new SectionHeader("Purple log"));
		Card log = new Card(new GridLayout(0, 1, 0, 4));
		List<Drop> drops = a.getDrops();
		for (int i = drops.size() - 1; i >= 0 && i >= drops.size() - DROPS; i--)
		{
			Drop d = drops.get(i);
			String when = (d.getKc() > 0 ? "KC " + Format.count(d.getKc()) + ", " : "") + Format.shortDay(d.getAt());
			String after = d.getDry() <= 1 ? "first clear" : "after " + d.getDry() + " clears";
			JComponent line = dropLine(d.getUnique(), when, after);
			line.setComponentPopupMenu(menu("Remove this purple",
				"Remove the " + d.getUnique() + " from " + Format.day(d.getAt()) + "? Only do this if it never dropped; the clears it took go back onto your dry streak.",
				() -> onDeleteDrop.accept(d.getAt(), d.getUnique())));
			Ui.inheritPopup(line);
			log.add(line);
		}
		if (drops.isEmpty())
		{
			log.add(Ui.wrapped("No purples yet. Each one lands here with the completion it came on and how long it took.", Theme.small(), Theme.TEXT_FAINT, WIDTH - 24));
		}
		stack.add(log);
		if (!drops.isEmpty())
		{
			stack.add(Ui.wrapped("Right-click a purple that never dropped to remove it.", Theme.small(), Theme.TEXT_FAINT, WIDTH - 16));
		}
		setPage(spoilsPage, stack);
	}

	/** The sitting the last run belongs to: clears, pace, average, time between runs, and any best or purple. */
	private void session(StackPanel stack, Sessions.Session t)
	{
		stack.add(new SectionHeader("This session", "since " + Format.time(t.getStart())));
		JPanel grid = new JPanel(new GridLayout(2, 2, 6, 6));
		grid.setOpaque(false);
		grid.add(new StatTile(String.valueOf(t.getClears()), "Clears", t.getWipes() == 0 ? "no wipes" : t.getWipes() + (t.getWipes() == 1 ? " wipe" : " wipes"),
			Theme.TEXT));
		grid.add(new StatTile(t.perHour() > 0 ? String.format(java.util.Locale.ENGLISH, "%.1f", t.perHour()) : "-", "An hour", "clears", Theme.TEAL));
		grid.add(new StatTile(t.getAverage() < 0 ? "-" : Format.ticks(t.getAverage()), "Average",
			t.getBest() < 0 ? "no clear yet" : "best " + Format.ticks(t.getBest()), Theme.TEXT));
		grid.add(new StatTile(t.getBetween() < 0 ? "-" : Format.clock((int) (t.getBetween() / 600)), "Downtime", "between runs", Theme.TEXT));
		stack.add(grid);
		java.util.List<String> parts = new java.util.ArrayList<>();
		if (t.getBests() > 0)
		{
			parts.add(t.getBests() == 1 ? "a new personal best" : t.getBests() + " new personal bests");
		}
		if (t.getPurples() > 0)
		{
			parts.add(t.getPurples() == 1 ? "a purple" : t.getPurples() + " purples");
		}
		if (!parts.isEmpty())
		{
			stack.add(Ui.wrapped("This session: " + String.join(" and ", parts) + ".", Theme.small(),
				t.getPurples() > 0 ? Theme.PURPLE : Theme.GOLD, WIDTH - 16));
		}
	}

	/**
	 * The goal for a party size: how far the best is from it, how often it has been beaten, and the
	 * rooms where a typical run loses most against the goal's share. With none set, a way to set one.
	 */
	private void goal(StackPanel stack, List<Run> runs, int bucket)
	{
		int ticks = view.archive.goal(bucket);
		Goal g = Goal.of(runs, bucket, ticks);
		String party = Records.BUCKETS[bucket].toLowerCase();
		JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
		buttons.setOpaque(false);
		buttons.add(new CodexButton(g == null ? "Set a goal time" : "Change goal", g == null, () -> askGoal(bucket, ticks)));
		if (g == null)
		{
			Card card = new Card(new BorderLayout(0, 6));
			card.add(Ui.wrapped("Set the time you are working towards for " + party + " runs, and see how far off it is and which rooms hold you back. "
				+ "Choose Goal time under Compare against in the settings to race it room by room.", Theme.small(), Theme.TEXT_MUTED, WIDTH - 30),
				BorderLayout.CENTER);
			card.add(buttons, BorderLayout.SOUTH);
			stack.add(card);
			return;
		}
		buttons.add(new CodexButton("Clear", false, () -> onGoal.accept(bucket, 0)));
		stack.add(new SectionHeader("Goal", Format.clock(g.getGoal()) + " " + party));
		Card card = new Card(new BorderLayout(0, 6));
		card.accent(g.getBeaten() > 0 ? Theme.GREEN : Theme.GOLD);
		JPanel lines = new JPanel(new GridLayout(0, 1, 0, 3));
		lines.setOpaque(false);
		if (g.getBest() < 0)
		{
			lines.add(line("Your best", "no clear yet", Theme.TEXT_FAINT));
		}
		else if (g.gap() > 0)
		{
			lines.add(line("Your best", Format.ticks(g.getBest()), Theme.TEXT));
			lines.add(line("Still to find", Format.span(g.gap()), Theme.EMBER));
		}
		else
		{
			lines.add(line("Your best", Format.ticks(g.getBest()), Theme.GREEN));
			lines.add(line("Beaten", g.getBeaten() + (g.getBeaten() == 1 ? " time" : " times"), Theme.GREEN));
			if (g.getFirst() != null)
			{
				lines.add(line("First on", Format.shortDay(g.getFirst().getStartedAt()), Theme.TEXT));
			}
		}
		int shown = 0;
		for (Goal.Row r : g.getRows())
		{
			if (r.over() <= 0 || shown >= 3)
			{
				continue;
			}
			if (shown++ == 0)
			{
				lines.add(Ui.label("Where a typical run loses it", Theme.small(), Theme.GOLD_DIM));
			}
			JComponent l = line(Encounter.rowLabel(r.row), "+" + Format.span(r.over()), Theme.EMBER);
			l.setToolTipText("A typical room here takes " + Format.ticks(r.typical) + "; the goal allows " + Format.ticks(r.allowed) + ".");
			lines.add(l);
		}
		card.add(lines, BorderLayout.CENTER);
		card.add(buttons, BorderLayout.SOUTH);
		stack.add(card);
	}

	/** Asks for a goal time, and passes on what was typed once it reads as a time. */
	private void askGoal(int bucket, int current)
	{
		String party = Records.BUCKETS[bucket].toLowerCase();
		Object answer = JOptionPane.showInputDialog(this, "Goal time for " + party + " runs, in minutes and seconds (for example 20:00):",
			"Goal time", JOptionPane.PLAIN_MESSAGE, null, null, current > 0 ? Format.clock(current) : "");
		if (answer == null)
		{
			return;
		}
		int ticks = Goal.parse(answer.toString());
		if (ticks <= 0)
		{
			JOptionPane.showMessageDialog(this, "\"" + answer + "\" is not a time. Write it like 20:00 or 19:42.6.", "Goal time",
				JOptionPane.WARNING_MESSAGE);
			return;
		}
		onGoal.accept(bucket, ticks);
	}

	/** Each row's best room with a bar for what a typical run lately gives away, the worst row lit. */
	private static JComponent rooms(List<Run> runs, int bucket)
	{
		Map<Integer, Insights.RowStat> stats = new HashMap<>();
		int scale = 0;
		int worst = -1;
		for (Insights.RowStat r : Insights.rows(runs, bucket))
		{
			stats.put(r.row, r);
			if (r.loss() > scale)
			{
				scale = r.loss();
				worst = r.row;
			}
		}
		Card card = new Card(new BorderLayout());
		JPanel lines = new JPanel();
		lines.setOpaque(false);
		lines.setLayout(new javax.swing.BoxLayout(lines, javax.swing.BoxLayout.Y_AXIS));
		for (int row = 0; row < Encounter.ROWS; row++)
		{
			Insights.RowStat stat = stats.get(row);
			if (row > 0)
			{
				lines.add(javax.swing.Box.createVerticalStrut(5));
			}
			lines.add(new RoomLine(Encounter.rowLabel(row), Records.bestRoom(runs, bucket, row), stat == null ? -1 : stat.typical,
				scale, row == worst && scale > 0));
		}
		card.add(lines, BorderLayout.CENTER);
		return card;
	}

	/** Both doors of a row, one line each with its typical room, and which is quicker for you. */
	private static JComponent doorLine(Insights.DoorStat[] pair)
	{
		Insights.DoorStat a = pair[0];
		Insights.DoorStat b = pair[1];
		int gap = a.typical - b.typical;
		Insights.DoorStat quick = gap > 0 ? b : a;
		JPanel block = new JPanel(new GridLayout(0, 1, 0, 1));
		block.setOpaque(false);
		for (Insights.DoorStat d : pair)
		{
			JPanel line = new JPanel(new BorderLayout(6, 0));
			line.setOpaque(false);
			line.add(Ui.label(d.encounter.getTitle(), Theme.small(), Theme.TEXT_MUTED), BorderLayout.CENTER);
			JLabel time = Ui.label(d.runs + "x  " + Format.ticks(d.typical), Theme.small(),
				d == quick && gap != 0 ? Theme.GREEN : Theme.TEXT);
			line.add(time, BorderLayout.EAST);
			block.add(line);
		}
		block.add(Ui.label(gap == 0 ? "Level either way" : quick.encounter.getShortName() + " is " + Format.span(gap) + " quicker for you",
			Theme.small(), gap == 0 ? Theme.TEXT_FAINT : Theme.GOLD));
		block.setToolTipText("Typical room through each door in this party size, from " + a.runs + " and " + b.runs + " runs.");
		return block;
	}

	private static JComponent twoLine(String title, String sub, String value, String valueSub, Color color)
	{
		return twoLine(title, Theme.body(), sub, value, valueSub, color);
	}

	private static JComponent twoLine(String title, java.awt.Font titleFont, String sub, String value, String valueSub, Color color)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		JPanel left = new JPanel(new GridLayout(2, 1));
		left.setOpaque(false);
		left.add(Ui.label(title, titleFont, Theme.TEXT));
		left.add(Ui.label(sub, Theme.small(), Theme.TEXT_FAINT));
		row.add(left, BorderLayout.CENTER);
		JPanel right = new JPanel(new GridLayout(2, 1));
		right.setOpaque(false);
		JLabel v = Ui.label(value, Theme.body(), color);
		v.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
		JLabel vs = Ui.label(valueSub, Theme.small(), Theme.TEXT_FAINT);
		vs.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
		right.add(v);
		right.add(vs);
		row.add(right, BorderLayout.EAST);
		return row;
	}

	/** Everything a run can have done to it: a role, a note, its splits to share, or deleting it. */
	private JPopupMenu runMenu(Run r, int bucket)
	{
		JPopupMenu popup = new JPopupMenu();
		JMenu role = new JMenu("Role");
		for (String name : Run.ROLES)
		{
			JCheckBoxMenuItem item = new JCheckBoxMenuItem(name, name.equals(r.getRole()));
			item.addActionListener(e -> onRole.accept(r.getStartedAt(), name.equals(r.getRole()) ? null : name));
			role.add(item);
		}
		popup.add(role);
		JMenuItem note = new JMenuItem(r.getNote() == null ? "Write a note..." : "Edit the note...");
		note.addActionListener(e ->
		{
			Object answer = JOptionPane.showInputDialog(this, "A note on this run, up to " + Run.NOTE_LENGTH + " characters:",
				"Note", JOptionPane.PLAIN_MESSAGE, null, null, r.getNote() == null ? "" : r.getNote());
			if (answer != null)
			{
				onNote.accept(r.getStartedAt(), answer.toString());
			}
		});
		popup.add(note);
		JMenuItem copy = new JMenuItem("Copy splits");
		copy.addActionListener(e ->
		{
			Comparison pb = Comparison.of(runs(), bucket, Comparison.Mode.PERSONAL_BEST, r);
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(Share.splits(r, pb)), null);
		});
		popup.add(copy);
		popup.addSeparator();
		JPopupMenu delete = menu("Delete this run",
			"Delete the " + describe(r) + " from " + Format.day(r.getStartedAt()) + " at " + Format.time(r.getStartedAt())
				+ "? Its times leave your records for good.",
			() -> onDeleteRun.accept(r.getStartedAt()));
		popup.add(delete.getComponent(0));
		return popup;
	}

	private static int totalClears(Archive a)
	{
		int n = 0;
		for (int b = 0; b < Records.BUCKETS.length; b++)
		{
			n += a.clears(b);
		}
		return n;
	}

	private static String describe(Run r)
	{
		String party = Records.BUCKETS[Records.bucket(r.getTeamSize())].toLowerCase();
		if (r.isCompleted())
		{
			return Format.ticks(r.getTicks()) + " " + party + " clear";
		}
		return party + (r.getOutcome() == Outcome.WIPED ? " wipe" : " run");
	}

	/** A right-click menu with one action, asked about first since it cannot be undone. */
	private JPopupMenu menu(String label, String question, Runnable action)
	{
		JPopupMenu popup = new JPopupMenu();
		JMenuItem item = new JMenuItem(label);
		item.addActionListener(e ->
		{
			int answer = JOptionPane.showConfirmDialog(this, Ui.wrapped(question, Theme.body(), null, 240).getText(),
				label, JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
			if (answer == JOptionPane.OK_OPTION)
			{
				action.run();
			}
		});
		popup.add(item);
		return popup;
	}

	private static JComponent line(String label, String value, Color color)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		row.add(Ui.label(label, Theme.small(), Theme.TEXT_MUTED), BorderLayout.CENTER);
		row.add(Ui.label(value, Theme.body(), color), BorderLayout.EAST);
		return row;
	}

	private static JComponent dropLine(String unique, String when, String after)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		Color tone = Spoils.isMegarare(unique) ? Theme.MEGARARE : Theme.PURPLE;
		JPanel left = new JPanel(new GridLayout(2, 1));
		left.setOpaque(false);
		left.add(Ui.label(unique, Theme.body(), tone));
		left.add(Ui.label(when, Theme.small(), Theme.TEXT_FAINT));
		row.add(left, BorderLayout.CENTER);
		row.add(Ui.label(after, Theme.small(), Theme.TEXT_MUTED), BorderLayout.EAST);
		return row;
	}

	private static JComponent note(String title, String body)
	{
		Card card = new Card(new BorderLayout(0, 6));
		card.accent(Theme.GOLD);
		card.add(Ui.label(title, Theme.title(), Theme.GOLD), BorderLayout.NORTH);
		card.add(Ui.wrapped(body, Theme.small(), Theme.TEXT_MUTED, WIDTH - 30), BorderLayout.CENTER);
		return card;
	}

	private static void setPage(JScrollPane page, StackPanel stack)
	{
		int scroll = page.getVerticalScrollBar().getValue();
		page.setViewportView(stack);
		page.getVerticalScrollBar().setValue(scroll);
		page.revalidate();
		page.repaint();
	}
}
