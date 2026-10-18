package com.codex.archive.ui;

import com.codex.archive.core.Archive;
import com.codex.archive.core.Drop;
import com.codex.archive.core.Encounter;
import com.codex.archive.core.Outcome;
import com.codex.archive.core.Records;
import com.codex.archive.core.Run;
import com.codex.archive.core.Spoils;
import com.codex.archive.core.Split;
import com.codex.archive.core.Supply;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JComponent;
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
		if (historyChanged)
		{
			rebuildRecords(false);
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
			crest.update(heading, "-", null, false, "Log in to open the archive", null, null, null);
			return;
		}
		Archive a = view.archive;
		Run live = view.live;
		if (live != null)
		{
			int bucket = Records.bucket(live.getTeamSize());
			Run best = Records.best(a.getRuns(), bucket);
			int row = currentRow(live);
			Split split = row < Encounter.ROWS ? live.split(row) : null;
			Integer delta = row > 0 ? Records.delta(live, best, row - 1) : null;
			String room = split == null ? (row >= Encounter.ROWS ? "Cleared" : "Walking") : split.getEncounter().getShortName();
			crest.update(heading, Format.clock(live.getTicks()), Records.BUCKETS[bucket].toLowerCase(), true, null,
				new String[]{"Room", "On best", "Best"},
				new String[]{room, delta == null ? "-" : Format.delta(delta), best == null ? "-" : Format.clock(best.getTicks())},
				new Color[]{Theme.mix(Theme.TEAL, Theme.TEXT, 0.4), delta == null ? Theme.TEXT_FAINT : Theme.delta(delta), Theme.GOLD});
			return;
		}
		int bucket = party.getSelected();
		Run best = Records.best(a.getRuns(), bucket);
		String status = a.getRuns().isEmpty() ? (view.inLobby ? "In the lobby. Check your pack" : "Enter the archive to begin") : null;
		crest.update(heading, best == null ? "-" : Format.ticks(best.getTicks()),
			best == null ? null : Records.BUCKETS[bucket].toLowerCase(), false, status,
			new String[]{"KC", "Dry", "Purples"},
			new String[]{a.getKc() > 0 ? Format.count(a.getKc()) : String.valueOf(Records.count(a.getRuns(), Outcome.COMPLETED)),
				String.valueOf(a.getDry()), String.valueOf(a.getDrops().size())},
			new Color[]{Theme.TEXT, a.getDry() >= 30 ? Theme.EMBER : Theme.TEXT, a.getDrops().isEmpty() ? Theme.TEXT_FAINT : Theme.PURPLE});
	}

	private static int currentRow(Run run)
	{
		for (int row = 0; row < Encounter.ROWS; row++)
		{
			Split s = run.split(row);
			if (s == null || !s.isDone())
			{
				return row;
			}
		}
		return Encounter.ROWS;
	}

	private void rebuildRun()
	{
		StackPanel stack = new StackPanel(Theme.GUTTER, Theme.GUTTER);
		if (view.archive == null)
		{
			stack.add(note("The archive is closed", "Log in and Codex Archive Splits times every trip into the Fractured Archive: each room, each fight and the whole run, against your best for the same party size."));
			setPage(runPage, stack);
			return;
		}
		if (view.inLobby)
		{
			stack.add(new SectionHeader("Before you enter", "no bank inside"));
			packCard.update(view.pack, view.targets);
			stack.add(packCard);
		}
		Run run = view.live != null ? view.live : view.archive.last();
		if (run == null)
		{
			if (!view.inLobby)
			{
				stack.add(note("No runs yet", "Enter the archive and your first run is timed from the moment the doors close. Each boss gets a split as it falls; the Guardian and the hooded figure always, and whichever door you take in the three rows between."));
			}
			setPage(runPage, stack);
			return;
		}
		boolean live = view.live != null;
		int bucket = Records.bucket(run.getTeamSize());
		Run best = Records.best(runs(), bucket);
		if (!live && best == run)
		{
			// Against itself every delta is nothing; show the best before it instead, if there is one.
			best = bestExcept(runs(), bucket, run);
		}
		String trailing = live ? "live, " + Records.BUCKETS[bucket].toLowerCase()
			: Format.ago(view.now - run.getStartedAt());
		stack.add(new SectionHeader(live ? "This run" : "Last run", trailing));
		int[] bestRooms = new int[Encounter.ROWS];
		for (int row = 0; row < Encounter.ROWS; row++)
		{
			bestRooms[row] = Records.bestRoom(runs(), bucket, row);
		}
		table.update(run, best, bestRooms, run.getTicks(), live);
		stack.add(table);

		if (!live)
		{
			stack.add(summary(run, best));
		}
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
		if (!used.isEmpty())
		{
			usedCard.update(used, null);
			stack.add(usedCard);
		}
		setPage(runPage, stack);
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
			grid.add(new StatTile(run.getKc() > 0 ? Format.count(run.getKc()) : "-", "KC", "completions", Theme.TEXT));
		}
		grid.add(new StatTile(String.valueOf(run.getDeaths()), "Deaths", Records.BUCKETS[Records.bucket(run.getTeamSize())].toLowerCase(),
			run.getDeaths() == 0 ? Theme.GREEN : Theme.EMBER));
		return grid;
	}

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
		for (Run r : runs)
		{
			if (Records.bucket(r.getTeamSize()) == bucket)
			{
				done += r.isCompleted() ? 1 : 0;
				wiped += r.getOutcome() == Outcome.WIPED ? 1 : 0;
			}
		}
		JPanel grid = new JPanel(new GridLayout(2, 2, 6, 6));
		grid.setOpaque(false);
		grid.add(new StatTile(best == null ? "-" : Format.ticks(best.getTicks()), "Best", best == null ? "no clear yet" : Format.shortDay(best.getStartedAt()), Theme.GOLD_BRIGHT));
		grid.add(new StatTile(sob < 0 ? "-" : Format.ticks(sob), "Sum of best", sob < 0 || best == null ? "every room" : Format.delta(sob - best.getTicks()) + " on best", Theme.TEAL));
		grid.add(new StatTile(avg < 0 ? "-" : Format.ticks(avg), "Average", "last 10 clears", Theme.TEXT));
		grid.add(new StatTile(Format.count(done), "Clears", wiped == 0 ? "no wipes" : wiped + (wiped == 1 ? " wipe" : " wipes"), Theme.TEXT));
		stack.add(grid);

		stack.add(new SectionHeader("Best rooms", "walk in included"));
		Card rooms = new Card(new GridLayout(0, 1, 0, 2));
		for (int row = 0; row < Encounter.ROWS; row++)
		{
			int t = Records.bestRoom(runs, bucket, row);
			rooms.add(line(Encounter.rowLabel(row), t < 0 ? "-" : Format.ticks(t), t < 0 ? Theme.TEXT_FAINT : Theme.TEXT));
		}
		stack.add(rooms);

		stack.add(new SectionHeader("Best fights", "first hit to fall"));
		Card fights = new Card(new GridLayout(0, 1, 0, 2));
		for (Encounter e : Encounter.values())
		{
			int t = Records.bestFight(runs, bucket, e);
			fights.add(line(e.getTitle(), t < 0 ? "-" : Format.ticks(t), t < 0 ? Theme.TEXT_FAINT : Theme.TEXT));
		}
		stack.add(fights);

		stack.add(new SectionHeader("Recent runs", runs.isEmpty() ? null : Format.count(runs.size()) + " in all"));
		Card history = new Card(new GridLayout(0, 1, 0, 0));
		history.setBorder(new EmptyBorder(2, 4, 2, 4));
		int shown = 0;
		for (int i = runs.size() - 1; i >= 0 && shown < HISTORY; i--)
		{
			history.add(new RunRow(runs.get(i), runs.get(i) == Records.best(runs, Records.bucket(runs.get(i).getTeamSize())), view.now));
			shown++;
		}
		if (shown == 0)
		{
			history.add(Ui.wrapped("Your runs appear here as you finish them.", Theme.small(), Theme.TEXT_FAINT, WIDTH - 20));
		}
		stack.add(history);
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
		JPanel grid = new JPanel(new GridLayout(1, 3, 6, 0));
		grid.setOpaque(false);
		int clears = Records.count(a.getRuns(), Outcome.COMPLETED);
		grid.add(new StatTile(String.valueOf(a.getDry()), "Dry", a.getDry() == 1 ? "clear" : "clears", a.getDry() >= 30 ? Theme.EMBER : Theme.TEXT));
		grid.add(new StatTile(String.valueOf(a.getDrops().size()), "Purples", a.getDrops().isEmpty() ? "none yet" : "1 in " + Math.max(1, Math.round(clears / (double) a.getDrops().size())), Theme.PURPLE));
		grid.add(new StatTile(a.getKc() > 0 ? Format.count(a.getKc()) : String.valueOf(clears), "KC", "clears", Theme.TEXT));
		stack.add(grid);

		stack.add(new SectionHeader("Uniques"));
		Map<String, Integer> counts = new HashMap<>();
		for (Drop d : a.getDrops())
		{
			counts.merge(d.getUnique(), 1, Integer::sum);
		}
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
			log.add(dropLine(d.getUnique(), when, after));
		}
		if (drops.isEmpty())
		{
			log.add(Ui.wrapped("No purples yet. Each one lands here with the completion it came on and how long it took.", Theme.small(), Theme.TEXT_FAINT, WIDTH - 24));
		}
		stack.add(log);
		stack.add(Ui.wrapped("Jagex publishes the archive's drop rates in its second week; the odds of your dry streak come with them.", Theme.small(), Theme.TEXT_FAINT, WIDTH - 16));
		setPage(spoilsPage, stack);
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
