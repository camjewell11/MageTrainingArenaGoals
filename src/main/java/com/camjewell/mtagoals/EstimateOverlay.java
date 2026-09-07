package com.camjewell.mtagoals;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.function.DoubleUnaryOperator;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

/**
 * Estimates how much longer it'll take to reach the tracked reward goal, per room. Telekinetic
 * and Graveyard have a fixed points-per-action rate the game defines, so they're shown as
 * actions remaining. Alchemist is likewise fixed, but also nets out gold already sitting
 * un-deposited in the inventory. Enchantment has no fixed rate at all (it depends on spell
 * level, dragonstone luck and playstyle), so it's shown as an estimated time remaining based on
 * this session's observed rate instead.
 */
class EstimateOverlay extends OverlayPanel
{
	/**
	 * Telekinetic Theatre awards 2 points per maze, plus an 8-point bonus every 5th consecutive
	 * maze (18 points per 5-maze cycle) - this is that cycle's steady-state average, rather than
	 * tracking exact streak position.
	 */
	private static final double TELEKINETIC_AVG_POINTS_PER_MAZE = (5 * 2 + 8) / 5.0;

	private static final int ALCHEMIST_GOLD_PER_ALCH = 30;
	private static final int ALCHEMIST_GOLD_PER_POINT = 100;

	private static final int GRAVEYARD_INVENTORY_SIZE = 23;

	private final MtaGoalsPlugin plugin;
	private final MtaGoalsConfig config;
	private final Client client;

	@Inject
	EstimateOverlay(MtaGoalsPlugin plugin, MtaGoalsConfig config, Client client)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		this.client = client;

		setPosition(OverlayPosition.TOP_RIGHT);
		setPriority(PRIORITY_LOW);
		panelComponent.setPreferredSize(new Dimension(250, 0));
		panelComponent.setBorder(new Rectangle(3, 3, 3, 3));
		panelComponent.setGap(new Point(0, 2));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showEstimatesOverlay())
		{
			return null;
		}

		Goal goal = plugin.getGoal();
		if (goal.getSelected().isEmpty())
		{
			return null;
		}
		if (config.onlyShowInArena() && !plugin.isInArena())
		{
			return null;
		}

		graphics.setFont(FontManager.getRunescapeSmallFont());

		addRow("Estimated remaining", null, null, true);

		addActionsRow("Telekinetic", goal, PizazzRoom.TELEKINETIC, "mazes",
			remaining -> Math.ceil(remaining / TELEKINETIC_AVG_POINTS_PER_MAZE));

		addAlchemistRow(goal);

		int pointsPerInventory = GRAVEYARD_INVENTORY_SIZE / config.graveyardFruit().fruitPerPoint();
		addActionsRow("Graveyard", goal, PizazzRoom.GRAVEYARD, "inventories",
			remaining -> Math.ceil(remaining / (double) pointsPerInventory));

		addEnchantmentRow(goal);

		return super.render(graphics);
	}

	private void addActionsRow(String label, Goal goal, PizazzRoom room, String unit, DoubleUnaryOperator actionsForRemaining)
	{
		Goal.RoomProgress progress = goal.getRoomProgress(room);
		if (progress == null)
		{
			return;
		}

		int remaining = Math.max(progress.goalAmount() - progress.currentAmount(), 0);
		if (remaining <= 0)
		{
			addRow(label + ":", "Done", Color.GREEN, false);
			return;
		}

		int actions = (int) actionsForRemaining.applyAsDouble(remaining);
		addRow(label + ":", actions + " " + unit, Color.WHITE, false);
	}

	/**
	 * Nets out gold already sitting in the inventory (alched but not yet deposited) against the
	 * gold still needed, so the item count ticks down the instant you alch something rather than
	 * only after you walk over and deposit it.
	 */
	private void addAlchemistRow(Goal goal)
	{
		Goal.RoomProgress progress = goal.getRoomProgress(PizazzRoom.ALCHEMIST);
		if (progress == null)
		{
			return;
		}

		int pointsRemaining = Math.max(progress.goalAmount() - progress.currentAmount(), 0);
		if (pointsRemaining <= 0)
		{
			addRow("Alchemist:", "Done", Color.GREEN, false);
			return;
		}

		int goldNeeded = pointsRemaining * ALCHEMIST_GOLD_PER_POINT;
		int netGoldNeeded = Math.max(goldNeeded - getInventoryCoins(), 0);
		int items = (int) Math.ceil(netGoldNeeded / (double) ALCHEMIST_GOLD_PER_ALCH);
		addRow("Alchemist:", items + " items", Color.WHITE, false);
	}

	private void addEnchantmentRow(Goal goal)
	{
		Goal.RoomProgress progress = goal.getRoomProgress(PizazzRoom.ENCHANTMENT);
		if (progress == null)
		{
			return;
		}

		int remaining = Math.max(progress.goalAmount() - progress.currentAmount(), 0);
		if (remaining <= 0)
		{
			addRow("Enchantment:", "Done", Color.GREEN, false);
			return;
		}

		Double pointsPerMinute = plugin.getEnchantmentPointsPerMinute();
		if (pointsPerMinute == null)
		{
			addRow("Enchantment:", "collecting data…", Color.GRAY, false);
			return;
		}

		int minutes = (int) Math.ceil(remaining / pointsPerMinute);
		addRow("Enchantment:", "~" + minutes + " min", Color.WHITE, false);
	}

	private int getInventoryCoins()
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory == null)
		{
			return 0;
		}
		int coins = 0;
		for (Item item : inventory.getItems())
		{
			if (item.getId() == ItemID.COINS)
			{
				coins += item.getQuantity();
			}
		}
		return coins;
	}

	private void addRow(String left, String right, Color rightColor, boolean header)
	{
		var line = LineComponent.builder()
			.left(left)
			.leftFont(header ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeFont());
		if (right != null)
		{
			line.right(right)
				.rightFont(FontManager.getRunescapeFont())
				.rightColor(rightColor);
		}
		panelComponent.getChildren().add(line.build());
	}
}
