package com.camjewell.mtagoals;

import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

/**
 * Tracks progress toward Mage Training Arena reward shop purchases and shows it as an overlay.
 */
@Slf4j
@PluginDescriptor(
	name = "Mage Training Arena Goals",
	description = "Shows progress toward Mage Training Arena reward shop purchases",
	tags = {"mta", "magic", "minigame", "overlay", "pizazz"}
)
public class MtaGoalsPlugin extends Plugin
{
	/**
	 * Shared by the Alchemist's Playground, Creature Graveyard and Enchanting Chamber (each on a
	 * different plane); used as a broad "inside the arena building" check since a confirmed region
	 * for the lobby/Telekinetic Theatre wasn't available, so widget presence is checked as well.
	 */
	private static final int ARENA_REGION = 13462;

	private static final Pattern NUMBER_ONLY = Pattern.compile("^[0-9][0-9,]*$");

	@Inject
	private Client client;

	@Inject
	private MtaGoalsConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private Notifier notifier;

	@Inject
	private GoalOverlay goalOverlay;

	private final Goal goal = new Goal();
	private final int[] currentPoints = new int[PizazzRoom.ENTRIES.length];

	private boolean livePointsAvailable = false;
	private boolean readingFromHud = false;
	private boolean previouslyMetThreshold = false;

	@Provides
	MtaGoalsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MtaGoalsConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(goalOverlay);
		livePointsAvailable = false;
		readingFromHud = false;
		previouslyMetThreshold = false;
		refresh();
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(goalOverlay);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			livePointsAvailable = false;
			readingFromHud = false;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (isInArena())
		{
			refresh();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals(MtaGoalsConfig.CONFIG_GROUP))
		{
			return;
		}
		refresh();
	}

	Goal getGoal()
	{
		return goal;
	}

	boolean isReadingLivePoints()
	{
		return livePointsAvailable;
	}

	/**
	 * @return true if the player is anywhere inside the Mage Training Arena building
	 */
	boolean isInArena()
	{
		Player player = client.getLocalPlayer();
		if (player != null && player.getWorldLocation().getRegionID() == ARENA_REGION)
		{
			return true;
		}
		return client.getWidget(InterfaceID.MAGICTRAINING_MAIN, 0) != null
			|| client.getWidget(InterfaceID.MAGICTRAINING_TELE, 0) != null
			|| client.getWidget(InterfaceID.MAGICTRAINING_SHOP, 0) != null;
	}

	private void refresh()
	{
		updateCurrentPoints();
		goal.recalculate(config, currentPoints);
		checkThresholdNotification();
	}

	private void updateCurrentPoints()
	{
		if (config.useManualPoints())
		{
			currentPoints[PizazzRoom.TELEKINETIC.ordinal()] = config.manualTelekineticPoints();
			currentPoints[PizazzRoom.GRAVEYARD.ordinal()] = config.manualGraveyardPoints();
			currentPoints[PizazzRoom.ENCHANTMENT.ordinal()] = config.manualEnchantmentPoints();
			currentPoints[PizazzRoom.ALCHEMIST.ordinal()] = config.manualAlchemistPoints();
			livePointsAvailable = true;
			readingFromHud = false;
			return;
		}

		int[] hudPoints = tryReadLobbyHud();
		if (hudPoints != null)
		{
			System.arraycopy(hudPoints, 0, currentPoints, 0, currentPoints.length);
			livePointsAvailable = true;
			readingFromHud = true;
		}
		else
		{
			readingFromHud = false;
			// Keep the last known totals (if any) rather than zeroing them out just because the
			// HUD widget isn't on screen right now (e.g. the player stepped into a side room).
		}
	}

	private void checkThresholdNotification()
	{
		boolean nowMet = goal.isMetThreshold();
		if (nowMet && !previouslyMetThreshold)
		{
			notifier.notify(config.thresholdNotification(),
				"You have enough Pizazz points to purchase your selected Mage Training Arena rewards.");
		}
		previouslyMetThreshold = nowMet;
	}

	/**
	 * Best-effort read of the native "points" HUD that OSRS shows while in the MTA lobby
	 * (added per the 2024 Poll 81 quality-of-life vote). The exact widget layout isn't
	 * publicly documented, so this scans every text child of the interface for the four
	 * room names and pulls the nearest number rather than relying on fixed component IDs.
	 * Returns null (rather than a partial result) if any of the four totals can't be found,
	 * so a layout mismatch never reports incorrect numbers.
	 */
	private int[] tryReadLobbyHud()
	{
		Widget root = client.getWidget(InterfaceID.MAGICTRAINING_MAIN, 0);
		if (root == null)
		{
			return null;
		}

		List<Widget> textWidgets = new ArrayList<>();
		collectTextWidgets(root, textWidgets);

		int[] points = new int[PizazzRoom.ENTRIES.length];
		for (PizazzRoom room : PizazzRoom.ENTRIES)
		{
			Integer value = extractRoomPoints(textWidgets, room);
			if (value == null)
			{
				return null;
			}
			points[room.ordinal()] = value;
		}
		return points;
	}

	private void collectTextWidgets(Widget widget, List<Widget> out)
	{
		if (widget == null)
		{
			return;
		}
		if (widget.getType() == WidgetType.TEXT && widget.getText() != null && !widget.getText().isEmpty())
		{
			out.add(widget);
		}
		Widget[] staticChildren = widget.getStaticChildren();
		if (staticChildren != null)
		{
			for (Widget child : staticChildren)
			{
				collectTextWidgets(child, out);
			}
		}
		Widget[] dynamicChildren = widget.getDynamicChildren();
		if (dynamicChildren != null)
		{
			for (Widget child : dynamicChildren)
			{
				collectTextWidgets(child, out);
			}
		}
	}

	private Integer extractRoomPoints(List<Widget> textWidgets, PizazzRoom room)
	{
		Pattern combined = Pattern.compile("(?i)" + Pattern.quote(room.shortName()) + "[^0-9]*([0-9][0-9,]*)");

		for (int i = 0; i < textWidgets.size(); i++)
		{
			String text = Text.removeTags(textWidgets.get(i).getText());
			String lower = text.toLowerCase();
			if (!lower.contains(room.shortName().toLowerCase()) && !lower.contains(room.roomName().toLowerCase()))
			{
				continue;
			}

			Matcher matcher = combined.matcher(text);
			if (matcher.find())
			{
				return parseNumber(matcher.group(1));
			}

			for (int j = i + 1; j < Math.min(i + 3, textWidgets.size()); j++)
			{
				String candidate = Text.removeTags(textWidgets.get(j).getText()).trim();
				if (NUMBER_ONLY.matcher(candidate).matches())
				{
					return parseNumber(candidate);
				}
			}
		}
		return null;
	}

	private int parseNumber(String s)
	{
		return Integer.parseInt(s.replace(",", ""));
	}
}
