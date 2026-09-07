package com.camjewell.mtagoals;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.function.DoubleUnaryOperator;
import javax.inject.Inject;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

/**
 * Estimates how much longer it'll take to reach the tracked reward goal, per room. Telekinetic,
 * Alchemist and Graveyard each have a fixed points-per-action rate the game defines, so they're
 * shown as actions remaining; Enchantment has no such fixed rate (it depends on spell level and
 * dragonstone doubling), so it's shown as an estimated time remaining based on this session's
 * observed rate instead.
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

	@Inject
	EstimateOverlay(MtaGoalsPlugin plugin, MtaGoalsConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;

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

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Estimated remaining")
			.leftFont(FontManager.getRunescapeBoldFont())
			.build());

		addActionsRow("Telekinetic", goal, PizazzRoom.TELEKINETIC, "mazes",
			remaining -> Math.ceil(remaining / TELEKINETIC_AVG_POINTS_PER_MAZE));

		addActionsRow("Alchemist", goal, PizazzRoom.ALCHEMIST, "items",
			remaining -> Math.ceil(remaining * (double) ALCHEMIST_GOLD_PER_POINT / ALCHEMIST_GOLD_PER_ALCH));

		int pointsPerInventory = GRAVEYARD_INVENTORY_SIZE / config.graveyardFruit().fruitPerPoint();
		addActionsRow("Graveyard", goal, PizazzRoom.GRAVEYARD, "inventories",
			remaining -> Math.ceil(remaining / (double) pointsPerInventory));

		addEnchantmentRow(goal);

		return super.render(graphics);
	}

	private void addActionsRow(String label, Goal goal, PizazzRoom room, String unit, java.util.function.DoubleUnaryOperator actionsForRemaining)
	{
		Goal.RoomProgress progress = goal.getRoomProgress(room);
		if (progress == null)
		{
			return;
		}

		int remaining = Math.max(progress.goalAmount() - progress.currentAmount(), 0);
		String right;
		Color rightColor;
		if (remaining <= 0)
		{
			right = "Done";
			rightColor = Color.GREEN;
		}
		else
		{
			int actions = (int) actionsForRemaining.applyAsDouble(remaining);
			right = actions + " " + unit;
			rightColor = Color.WHITE;
		}

		panelComponent.getChildren().add(LineComponent.builder()
			.left(label + ":")
			.leftFont(FontManager.getRunescapeFont())
			.right(right)
			.rightFont(FontManager.getRunescapeFont())
			.rightColor(rightColor)
			.build());
	}

	private void addEnchantmentRow(Goal goal)
	{
		Goal.RoomProgress progress = goal.getRoomProgress(PizazzRoom.ENCHANTMENT);
		if (progress == null)
		{
			return;
		}

		int remaining = Math.max(progress.goalAmount() - progress.currentAmount(), 0);
		String right;
		Color rightColor;
		if (remaining <= 0)
		{
			right = "Done";
			rightColor = Color.GREEN;
		}
		else
		{
			Double pointsPerMinute = plugin.getEnchantmentPointsPerMinute();
			if (pointsPerMinute == null)
			{
				right = "collecting data…";
				rightColor = Color.GRAY;
			}
			else
			{
				int minutes = (int) Math.ceil(remaining / pointsPerMinute);
				right = "~" + minutes + " min";
				rightColor = Color.WHITE;
			}
		}

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Enchantment:")
			.leftFont(FontManager.getRunescapeFont())
			.right(right)
			.rightFont(FontManager.getRunescapeFont())
			.rightColor(rightColor)
			.build());
	}
}
