package com.camjewell.mtagoals;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
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

	/**
	 * Child component IDs under {@link InterfaceID#MAGICTRAINING_MAIN}, confirmed via RuneLite's
	 * Widget Inspector: 6-9 are the "Telekinetic:"/"Alchemist:"/"Enchantment:"/"Graveyard:" labels,
	 * 10-13 are the paired point-total values in the same order (e.g. child 6's label pairs with
	 * child 10's value). Live-verified against a real account's totals in-game.
	 */
	private static final int MTA_MAIN_VALUE_TELEKINETIC = 10;
	private static final int MTA_MAIN_VALUE_ALCHEMIST = 11;
	private static final int MTA_MAIN_VALUE_ENCHANTMENT = 12;
	private static final int MTA_MAIN_VALUE_GRAVEYARD = 13;

	/**
	 * Each room also shows a live "Pizazz Points:" counter for just its own currency while
	 * you're actually playing it, at the same child ID (6) in every room's interface -
	 * confirmed via Widget Inspector for Telekinetic, Graveyard and Enchantment (identical
	 * BACK_MODEL/_A/_B/_PTS widget layout at children 3-6 in all three), inferred for
	 * Alchemist from that same pattern. Confirmed to be the same all-time banked total as
	 * the lobby HUD, not a per-visit delta: reading it in Telekinetic went from 46 (the last
	 * lobby reading) to 48 after completing one maze, matching the wiki's "+2 points per maze".
	 */
	private static final int MTA_ROOM_PTS_CHILD = 6;

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
			return;
		}

		if (tryUpdateFromRoomWidget())
		{
			livePointsAvailable = true;
			readingFromHud = true;
			return;
		}

		readingFromHud = false;
		// Keep the last known totals (if any) rather than zeroing them out just because neither
		// the lobby HUD nor a room's live counter is on screen right now (e.g. mid-teleport).
	}

	/**
	 * Updates just the one room's total the player is currently playing, from that room's own
	 * live "Pizazz Points:" counter. Unlike the lobby HUD this never touches the other three
	 * rooms' totals, since only one room's widget can be on screen at a time.
	 */
	private boolean tryUpdateFromRoomWidget()
	{
		return tryUpdateRoom(PizazzRoom.TELEKINETIC, InterfaceID.MAGICTRAINING_TELE)
			|| tryUpdateRoom(PizazzRoom.GRAVEYARD, InterfaceID.MAGICTRAINING_GRAVE)
			|| tryUpdateRoom(PizazzRoom.ENCHANTMENT, InterfaceID.MAGICTRAINING_ENCHA)
			|| tryUpdateRoom(PizazzRoom.ALCHEMIST, InterfaceID.MAGICTRAINING_ALCHEM);
	}

	private boolean tryUpdateRoom(PizazzRoom room, int interfaceId)
	{
		Integer value = readValue(interfaceId, MTA_ROOM_PTS_CHILD);
		if (value == null)
		{
			return false;
		}
		currentPoints[room.ordinal()] = value;
		return true;
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
	 * Reads the native "points" HUD that OSRS shows while in the MTA lobby (added per the
	 * 2024 Poll 81 quality-of-life vote), via the fixed component IDs confirmed above.
	 * Returns null (rather than a partial result) if the HUD isn't on screen or any of the
	 * four totals fail to parse, so an unexpected layout never reports incorrect numbers.
	 */
	private int[] tryReadLobbyHud()
	{
		Integer telekinetic = readValue(InterfaceID.MAGICTRAINING_MAIN, MTA_MAIN_VALUE_TELEKINETIC);
		Integer alchemist = readValue(InterfaceID.MAGICTRAINING_MAIN, MTA_MAIN_VALUE_ALCHEMIST);
		Integer enchantment = readValue(InterfaceID.MAGICTRAINING_MAIN, MTA_MAIN_VALUE_ENCHANTMENT);
		Integer graveyard = readValue(InterfaceID.MAGICTRAINING_MAIN, MTA_MAIN_VALUE_GRAVEYARD);

		if (telekinetic == null || alchemist == null || enchantment == null || graveyard == null)
		{
			return null;
		}

		int[] points = new int[PizazzRoom.ENTRIES.length];
		points[PizazzRoom.TELEKINETIC.ordinal()] = telekinetic;
		points[PizazzRoom.ALCHEMIST.ordinal()] = alchemist;
		points[PizazzRoom.ENCHANTMENT.ordinal()] = enchantment;
		points[PizazzRoom.GRAVEYARD.ordinal()] = graveyard;
		return points;
	}

	private Integer readValue(int interfaceId, int childId)
	{
		Widget widget = client.getWidget(interfaceId, childId);
		if (widget == null || widget.getText() == null)
		{
			return null;
		}
		try
		{
			return Integer.parseInt(Text.removeTags(widget.getText()).replace(",", "").trim());
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}
}
