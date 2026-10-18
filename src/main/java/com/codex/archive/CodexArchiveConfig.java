package com.codex.archive;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(CodexArchiveConfig.GROUP)
public interface CodexArchiveConfig extends Config
{
	String GROUP = "codexarchive";

	@ConfigSection(
		name = "Pack check",
		description = "What you want in your pack before the archive's doors close; nothing can be banked inside",
		position = 10
	)
	String PACK = "pack";

	@ConfigItem(
		keyName = "showPlaque",
		name = "Splits plaque",
		description = "Show the live room and run timers, and how you stand against your best, in the game view during a run",
		position = 0
	)
	default boolean showPlaque()
	{
		return true;
	}

	@ConfigItem(
		keyName = "chatSplits",
		name = "Splits in chat",
		description = "When a boss falls, write its room time and how it compares with your best in the chat box",
		position = 1
	)
	default boolean chatSplits()
	{
		return true;
	}

	@ConfigItem(
		keyName = "packPlaque",
		name = "Pack warning",
		description = "In the lobby, show a plaque when your pack is short of what you set below",
		position = 11,
		section = PACK
	)
	default boolean packPlaque()
	{
		return true;
	}

	@Range(max = 28)
	@ConfigItem(
		keyName = "food",
		name = "Food",
		description = "Pieces of food to bring",
		position = 12,
		section = PACK
	)
	default int food()
	{
		return 10;
	}

	@Range(max = 112)
	@ConfigItem(
		keyName = "brews",
		name = "Brew doses",
		description = "Doses of Saradomin brew to bring",
		position = 13,
		section = PACK
	)
	default int brews()
	{
		return 12;
	}

	@Range(max = 112)
	@ConfigItem(
		keyName = "restores",
		name = "Restore doses",
		description = "Doses of super restore, prayer potion or sanfew serum to bring",
		position = 14,
		section = PACK
	)
	default int restores()
	{
		return 12;
	}

	@Range(max = 112)
	@ConfigItem(
		keyName = "boosts",
		name = "Boost doses",
		description = "Doses of combat, ranging or magic potions to bring",
		position = 15,
		section = PACK
	)
	default int boosts()
	{
		return 4;
	}
}
