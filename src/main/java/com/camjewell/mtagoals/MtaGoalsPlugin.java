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
	 * confirmed via Widget Inspector in all four rooms (identical BACK_MODEL/_A/_B/_PTS
	 * widget layout at children 3-6 in each). Confirmed to be the same all-time banked total
	 * as the lobby HUD, not a per-visit delta: reading it in Telekinetic went from 46 (the
	 * last lobby reading) to 48 after completing one maze, matching the wiki's "+2 points
	 * per maze".
	 */
	private static final int MTA_ROOM_PTS_CHILD = 6;

	/**
	 * Planes within {@link #ARENA_REGION} for the Alchemist's Playground, Creature Graveyard
	 * and Enchanting Chamber, matching RuneLite's own built-in MTA plugin. Used as ground truth
	 * for which room's widget to trust: a room's interface can stay loaded (so getWidget()
	 * still returns non-null) after you've left it, just marked Hidden, so "is the widget
	 * non-null" alone isn't reliable for figuring out which room you're actually in.
	 */
	private static final int PLANE_ENCHANTMENT = 0;
	private static final int PLANE_GRAVEYARD = 1;
	private static final int PLANE_ALCHEMIST = 2;

	/**
	 * Enchantment has no fixed points-per-action (it depends on spell level and dragonstone
	 * doubling), so its completion estimate uses an observed points-per-minute rate instead:
	 * points gained since the first reading this session, divided by ticks actually spent in
	 * the room (idle/away time excluded). One game tick is 0.6s, so 100 ticks = 1 minute.
	 */
	private static final int TICKS_PER_MINUTE = 100;

	/**
	 * Minimum active ticks in the Enchanting Chamber before trusting the observed rate enough
	 * to show an estimate, so a couple of lucky/unlucky early casts don't produce a wild number.
	 */
	private static final int MIN_TICKS_FOR_ENCHANT_ESTIMATE = TICKS_PER_MINUTE;

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

	@Inject
	private EstimateOverlay estimateOverlay;

	private final Goal goal = new Goal();
	private final int[] currentPoints = new int[PizazzRoom.ENTRIES.length];

	private boolean livePointsAvailable = false;
	private boolean readingFromHud = false;
	private boolean previouslyMetThreshold = false;

	private int enchantBaselinePoints = -1;
	private int enchantActiveTicks = 0;

	@Provides
	MtaGoalsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MtaGoalsConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(goalOverlay);
		overlayManager.add(estimateOverlay);
		livePointsAvailable = false;
		readingFromHud = false;
		previouslyMetThreshold = false;
		enchantBaselinePoints = -1;
		enchantActiveTicks = 0;
		refresh();
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(goalOverlay);
		overlayManager.remove(estimateOverlay);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			livePointsAvailable = false;
			readingFromHud = false;
			enchantBaselinePoints = -1;
			enchantActiveTicks = 0;
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

		PizazzRoom activeRoom = tryUpdateFromRoomWidget();
		if (activeRoom != null)
		{
			livePointsAvailable = true;
			readingFromHud = true;
			if (activeRoom == PizazzRoom.ENCHANTMENT)
			{
				updateEnchantmentRate();
			}
			return;
		}

		readingFromHud = false;
		// Keep the last known totals (if any) rather than zeroing them out just because neither
		// the lobby HUD nor a room's live counter is on screen right now (e.g. mid-teleport).
	}

	/**
	 * Updates just the one room's total the player is currently playing, from that room's own
	 * live "Pizazz Points:" counter, and returns which room that was. Unlike the lobby HUD this
	 * never touches the other three rooms' totals.
	 */
	private PizazzRoom tryUpdateFromRoomWidget()
	{
		PizazzRoom room = currentRoom();
		if (room == null)
		{
			return null;
		}
		int interfaceId;
		switch (room)
		{
			case TELEKINETIC:
				interfaceId = InterfaceID.MAGICTRAINING_TELE;
				break;
			case GRAVEYARD:
				interfaceId = InterfaceID.MAGICTRAINING_GRAVE;
				break;
			case ENCHANTMENT:
				interfaceId = InterfaceID.MAGICTRAINING_ENCHA;
				break;
			case ALCHEMIST:
				interfaceId = InterfaceID.MAGICTRAINING_ALCHEM;
				break;
			default:
				return null;
		}
		return tryUpdateRoom(room, interfaceId) ? room : null;
	}

	/**
	 * Ground-truth "which room is the player actually standing in", by region/plane rather than
	 * by widget presence - a room's interface can stay loaded (getWidget() still non-null, just
	 * Hidden) after the player has left it, which previously let a stale widget from an
	 * earlier-visited room win a fixed-priority check even while standing in a different room.
	 */
	private PizazzRoom currentRoom()
	{
		Player player = client.getLocalPlayer();
		if (player != null && player.getWorldLocation().getRegionID() == ARENA_REGION)
		{
			switch (player.getWorldLocation().getPlane())
			{
				case PLANE_ENCHANTMENT:
					return PizazzRoom.ENCHANTMENT;
				case PLANE_GRAVEYARD:
					return PizazzRoom.GRAVEYARD;
				case PLANE_ALCHEMIST:
					return PizazzRoom.ALCHEMIST;
				default:
					break;
			}
		}
		// Telekinetic has no confirmed region, so widget visibility is the best available
		// signal - guarded with isHidden() rather than just a non-null check, for the same
		// stale-leftover-widget reason noted above.
		Widget teleWidget = client.getWidget(InterfaceID.MAGICTRAINING_TELE, 0);
		if (teleWidget != null && !teleWidget.isHidden())
		{
			return PizazzRoom.TELEKINETIC;
		}
		return null;
	}

	/**
	 * Advances the Enchanting Chamber's observed-rate tracking by one active tick. Establishes
	 * a fresh baseline (rather than accumulating) the first time this session, or whenever the
	 * total drops below the current baseline (e.g. the player spent points on a purchase),
	 * so the rate never goes negative or counts a purchase as "zero points earned".
	 */
	private void updateEnchantmentRate()
	{
		int points = currentPoints[PizazzRoom.ENCHANTMENT.ordinal()];
		if (enchantBaselinePoints < 0 || points < enchantBaselinePoints)
		{
			enchantBaselinePoints = points;
			enchantActiveTicks = 0;
			return;
		}
		enchantActiveTicks++;
	}

	/**
	 * @return observed Enchantment points-per-minute this session, or null if there isn't yet
	 * enough active-tick data (or no points have been gained) to trust an estimate.
	 */
	Double getEnchantmentPointsPerMinute()
	{
		if (enchantBaselinePoints < 0 || enchantActiveTicks < MIN_TICKS_FOR_ENCHANT_ESTIMATE)
		{
			return null;
		}
		int gained = currentPoints[PizazzRoom.ENCHANTMENT.ordinal()] - enchantBaselinePoints;
		if (gained <= 0)
		{
			return null;
		}
		return gained * (double) TICKS_PER_MINUTE / enchantActiveTicks;
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
		if (widget == null || widget.isHidden() || widget.getText() == null)
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
