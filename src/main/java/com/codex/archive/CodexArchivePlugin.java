package com.codex.archive;

import com.codex.archive.core.Archive;
import com.codex.archive.core.Comparison;
import com.codex.archive.core.Export;
import com.codex.archive.core.Encounter;
import com.codex.archive.core.Goal;
import com.codex.archive.core.LootWatch;
import com.codex.archive.core.Messages;
import com.codex.archive.core.Presence;
import com.codex.archive.core.Records;
import com.codex.archive.core.Run;
import com.codex.archive.core.RunTracker;
import com.codex.archive.core.Spoils;
import com.codex.archive.core.Split;
import com.codex.archive.core.Supply;
import com.codex.archive.overlay.PackPlaque;
import com.codex.archive.overlay.SplitsPlaque;
import com.codex.archive.ui.ArchivePanel;
import com.codex.archive.ui.Format;
import com.codex.archive.ui.Sigil;
import com.codex.archive.ui.View;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
	name = "Codex Archive Splits",
	description = "Splits, personal bests, purples and supply checks for the Fractured Archive raid",
	tags = {"raid", "raids", "fractured archive", "archive", "splits", "timer", "pb", "purple", "codex"}
)
public class CodexArchivePlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(CodexArchivePlugin.class);
	private static final String ARCHIVE_KEY = "archive";
	/** How long after a clear uniques landing in the pack are still its loot, five minutes, or until the bank opens. */
	private static final int LOOT_TICKS = 500;
	/** How long the plaque keeps showing a clear while the player is still inside, half a minute. */
	private static final int CLEARED_TICKS = 50;
	private static final int SAVE_TICKS = 50;

	/** The lobby's own NPCs: seeing one means the next instance is the archive. */
	private static final String[] LOBBY_NPCS = {"record keeper", "general montoya"};

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private Gson gson;

	@Inject
	private CodexArchiveConfig config;

	@Inject
	private ScheduledExecutorService executor;

	private final RunTracker tracker = new RunTracker();
	private final Presence presence = new Presence();
	private final LootWatch loot = new LootWatch();
	private final SplitsPlaque splitsPlaque = new SplitsPlaque();
	private final PackPlaque packPlaque = new PackPlaque();
	private Map<Supply, Integer> pack = new EnumMap<>(Supply.class);

	private ArchivePanel panel;
	private NavigationButton navButton;

	private String profile;
	private Archive archive;
	private boolean unsaved;
	/** The copy the panel last got; copied again only when the archive changes, not on every tick of a run. */
	private Archive shown;
	private boolean changed;
	private boolean dirty;
	private int tick;
	private int runEnded = Integer.MIN_VALUE / 2;
	/** The run just recorded when it was a clear, for the plaque while the player is still inside. */
	private Run lastClear;
	private boolean wipeAnnounced;
	/** The furthest row whose split has gone to the chat box this run. */
	private int announcedRow;

	@Provides
	CodexArchiveConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CodexArchiveConfig.class);
	}

	@Override
	protected void startUp()
	{
		panel = new ArchivePanel();
		navButton = NavigationButton.builder()
			.tooltip("Codex Archive Splits")
			.icon(Sigil.icon(16))
			.priority(8)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		applyConfig();
		overlayManager.add(splitsPlaque);
		overlayManager.add(packPlaque);
		panel.onDelete(this::deleteRun, this::deleteDrop);
		panel.onNotes((at, role) -> annotate(a -> a.setRole(at, role)), (at, note) -> annotate(a -> a.setNote(at, note)));
		panel.onExport(this::export);
		panel.onSaveCard(this::saveCard);
		panel.onGoal((bucket, ticks) -> annotate(a ->
		{
			a.setGoal(bucket, ticks);
			return true;
		}));
		clientThread.invokeLater(() ->
		{
			loadProfile();
			readPack();
			WorldView view = client.getTopLevelWorldView();
			if (view != null)
			{
				int lobby = 0;
				for (NPC npc : view.npcs())
				{
					lobby += isLobbyNpc(npc) ? 1 : 0;
				}
				presence.lobbyNpcsInView(lobby, tick);
			}
			publish();
		});
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(splitsPlaque);
		overlayManager.remove(packPlaque);
		clientToolbar.removeNavigation(navButton);
		clientThread.invokeLater(() ->
		{
			if (tracker.isActive())
			{
				// The plugin is going off mid-run; what was timed so far is kept rather than lost.
				endRun();
			}
			loot.close();
			presence.reset();
			save();
			archive = null;
			shown = null;
			profile = null;
		});
		panel = null;
	}

	private void applyConfig()
	{
		splitsPlaque.setEnabled(config.showPlaque());
		packPlaque.setEnabled(config.packPlaque());
		ArchivePanel view = panel;
		boolean nameOnCard = config.nameOnCard();
		if (view != null)
		{
			SwingUtilities.invokeLater(() -> view.setNameOnCard(nameOnCard));
		}
	}

	@Subscribe
	public void onClientShutdown(ClientShutdown event)
	{
		// ConfigManager writes its changes to disk in this same event, before a save queued on the
		// client thread runs, so a save made here has to be sent on by hand or it is never written.
		CompletableFuture<Void> done = new CompletableFuture<>();
		clientThread.invokeLater(() ->
		{
			try
			{
				// Closing the client mid-run logs out of it, as the logout screen would: what was timed is kept.
				boolean wrote = false;
				if (tracker.isActive())
				{
					endRun();
					wrote = true;
				}
				wrote |= save();
				if (wrote)
				{
					configManager.sendConfig();
				}
			}
			finally
			{
				done.complete(null);
			}
		});
		event.waitFor(done);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (CodexArchiveConfig.GROUP.equals(event.getGroup()))
		{
			applyConfig();
			dirty = true;
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOADING)
		{
			presence.sceneLoaded(tick);
		}
		else if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			if (tracker.isActive())
			{
				endRun();
			}
			presence.reset();
			loot.close();
			lastClear = null;
			save();
			dirty = true;
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		if (tracker.isActive())
		{
			// The account is changing under a run, as on logging out before the login screen shows:
			// the run belongs to the account it was timed on, so it is put away there first.
			endRun();
		}
		save();
		loadProfile();
		publish();
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		if (isLobbyNpc(event.getNpc()))
		{
			presence.lobbyNpc(true, tick);
			dirty = true;
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		NPC npc = event.getNpc();
		if (isLobbyNpc(npc))
		{
			presence.lobbyNpc(false, tick);
			dirty = true;
		}
		else if (tracker.isActive() && (npc.isDead() || npc.getHealthRatio() == 0))
		{
			// Some bosses leave without the client reporting a death; a body leaving at no health is a fall.
			bossFell(npc);
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		Actor actor = event.getActor();
		if (!(actor instanceof NPC) || !presence.isInInstance())
		{
			return;
		}
		NPC npc = (NPC) actor;
		if (npc.isDead())
		{
			// A hit landing on a body is not a fight going on.
			return;
		}
		if (!tracker.isActive() && Encounter.byName(npc.getName()) == Encounter.GUARDIAN)
		{
			// The lobby was missed, but the Guardian is unmistakable: the run began on entering.
			beginRun(presence.likelyStart(tick));
		}
		if (!tracker.isActive())
		{
			return;
		}
		String name = npc.getName() == null ? null : Text.removeTags(npc.getName());
		Encounter encounter = tracker.resolve(npc.getName(), npc.getCombatLevel());
		if (encounter != null)
		{
			tracker.engage(encounter, tick, name, npc.getCombatLevel());
			dirty = true;
		}
		else
		{
			// Kept so a boss that goes unrecognised can show what the run did see.
			tracker.foe(name, npc.getCombatLevel());
		}
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		Actor actor = event.getActor();
		if (!tracker.isActive())
		{
			return;
		}
		if (actor == client.getLocalPlayer())
		{
			tracker.died();
			dirty = true;
			return;
		}
		if (actor instanceof NPC)
		{
			bossFell((NPC) actor);
		}
	}

	/** A boss NPC went down; the split closes once nothing else of its encounter stands. */
	private void bossFell(NPC npc)
	{
		Encounter encounter = tracker.resolve(npc.getName(), npc.getCombatLevel());
		if (encounter == null)
		{
			return;
		}
		boolean anyAlive = false;
		WorldView view = client.getTopLevelWorldView();
		if (view != null)
		{
			for (NPC other : view.npcs())
			{
				if (other != npc && !other.isDead() && other.getHealthRatio() != 0
					&& tracker.resolve(other.getName(), other.getCombatLevel()) == encounter)
				{
					anyAlive = true;
					break;
				}
			}
		}
		tracker.fell(encounter, tick, anyAlive);
		dirty = true;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
		{
			return;
		}
		String text = event.getMessage();
		int count = Messages.completionCount(text);
		if (count >= 0)
		{
			if (tracker.isActive())
			{
				// The game's own word that the archive is cleared: the run is complete, whatever was seen of the last boss.
				tracker.kc(count);
				tracker.complete(tick);
				endRun();
			}
			if (archive != null)
			{
				archive.kc(count, System.currentTimeMillis());
				unsaved = true;
				changed = true;
			}
			dirty = true;
		}
		if (tracker.isActive() && Messages.isWipe(text))
		{
			wipeAnnounced = true;
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int id = event.getContainerId();
		if (id == InventoryID.INV)
		{
			readPack();
		}
		if (id == InventoryID.INV || id == InventoryID.WORN)
		{
			watchLoot();
		}
		else if (id == InventoryID.BANK && !tracker.isActive())
		{
			// Anything from here on could have come out of the bank.
			loot.close();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		tick++;
		WorldView view = client.getTopLevelWorldView();
		boolean instance = view != null && view.isInstance();
		Presence.Action action = presence.tick(tick, instance, tracker.isActive(), tracker.elapsed(tick));
		if (action == Presence.Action.END)
		{
			endRun();
		}
		else if (action == Presence.Action.BEGIN)
		{
			beginRun(presence.beginTick());
		}

		if (tracker.isActive())
		{
			if (tracker.nextRow() == 0 && view != null)
			{
				tracker.seeTeam(count(view));
				tracker.seeCrew(crew(view));
			}
			tracker.tick(tick);
			int row = tracker.nextRow();
			if (row > announcedRow)
			{
				announceSplit(row - 1);
				announcedRow = row;
			}
			else if (row < announcedRow)
			{
				// A boss came back after its split closed; its true time goes to the chat box when it falls again.
				announcedRow = row;
			}
			dirty = true;
		}
		if (lastClear != null && (tick - runEnded > CLEARED_TICKS || !instance))
		{
			lastClear = null;
			dirty = true;
		}
		if (unsaved && tick % SAVE_TICKS == 0)
		{
			save();
		}
		if (dirty)
		{
			publish();
		}
	}

	private static int count(WorldView view)
	{
		int n = 0;
		for (Player ignored : view.players())
		{
			n++;
		}
		return n;
	}

	/** The other raiders in view, by their names as the game shows them. */
	private List<String> crew(WorldView view)
	{
		List<String> names = new ArrayList<>();
		Player me = client.getLocalPlayer();
		for (Player p : view.players())
		{
			if (p != null && p != me && p.getName() != null)
			{
				names.add(Text.sanitize(p.getName()));
			}
		}
		return names;
	}

	private static boolean isLobbyNpc(NPC npc)
	{
		String name = npc.getName();
		if (name == null)
		{
			return false;
		}
		String lower = name.toLowerCase();
		for (String n : LOBBY_NPCS)
		{
			if (lower.equals(n))
			{
				return true;
			}
		}
		return false;
	}

	private void beginRun(int at)
	{
		WorldView view = client.getTopLevelWorldView();
		tracker.begin(System.currentTimeMillis(), at, view == null ? 1 : count(view), pack);
		if (view != null)
		{
			tracker.seeCrew(crew(view));
		}
		loot.open(heldUniques());
		announcedRow = 0;
		wipeAnnounced = false;
		lastClear = null;
		dirty = true;
	}

	private void endRun()
	{
		List<String> purples = tracker.purples();
		Run run = tracker.finish(tick, wipeAnnounced);
		runEnded = tick;
		wipeAnnounced = false;
		if (run == null)
		{
			return;
		}
		if (run.isCompleted())
		{
			loot.closeAt(tick + LOOT_TICKS);
		}
		else
		{
			loot.close();
		}
		long now = System.currentTimeMillis();
		if (archive != null)
		{
			if (run.isCompleted() || !run.getSplits().isEmpty())
			{
				archive.record(run, purples, now);
				if (run.isCompleted())
				{
					lastClear = run;
					announceRun(run);
				}
			}
			else
			{
				// Too little of a run to keep, but a purple is a purple.
				purples.forEach(u -> archive.purple(u, now));
			}
			unsaved = true;
			changed = true;
		}
		save();
		dirty = true;
	}

	/** Counts the supplies in the pack. */
	private void readPack()
	{
		Map<Supply, Integer> units = new EnumMap<>(Supply.class);
		ItemContainer container = client.getItemContainer(InventoryID.INV);
		if (container != null)
		{
			for (Item item : container.getItems())
			{
				if (item.getId() < 0 || item.getQuantity() <= 0)
				{
					continue;
				}
				String name = itemManager.getItemComposition(item.getId()).getName();
				Supply supply = Supply.of(name);
				if (supply != null)
				{
					units.merge(supply, supply.units(name) * item.getQuantity(), Integer::sum);
				}
			}
		}
		pack = units;
		tracker.pack(units);
		dirty = true;
	}

	/** The uniques held now, in the pack and worn, by the panel's name for them. */
	private Map<String, Integer> heldUniques()
	{
		Map<String, Integer> held = new HashMap<>();
		for (int id : new int[]{InventoryID.INV, InventoryID.WORN})
		{
			ItemContainer container = client.getItemContainer(id);
			if (container == null)
			{
				continue;
			}
			for (Item item : container.getItems())
			{
				if (item.getId() < 0 || item.getQuantity() <= 0)
				{
					continue;
				}
				String unique = Spoils.unique(itemManager.getItemComposition(item.getId()).getName());
				if (unique != null)
				{
					held.merge(unique, item.getQuantity(), Integer::sum);
				}
			}
		}
		return held;
	}

	/** A unique arriving during a run, or just after a clear, is a purple. */
	private void watchLoot()
	{
		if (archive == null || !loot.isOpen(tick))
		{
			return;
		}
		for (String unique : loot.arrivals(heldUniques(), tick))
		{
			if (tracker.isActive())
			{
				tracker.purple(unique);
			}
			else
			{
				archive.purple(unique, System.currentTimeMillis());
				unsaved = true;
				changed = true;
			}
			dirty = true;
		}
	}

	private void deleteRun(long startedAt)
	{
		clientThread.invokeLater(() ->
		{
			if (archive != null && archive.removeRun(startedAt))
			{
				unsaved = true;
				changed = true;
				save();
				publish();
			}
		});
	}

	/** A role or note the player wrote on a run in the panel. */
	private void annotate(java.util.function.Predicate<Archive> change)
	{
		clientThread.invokeLater(() ->
		{
			if (archive != null && change.test(archive))
			{
				unsaved = true;
				changed = true;
				save();
				publish();
			}
		});
	}

	/** Asks where to save the run history with RuneLite's own file chooser, then writes it there; runs on the Swing thread. */
	private void export()
	{
		ArchivePanel view = panel;
		Archive copy = view == null ? null : view.archive();
		if (copy == null)
		{
			return;
		}
		List<Filepath> chosen = new Filepath.Chooser()
			.setIsSave()
			.setAcceptsFiles()
			.setDialogTitle("Save Fractured Archive runs")
			.addExtensionFilter("Spreadsheet (CSV)", "csv")
			.setDefaultExtension("csv")
			.setFileName("Fractured Archive runs.csv")
			.showDialog(view);
		if (chosen == null || chosen.isEmpty())
		{
			return;
		}
		Filepath target = chosen.get(0);
		String csv = Export.csv(copy.getRuns(), ZoneId.systemDefault());
		executor.execute(() ->
		{
			try
			{
				target.write(csv);
			}
			catch (IOException e)
			{
				log.warn("Could not save the run history", e);
			}
		});
	}

	/** Asks where to save a run card with RuneLite's own file chooser, then writes it there; runs on the Swing thread. */
	private void saveCard(BufferedImage card, String name)
	{
		ArchivePanel view = panel;
		if (view == null)
		{
			return;
		}
		List<Filepath> chosen = new Filepath.Chooser()
			.setIsSave()
			.setAcceptsFiles()
			.setDialogTitle("Save run card")
			.addExtensionFilter("PNG image", "png")
			.setDefaultExtension("png")
			.setFileName(name + ".png")
			.showDialog(view);
		if (chosen == null || chosen.isEmpty())
		{
			return;
		}
		Filepath target = chosen.get(0);
		executor.execute(() ->
		{
			String note;
			try (OutputStream out = target.openOutputStream())
			{
				ImageIO.write(card, "png", out);
				note = "Run card saved as " + target.getFileName() + ".";
			}
			catch (IOException e)
			{
				log.warn("Could not save the run card", e);
				note = "The run card could not be saved: " + e.getMessage();
			}
			String shown = note;
			SwingUtilities.invokeLater(() -> view.cardNote(shown));
		});
	}

	private void deleteDrop(long at, String unique)
	{
		clientThread.invokeLater(() ->
		{
			if (archive != null && archive.removeDrop(at, unique))
			{
				unsaved = true;
				changed = true;
				save();
				publish();
			}
		});
	}

	private void announceSplit(int row)
	{
		Run live = tracker.view(tick);
		Split split = live == null ? null : live.split(row);
		if (!config.chatSplits() || split == null || !split.isDone() || archive == null)
		{
			return;
		}
		Comparison.Mode mode = config.compareTo();
		Comparison against = Comparison.of(archive, Records.bucket(live.getTeamSize()), mode, live);
		Integer delta = against.delta(live, row);
		mode = against.getMode();
		ChatMessageBuilder line = new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT).append(split.getEncounter().getShortName())
			.append(ChatColorType.NORMAL).append(" down at " + Format.ticks(split.getEndTick()));
		if (delta != null)
		{
			line.append(", ").append(ChatColorType.HIGHLIGHT).append(Format.versus(delta, mode.getNoun())).append(ChatColorType.NORMAL);
		}
		line.append(". Room " + Format.ticks(split.roomTicks(0)) + ", fight " + Format.ticks(split.fightTicks(0)) + ".");
		chat(line);
	}

	private void announceRun(Run run)
	{
		if (!config.chatSplits() || archive == null)
		{
			return;
		}
		Run best = null;
		int bucket = Records.bucket(run.getTeamSize());
		for (Run r : archive.getRuns())
		{
			if (r != run && r.isCompleted() && Records.bucket(r.getTeamSize()) == bucket && (best == null || r.getTicks() < best.getTicks()))
			{
				best = r;
			}
		}
		ChatMessageBuilder line = new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT).append("Archive cleared in " + Format.ticks(run.getTicks()))
			.append(ChatColorType.NORMAL).append(" (" + Records.BUCKETS[bucket].toLowerCase() + ")");
		if (best == null)
		{
			line.append(", your first " + Records.BUCKETS[bucket].toLowerCase() + " clear.");
		}
		else if (run.getTicks() < best.getTicks())
		{
			line.append(ChatColorType.HIGHLIGHT).append(", a new personal best by " + Format.span(best.getTicks() - run.getTicks()) + ".");
		}
		else
		{
			line.append(", " + Format.versus(run.getTicks() - best.getTicks()) + ".");
		}
		int goal = archive.goal(bucket);
		if (goal > 0 && run.getTicks() <= goal && Goal.of(archive.getRuns(), bucket, goal).getBeaten() == 1)
		{
			// The history already holds this run, so it is the only one under the goal.
			line.append(ChatColorType.HIGHLIGHT).append(" Your goal of " + Format.clock(goal) + " is beaten for the first time.");
		}
		chat(line);
	}

	private void chat(ChatMessageBuilder line)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(new ChatMessageBuilder()
				.append(ChatColorType.HIGHLIGHT).append("Codex Archive: ")
				.append(ChatColorType.NORMAL).build() + line.build())
			.build());
	}

	private void loadProfile()
	{
		profile = configManager.getRSProfileKey();
		archive = null;
		if (profile == null)
		{
			return;
		}
		try
		{
			String saved = configManager.getRSProfileConfiguration(CodexArchiveConfig.GROUP, ARCHIVE_KEY);
			if (saved != null && !saved.isEmpty())
			{
				Archive read = gson.fromJson(saved, Archive.class);
				archive = read == null ? null : read.repair();
			}
		}
		catch (JsonParseException e)
		{
			log.warn("Could not read the saved archive, starting it afresh", e);
		}
		if (archive == null)
		{
			archive = new Archive();
		}
		unsaved = false;
		shown = null;
		dirty = true;
	}

	/** Writes to the profile if anything changed, and says whether it did. */
	private boolean save()
	{
		if (profile == null || archive == null || !unsaved)
		{
			return false;
		}
		unsaved = false;
		configManager.setConfiguration(CodexArchiveConfig.GROUP, profile, ARCHIVE_KEY, gson.toJson(archive));
		return true;
	}

	private Map<Supply, Integer> targets()
	{
		Map<Supply, Integer> t = new EnumMap<>(Supply.class);
		t.put(Supply.FOOD, config.food());
		t.put(Supply.BREWS, config.brews());
		t.put(Supply.RESTORES, config.restores());
		t.put(Supply.BOOSTS, config.boosts());
		return t;
	}

	private void publish()
	{
		dirty = false;
		boolean loggedIn = client.getGameState() == GameState.LOGGED_IN;
		Player player = client.getLocalPlayer();
		if (archive == null)
		{
			shown = null;
		}
		else if (shown == null || changed)
		{
			shown = gson.fromJson(gson.toJson(archive), Archive.class).repair();
		}
		changed = false;
		Archive copy = shown;
		Run live = tracker.view(tick);
		boolean inLobby = loggedIn && presence.isInLobby() && !tracker.isActive();
		View view = new View(loggedIn && player != null ? player.getName() : null, loggedIn, copy, live, inLobby,
			pack, targets(), System.currentTimeMillis(), live == null ? lastClear : null, config.compareTo());
		splitsPlaque.show(view);
		packPlaque.show(view);
		ArchivePanel target = panel;
		if (target != null)
		{
			SwingUtilities.invokeLater(() -> target.update(view));
		}
	}
}
