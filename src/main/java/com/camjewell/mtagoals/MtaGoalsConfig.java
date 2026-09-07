package com.camjewell.mtagoals;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Notification;
import net.runelite.client.config.Range;

import static com.camjewell.mtagoals.MtaGoalsConfig.CONFIG_GROUP;

@ConfigGroup(CONFIG_GROUP)
public interface MtaGoalsConfig extends Config
{
	String CONFIG_GROUP = "mtagoals";

	@ConfigSection(
		name = "Reward Tracking",
		description = "Tick the rewards you're working toward. The overlay shows progress toward their combined cost.",
		position = 0
	)
	String REWARD_TRACKING = "RewardTracking";

	@ConfigItem(
		section = REWARD_TRACKING,
		keyName = "thresholdNotification",
		name = "Threshold notification",
		description = "Fires once when your points meet the combined cost of every selected reward",
		position = 0
	)
	default Notification thresholdNotification()
	{
		return Notification.OFF;
	}

	@ConfigItem(
		section = REWARD_TRACKING,
		keyName = "trackBeginnerWand",
		name = "Beginner wand",
		description = "Include the Beginner wand in the tracked totals",
		position = 1
	)
	default boolean trackBeginnerWand()
	{
		return false;
	}

	@ConfigItem(
		section = REWARD_TRACKING,
		keyName = "trackApprenticeWand",
		name = "Apprentice wand (upgrade)",
		description = "Include the Apprentice wand upgrade in the tracked totals",
		position = 2
	)
	default boolean trackApprenticeWand()
	{
		return false;
	}

	@ConfigItem(
		section = REWARD_TRACKING,
		keyName = "trackTeacherWand",
		name = "Teacher wand (upgrade)",
		description = "Include the Teacher wand upgrade in the tracked totals",
		position = 3
	)
	default boolean trackTeacherWand()
	{
		return false;
	}

	@ConfigItem(
		section = REWARD_TRACKING,
		keyName = "trackMasterWand",
		name = "Master wand (upgrade)",
		description = "Include the Master wand upgrade in the tracked totals",
		position = 4
	)
	default boolean trackMasterWand()
	{
		return false;
	}

	@ConfigItem(
		section = REWARD_TRACKING,
		keyName = "trackInfinityHat",
		name = "Infinity hat",
		description = "Include the Infinity hat in the tracked totals",
		position = 5
	)
	default boolean trackInfinityHat()
	{
		return false;
	}

	@ConfigItem(
		section = REWARD_TRACKING,
		keyName = "trackInfinityTop",
		name = "Infinity top",
		description = "Include the Infinity top in the tracked totals",
		position = 6
	)
	default boolean trackInfinityTop()
	{
		return false;
	}

	@ConfigItem(
		section = REWARD_TRACKING,
		keyName = "trackInfinityBottoms",
		name = "Infinity bottoms",
		description = "Include the Infinity bottoms in the tracked totals",
		position = 7
	)
	default boolean trackInfinityBottoms()
	{
		return false;
	}

	@ConfigItem(
		section = REWARD_TRACKING,
		keyName = "trackInfinityGloves",
		name = "Infinity gloves",
		description = "Include the Infinity gloves in the tracked totals",
		position = 8
	)
	default boolean trackInfinityGloves()
	{
		return false;
	}

	@ConfigItem(
		section = REWARD_TRACKING,
		keyName = "trackInfinityBoots",
		name = "Infinity boots",
		description = "Include the Infinity boots in the tracked totals",
		position = 9
	)
	default boolean trackInfinityBoots()
	{
		return false;
	}

	@ConfigItem(
		section = REWARD_TRACKING,
		keyName = "trackMagesBook",
		name = "Mage's book",
		description = "Include the Mage's book in the tracked totals",
		position = 10
	)
	default boolean trackMagesBook()
	{
		return false;
	}

	@ConfigItem(
		section = REWARD_TRACKING,
		keyName = "trackBonesToPeaches",
		name = "Bones to Peaches spell",
		description = "Include the Bones to Peaches spell unlock in the tracked totals",
		position = 11
	)
	default boolean trackBonesToPeaches()
	{
		return false;
	}

	@ConfigItem(
		section = REWARD_TRACKING,
		keyName = "trackRunePouch",
		name = "Rune pouch",
		description = "Include the Rune pouch in the tracked totals",
		position = 12
	)
	default boolean trackRunePouch()
	{
		return false;
	}

	@ConfigSection(
		name = "Points",
		description = "Where your current Pizazz point totals come from. The plugin reads the in-game points display "
			+ "shown in the MTA lobby automatically; the fields below are a manual fallback if that ever fails to "
			+ "pick up, or if you want to preview the overlay outside the arena.",
		position = 1
	)
	String POINTS = "Points";

	@ConfigItem(
		section = POINTS,
		keyName = "useManualPoints",
		name = "Use manual point totals",
		description = "When enabled, the fields below are used instead of reading the in-game lobby points display",
		position = 0
	)
	default boolean useManualPoints()
	{
		return false;
	}

	@ConfigItem(
		section = POINTS,
		keyName = "manualTelekineticPoints",
		name = "Telekinetic points",
		description = "Your current Telekinetic pizazz points",
		position = 1
	)
	@Range(min = 0, max = 4000)
	default int manualTelekineticPoints()
	{
		return 0;
	}

	@ConfigItem(
		section = POINTS,
		keyName = "manualGraveyardPoints",
		name = "Graveyard points",
		description = "Your current Graveyard pizazz points",
		position = 2
	)
	@Range(min = 0, max = 4000)
	default int manualGraveyardPoints()
	{
		return 0;
	}

	@ConfigItem(
		section = POINTS,
		keyName = "manualEnchantmentPoints",
		name = "Enchantment points",
		description = "Your current Enchantment pizazz points",
		position = 3
	)
	@Range(min = 0, max = 16000)
	default int manualEnchantmentPoints()
	{
		return 0;
	}

	@ConfigItem(
		section = POINTS,
		keyName = "manualAlchemistPoints",
		name = "Alchemist points",
		description = "Your current Alchemist pizazz points",
		position = 4
	)
	@Range(min = 0, max = 8000)
	default int manualAlchemistPoints()
	{
		return 0;
	}

	@ConfigSection(
		name = "Overlay",
		description = "Overlay display options",
		position = 2
	)
	String OVERLAY = "Overlay";

	@ConfigItem(
		section = OVERLAY,
		keyName = "showRoomBars",
		name = "Show per-room progress bars",
		description = "Toggle to display or hide the four per-room point progress bars",
		position = 0
	)
	default boolean showRoomBars()
	{
		return true;
	}

	@ConfigItem(
		section = OVERLAY,
		keyName = "onlyShowInArena",
		name = "Only show while in the arena",
		description = "Hide the overlay unless you're inside the Mage Training Arena building",
		position = 1
	)
	default boolean onlyShowInArena()
	{
		return true;
	}

	@ConfigSection(
		name = "Completion Estimates",
		description = "A second overlay estimating how much longer until your tracked reward goals are met.",
		position = 3
	)
	String ESTIMATES = "Estimates";

	@ConfigItem(
		section = ESTIMATES,
		keyName = "showEstimatesOverlay",
		name = "Show estimates overlay",
		description = "Toggle the mazes/inventories/items/time-remaining overlay",
		position = 0
	)
	default boolean showEstimatesOverlay()
	{
		return true;
	}

	@ConfigItem(
		section = ESTIMATES,
		keyName = "graveyardFruit",
		name = "Graveyard fruit",
		description = "Which fruit you deposit in the Creature Graveyard, used to estimate inventories remaining "
			+ "(peaches are worth twice as many points per fruit as bananas)",
		position = 1
	)
	default GraveyardFruit graveyardFruit()
	{
		return GraveyardFruit.BANANAS;
	}

	@ConfigItem(
		section = ESTIMATES,
		keyName = "showTimeEstimates",
		name = "Show time estimates",
		description = "Adds an estimated time in parentheses next to the Telekinetic/Alchemist/Graveyard action "
			+ "counts, based on your observed pace in that room this session",
		position = 2
	)
	default boolean showTimeEstimates()
	{
		return true;
	}

	@ConfigItem(
		section = ESTIMATES,
		keyName = "rateWindowMinutes",
		name = "Rate averaging window (min)",
		description = "How many minutes of your most recent active play in a room the observed-pace rate averages "
			+ "over. Shorter reacts faster to a real pace change (e.g. slowing down partway through a session); "
			+ "longer is steadier against short-term noise.",
		position = 3
	)
	@Range(min = 1, max = 60)
	default int rateWindowMinutes()
	{
		return 5;
	}
}
