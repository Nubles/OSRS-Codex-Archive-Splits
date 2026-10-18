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
import java.util.function.BiConsumer;
import java.util.function.LongConsumer;
import javax.swing.JComponent;
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
			Run best = view.liveBest;
			int row = live.reached();
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
		Map<Supply, Integer> used = used(run);
		if (!used.isEmpty())
		{
			usedCard.update(used, null);
			stack.add(usedCard);
		}
		setPage(runPage, stack);
	}

	/** Everything that decides the run page's shape, so it is only rebuilt when that changes. */
	private String runKey()
	{
		Run run = view.live != null ? view.live : view.archive == null ? null : view.archive.last();
		return System.identityHashCode(view.archive) + "|" + (view.live != null) + "|" + view.inLobby + "|" + view.checksPack()
			+ "|" + (run == null ? "" : run.getStartedAt() + ":" + run.getTeamSize() + ":" + used(run).isEmpty())
			+ "|" + (view.live == null ? view.now / 60_000 : 0);
	}

	/** One tick of a live run on an unchanged page. */
	private void refreshLive()
	{
		Run run = view.live;
		int bucket = Records.bucket(run.getTeamSize());
		Run best = view.liveBest;
		int[] bestRooms = new int[Encounter.ROWS];
		for (int row = 0; row < Encounter.ROWS; row++)
		{
			bestRooms[row] = Records.bestRoom(runs(), bucket, row);
		}
		table.update(run, best, bestRooms, run.getTicks(), true);
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
			RunRow row = new RunRow(r, r == Records.best(runs, Records.bucket(r.getTeamSize())), view.now);
			row.setComponentPopupMenu(menu("Delete this run",
				"Delete the " + describe(r) + " from " + Format.day(r.getStartedAt()) + " at " + Format.time(r.getStartedAt())
					+ "? Its times leave your records for good.",
				() -> onDeleteRun.accept(r.getStartedAt())));
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
			stack.add(Ui.wrapped("Right-click a run the plugin timed wrongly to delete it.", Theme.small(), Theme.TEXT_FAINT, WIDTH - 16));
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
