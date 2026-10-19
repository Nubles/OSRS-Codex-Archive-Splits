package com.codex.archive;

import com.codex.archive.core.Comparison;
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
		description = "During a run, show the room and run timers in the game view, and how you stand against what you compare with",
		position = 0
	)
	default boolean showPlaque()
	{
		return true;
	}

	@ConfigItem(
		keyName = "chatSplits",
		name = "Splits in chat",
		description = "When a boss falls, write its time in the chat box with how it stands, and the final time when the run ends",
		position = 1
	)
	default boolean chatSplits()
	{
		return true;
	}

	@ConfigItem(
		keyName = "compareTo",
		name = "Compare against",
		description = "What each boss's time is measured against: your best run, your best rooms strung together, your typical run (the middle of your last 10 clears) or the goal time you set on the Records page",
		position = 2
	)
	default Comparison.Mode compareTo()
	{
		return Comparison.Mode.PERSONAL_BEST;
	}

	@ConfigItem(
		keyName = "nameOnCard",
		name = "Name on cards",
		description = "Write your character's name on run and session cards, and your party's on run cards; turn off to share them without any names",
		position = 3
	)
	default boolean nameOnCard()
	{
		return true;
	}

	@ConfigItem(
		keyName = "packPlaque",
		name = "Pack warning",
		description = "In the lobby, show a plaque naming what your pack is short of, against the amounts below",
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
		description = "Pieces of food to bring; 0 leaves food out of the check",
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
		description = "Doses of Saradomin brew to bring; 0 leaves brews out of the check",
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
		description = "Doses of super restore, prayer potion or sanfew serum to bring; 0 leaves them out of the check",
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
		description = "Doses of combat, ranging or magic potions to bring; 0 leaves them out of the check",
		position = 15,
		section = PACK
	)
	default int boosts()
	{
		return 4;
	}
}
