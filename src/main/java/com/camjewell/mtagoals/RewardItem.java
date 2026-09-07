package com.camjewell.mtagoals;

import net.runelite.api.gameval.ItemID;

import static com.camjewell.mtagoals.PizazzRoom.ALCHEMIST;
import static com.camjewell.mtagoals.PizazzRoom.ENCHANTMENT;
import static com.camjewell.mtagoals.PizazzRoom.GRAVEYARD;
import static com.camjewell.mtagoals.PizazzRoom.TELEKINETIC;

/**
 * Every item purchasable from the Mage Training Arena Rewards Guardian, and its cost
 * in each of the four Pizazz point currencies. The wand upgrades are priced as the
 * Rewards Guardian actually charges them: the incremental cost to trade up from the
 * previous tier, not the cumulative cost from nothing.
 */
public enum RewardItem
{
	NONE("None", 0, 0, 0, 0, 0),
	BEGINNER_WAND("Beginner wand", ItemID.MAGICTRAINING_WAND_BEG, 30, 30, 300, 30),
	APPRENTICE_WAND("Apprentice wand (upgrade)", ItemID.MAGICTRAINING_WAND_APPR, 60, 60, 600, 60),
	TEACHER_WAND("Teacher wand (upgrade)", ItemID.MAGICTRAINING_WAND_TEACH, 150, 150, 1500, 200),
	MASTER_WAND("Master wand (upgrade)", ItemID.MAGICTRAINING_WAND_MASTER, 240, 240, 2400, 240),
	INFINITY_HAT("Infinity hat", ItemID.MAGICTRAINING_INFINITYHAT, 350, 350, 3000, 400),
	INFINITY_TOP("Infinity top", ItemID.MAGICTRAINING_INFINITYTOP, 400, 400, 4000, 450),
	INFINITY_BOTTOMS("Infinity bottoms", ItemID.MAGICTRAINING_INFINITYBOTTOM, 450, 450, 5000, 500),
	INFINITY_GLOVES("Infinity gloves", ItemID.MAGICTRAINING_INFINITYGLOVES, 175, 175, 1500, 225),
	INFINITY_BOOTS("Infinity boots", ItemID.MAGICTRAINING_INFINITYBOOTS, 120, 120, 1200, 120),
	MAGES_BOOK("Mage's book", ItemID.MAGICTRAINING_BOOKOFMAGIC, 500, 500, 6000, 550),
	BONES_TO_PEACHES("Bones to Peaches spell", ItemID.MAGICTRAINING_PEACHSPELL, 200, 200, 2000, 300),
	// ItemID's gameval name is a historical artifact of Jagex's internal cache naming; 12791 is the MTA Rune pouch.
	RUNE_POUCH("Rune pouch", ItemID.BH_RUNE_POUCH, 150, 150, 1500, 200);

	public static final RewardItem[] ENTRIES = values();

	private final String itemName;
	private final int itemId;
	private final int[] cost = new int[PizazzRoom.ENTRIES.length];

	RewardItem(String itemName, int itemId, int telekineticCost, int graveyardCost, int enchantmentCost, int alchemistCost)
	{
		this.itemName = itemName;
		this.itemId = itemId;
		this.cost[TELEKINETIC.ordinal()] = telekineticCost;
		this.cost[GRAVEYARD.ordinal()] = graveyardCost;
		this.cost[ENCHANTMENT.ordinal()] = enchantmentCost;
		this.cost[ALCHEMIST.ordinal()] = alchemistCost;
	}

	public String itemName()
	{
		return itemName;
	}

	public int itemId()
	{
		return itemId;
	}

	public int cost(PizazzRoom room)
	{
		return cost[room.ordinal()];
	}
}
