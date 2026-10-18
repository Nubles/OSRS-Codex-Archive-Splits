package com.codex.archive;

import com.codex.archive.core.Archive;
import com.codex.archive.core.Encounter;
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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
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
	/** How long after the lobby was last seen an instance still counts as the archive, about a minute. */
	private static final int LOBBY_TICKS = 100;
	/** How long after a run uniques landing in the pack are still its loot, five minutes. */
	private static final int LOOT_TICKS = 500;
	private static final int SAVE_TICKS = 50;

	/** The lobby's own NPCs: seeing one means the next instance is the archive. */
	private static final String[] LOBBY_NPCS = {"record keeper", "general montoya"};
	private static final Pattern KC = Pattern.compile("your (?:completed )?(.*archive.*?) (?:completion |kill )?count is:? ?([\\d,]+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern WIPE = Pattern.compile("(your party|your team|everyone) ha(?:s|ve) (?:fallen|been defeated|perished)", Pattern.CASE_INSENSITIVE);

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

	private final RunTracker tracker = new RunTracker();
	private final SplitsPlaque splitsPlaque = new SplitsPlaque();
	private final PackPlaque packPlaque = new PackPlaque();
	private final Map<Integer, Integer> inventory = new HashMap<>();
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
	private boolean inInstance;
	private int instanceSince = -1;
	private int lobbySeen = -LOBBY_TICKS * 2;
	private int lobbyNpcs;
	private int runEnded = -LOOT_TICKS * 2;
	private boolean wipeAnnounced;
	private int lastRow;

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
		clientThread.invokeLater(() ->
		{
			loadProfile();
			ItemContainer inv = client.getItemContainer(InventoryID.INV);
			if (inv != null)
			{
				readInventory(inv, false);
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
			save();
			archive = null;
			profile = null;
			inventory.clear();
		});
		panel = null;
	}

	private void applyConfig()
	{
		splitsPlaque.setEnabled(config.showPlaque());
		packPlaque.setEnabled(config.packPlaque());
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
		if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			if (tracker.isActive())
			{
				endRun();
			}
			inInstance = false;
			lobbyNpcs = 0;
			save();
			dirty = true;
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		save();
		loadProfile();
		publish();
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		if (isLobbyNpc(event.getNpc()))
		{
			lobbyNpcs++;
			lobbySeen = tick;
			dirty = true;
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		if (isLobbyNpc(event.getNpc()))
		{
			lobbyNpcs = Math.max(0, lobbyNpcs - 1);
			lobbySeen = tick;
			dirty = true;
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		Actor actor = event.getActor();
		if (!(actor instanceof NPC) || !inInstance)
		{
			return;
		}
		NPC npc = (NPC) actor;
		Encounter encounter = Encounter.byName(npc.getName());
		if (!tracker.isActive() && encounter == Encounter.GUARDIAN)
		{
			// The lobby was missed, but the Guardian is unmistakable: the run began on entering.
			beginRun(instanceSince >= 0 ? instanceSince : tick);
		}
		if (!tracker.isActive())
		{
			return;
		}
		encounter = tracker.resolve(npc.getName(), npc.getCombatLevel());
		if (encounter != null)
		{
			tracker.engage(encounter, tick);
			dirty = true;
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
		if (!(actor instanceof NPC))
		{
			return;
		}
		NPC npc = (NPC) actor;
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
				if (other != npc && !other.isDead() && tracker.resolve(other.getName(), other.getCombatLevel()) == encounter)
				{
					anyAlive = true;
					break;
				}
			}
		}
		tracker.fell(encounter, tick, anyAlive);
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
		{
			return;
		}
		String text = event.getMessage().replaceAll("<[^>]*>", "");
		Matcher kc = KC.matcher(text);
		if (kc.find())
		{
			int count = Integer.parseInt(kc.group(2).replace(",", ""));
			if (tracker.isActive())
			{
				tracker.kc(count);
			}
			if (archive != null)
			{
				archive.kc(count, System.currentTimeMillis());
				unsaved = true;
				changed = true;
			}
			dirty = true;
		}
		if (tracker.isActive() && WIPE.matcher(text).find())
		{
			wipeAnnounced = true;
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.INV)
		{
			readInventory(event.getItemContainer(), true);
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		tick++;
		WorldView view = client.getTopLevelWorldView();
		boolean nowInstance = view != null && view.isInstance();
		if (nowInstance && !inInstance)
		{
			instanceSince = tick;
			if (tick - lobbySeen <= LOBBY_TICKS && !tracker.isActive())
			{
				beginRun(tick);
			}
		}
		else if (!nowInstance && inInstance && tracker.isActive())
		{
			endRun();
		}
		inInstance = nowInstance;

		if (tracker.isActive())
		{
			if (tracker.nextRow() == 0 && view != null)
			{
				tracker.seeTeam(view.players().stream().mapToInt(p -> 1).sum());
			}
			tracker.tick(tick);
			int row = tracker.nextRow();
			if (row != lastRow)
			{
				announceSplit(row - 1);
				lastRow = row;
			}
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
		int team = view == null ? 1 : view.players().stream().mapToInt(p -> 1).sum();
		tracker.begin(System.currentTimeMillis(), at, team, pack);
		lastRow = 0;
		wipeAnnounced = false;
		dirty = true;
	}

	private void endRun()
	{
		Run run = tracker.finish(tick, wipeAnnounced);
		runEnded = tick;
		wipeAnnounced = false;
		if (run != null && archive != null && (run.isCompleted() || !run.getSplits().isEmpty()))
		{
			archive.record(run);
			unsaved = true;
			changed = true;
			if (run.isCompleted())
			{
				announceRun(run);
			}
		}
		save();
		dirty = true;
	}

	/** Counts supplies, and spots a unique arriving during or just after a run. */
	private void readInventory(ItemContainer container, boolean watchForLoot)
	{
		Map<Integer, Integer> now = new HashMap<>();
		Map<Supply, Integer> units = new EnumMap<>(Supply.class);
		if (container != null)
		{
			for (Item item : container.getItems())
			{
				if (item.getId() < 0 || item.getQuantity() <= 0)
				{
					continue;
				}
				now.merge(item.getId(), item.getQuantity(), Integer::sum);
				String name = itemManager.getItemComposition(item.getId()).getName();
				Supply supply = Supply.of(name);
				if (supply != null)
				{
					units.merge(supply, supply.units(name) * item.getQuantity(), Integer::sum);
				}
			}
		}
		boolean lootWindow = tracker.isActive() || tick - runEnded <= LOOT_TICKS;
		if (watchForLoot && lootWindow && archive != null)
		{
			for (Map.Entry<Integer, Integer> e : now.entrySet())
			{
				if (e.getValue() > inventory.getOrDefault(e.getKey(), 0))
				{
					String unique = Spoils.unique(itemManager.getItemComposition(e.getKey()).getName());
					if (unique != null)
					{
						tracker.purple(unique);
						archive.purple(unique, System.currentTimeMillis());
						unsaved = true;
						changed = true;
					}
				}
			}
		}
		inventory.clear();
		inventory.putAll(now);
		pack = units;
		tracker.pack(units);
		dirty = true;
	}

	private void announceSplit(int row)
	{
		Run live = tracker.view(tick);
		Split split = live == null ? null : live.split(row);
		if (!config.chatSplits() || split == null || !split.isDone() || archive == null)
		{
			return;
		}
		Run best = Records.best(archive.getRuns(), Records.bucket(live.getTeamSize()));
		Integer delta = Records.delta(live, best, row);
		ChatMessageBuilder line = new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT).append(split.getEncounter().getShortName() + ": ")
			.append(ChatColorType.NORMAL).append("room " + Format.ticks(split.roomTicks(0)) + ", fight " + Format.ticks(split.fightTicks(0))
				+ ", total " + Format.ticks(split.getEndTick()));
		if (delta != null)
		{
			line.append(" (" + Format.delta(delta) + " on your best)");
		}
		chat(line);
	}

	private void announceRun(Run run)
	{
		if (!config.chatSplits() || archive == null)
		{
			return;
		}
		Run best = Records.best(archive.getRuns(), Records.bucket(run.getTeamSize()));
		ChatMessageBuilder line = new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT).append("Archive cleared in " + Format.ticks(run.getTicks()))
			.append(ChatColorType.NORMAL).append(" with a party of " + run.getTeamSize() + ".");
		if (best == run)
		{
			line.append(ChatColorType.HIGHLIGHT).append(" A new personal best.");
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

	private void save()
	{
		if (profile == null || archive == null || !unsaved)
		{
			return;
		}
		unsaved = false;
		configManager.setConfiguration(CodexArchiveConfig.GROUP, profile, ARCHIVE_KEY, gson.toJson(archive));
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
		boolean inLobby = loggedIn && lobbyNpcs > 0 && !tracker.isActive();
		View view = new View(loggedIn && player != null ? player.getName() : null, loggedIn, copy, live, inLobby,
			pack, targets(), System.currentTimeMillis());
		splitsPlaque.show(view);
		packPlaque.show(view);
		ArchivePanel target = panel;
		if (target != null)
		{
			SwingUtilities.invokeLater(() -> target.update(view));
		}
	}
}
