package com.codex.archive.core;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a raider carries in. Nothing can be banked once inside the archive, so the pack check
 * counts these before the doors close, and the run history counts what each run ate and drank.
 * Items are told apart by name, so new potions in a family are counted without an update.
 */
public enum Supply
{
	FOOD("Food", "pieces"),
	BREWS("Brews", "doses"),
	RESTORES("Restores", "doses"),
	BOOSTS("Boosts", "doses");

	private static final Pattern DOSES = Pattern.compile("^(.*)\\((\\d)\\)$");

	private static final Set<String> FOODS = new HashSet<>(Arrays.asList(
		"shark", "manta ray", "anglerfish", "dark crab", "sea turtle", "tuna potato", "mushroom potato",
		"cooked karambwan", "monkfish", "curry", "pineapple pizza", "anchovy pizza", "meat pizza",
		"cooked moonlight antelope", "cooked sunlight antelope", "cooked dashing kebbit", "cooked wild kebbit",
		"cooked pyre fox", "cooked larupia", "cooked graahk", "cooked kyatt", "potato with cheese",
		"egg potato", "chilli potato", "swordfish", "lobster", "bass", "cooked bream", "cooked moss lizard",
		"tangled toad's legs", "summer pie", "half a summer pie", "wild pie", "half a wild pie",
		"botanical pie", "half a botanical pie", "admiral pie", "half an admiral pie", "cooked jubbly",
		"basket of strawberries", "jug of wine", "blighted manta ray", "blighted anglerfish", "blighted karambwan",
		"paddlefish", "ascension ration"));

	private final String title;
	private final String unit;

	Supply(String title, String unit)
	{
		this.title = title;
		this.unit = unit;
	}

	public String getTitle()
	{
		return title;
	}

	public String getUnit()
	{
		return unit;
	}

	/**
	 * How many units of a supply one of these items is: a piece of food is one, a potion is its doses.
	 *
	 * @return the units, or 0 when the item is not a supply
	 */
	public int units(String itemName)
	{
		return this == of(itemName) ? unitsOf(itemName) : 0;
	}

	/** The kind of supply an item is, or null. */
	public static Supply of(String itemName)
	{
		if (itemName == null)
		{
			return null;
		}
		String name = Encounter.clean(itemName);
		Matcher m = DOSES.matcher(name);
		if (m.matches())
		{
			String potion = m.group(1).trim();
			if (potion.contains("saradomin brew") || potion.startsWith("ancient brew"))
			{
				return BREWS;
			}
			if (potion.contains("restore") || potion.startsWith("prayer potion") || potion.startsWith("sanfew")
				|| potion.startsWith("prayer regeneration") || potion.contains("moonlight potion"))
			{
				return RESTORES;
			}
			if (potion.contains("combat") || potion.contains("ranging") || potion.contains("bastion")
				|| potion.contains("battlemage") || potion.contains("magic potion") || potion.contains("super strength")
				|| potion.contains("super attack") || potion.contains("super defence") || potion.contains("forgotten brew")
				|| potion.contains("overload"))
			{
				return BOOSTS;
			}
			return null;
		}
		return FOODS.contains(name) ? FOOD : null;
	}

	/** A potion's doses, or one for anything else. */
	static int unitsOf(String itemName)
	{
		Matcher m = DOSES.matcher(Encounter.clean(itemName));
		return m.matches() ? Integer.parseInt(m.group(2)) : 1;
	}

	public static String lower(String s)
	{
		return s.toLowerCase(Locale.ENGLISH);
	}
}
