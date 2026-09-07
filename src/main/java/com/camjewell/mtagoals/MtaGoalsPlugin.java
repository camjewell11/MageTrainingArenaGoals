package com.camjewell.mtagoals;

import com.google.inject.Provides;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
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
	 * Every room's completion estimate can show an observed points-per-minute rate alongside
	 * (or, for Enchantment, instead of) its fixed-formula action count. The rate is a rolling
	 * average over the last {@link MtaGoalsConfig#rateWindowMinutes()} minutes of active play in
	 * that room (idle/away time excluded) rather than an all-session average, so it reflects
	 * recent pace - if a player slows down partway through a session, the rate follows that
	 * slowdown once enough recent samples accumulate, instead of staying dragged toward an
	 * earlier faster pace forever. One game tick is 0.6s, so 100 ticks = 1 minute. This rolling
	 * rate is the only way to estimate Enchantment at all (spell level and dragonstone luck vary
	 * too much for a fixed formula); for the other three rooms it turns their exact action count
	 * into a real-world time estimate, since how fast a player completes each action varies.
	 */
	private static final int TICKS_PER_MINUTE = 100;

	/**
	 * Minimum active ticks in a room's rolling window before trusting its observed rate enough
	 * to show an estimate, so a couple of lucky/unlucky early actions don't produce a wild number.
	 */
	private static final int MIN_TICKS_FOR_RATE_ESTIMATE = TICKS_PER_MINUTE;

	private static final int ALCHEMIST_GOLD_PER_POINT = 100;

	/**
	 * The Alchemist's Playground pays out in a room-specific "training gold" currency, not real
	 * Coins (995) - confirmed via Widget Inspector: the inventory slot displaying "Coins" was
	 * actually ItemID.MAGICTRAINING_COINS (8890), and its quantity (150) matched exactly 5 alchs
	 * worth (5 x 30 gold). The _2 through _10000 variants are presumably alternate quantity-tier
	 * sprites for the same stackable currency; checking all of them defends against an ID swap at
	 * a higher stack size that hasn't been observed yet.
	 */
	private static final int[] ALCHEMIST_GOLD_ITEM_IDS = {
		ItemID.MAGICTRAINING_COINS,
		ItemID.MAGICTRAINING_COINS_2,
		ItemID.MAGICTRAINING_COINS_3,
		ItemID.MAGICTRAINING_COINS_4,
		ItemID.MAGICTRAINING_COINS_5,
		ItemID.MAGICTRAINING_COINS_25,
		ItemID.MAGICTRAINING_COINS_100,
		ItemID.MAGICTRAINING_COINS_250,
		ItemID.MAGICTRAINING_COINS_1000,
		ItemID.MAGICTRAINING_COINS_10000,
	};

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

	private final List<Deque<Integer>> roomRateWindows = newRoomRateWindows();

	private PizazzRoom lastRoom = null;
	private int graveyardCapacitySnapshot = -1;

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
		resetRoomRates();
		lastRoom = null;
		graveyardCapacitySnapshot = -1;
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
			resetRoomRates();
			lastRoom = null;
			graveyardCapacitySnapshot = -1;
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
		updateGraveyardCapacitySnapshot();
		goal.recalculate(config, currentPoints);
		checkThresholdNotification();
	}

	/**
	 * Snapshots free inventory capacity once, right when the player enters the Creature
	 * Graveyard, rather than re-scanning every tick. The inventory cycles full/empty
	 * continuously during actual play (fill with fruit, deposit, repeat), so a live per-tick
	 * scan would need to correctly identify the fruit item to exclude it from "reserved" slots -
	 * a snapshot on entry sidesteps that entirely, since it's taken before any fruit is held.
	 */
	private void updateGraveyardCapacitySnapshot()
	{
		PizazzRoom room = currentRoom();
		if (room == PizazzRoom.GRAVEYARD && lastRoom != PizazzRoom.GRAVEYARD)
		{
			graveyardCapacitySnapshot = countEmptyInventorySlots();
		}
		lastRoom = room;
	}

	/**
	 * @return the free-inventory-capacity snapshot taken on entering the Creature Graveyard this
	 * visit, or 0 if the player hasn't been in the room yet this session.
	 */
	int getGraveyardCapacity()
	{
		return Math.max(graveyardCapacitySnapshot, 0);
	}

	private int countEmptyInventorySlots()
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory == null)
		{
			return 0;
		}
		int empty = 0;
		for (Item item : inventory.getItems())
		{
			if (item.getId() == -1)
			{
				empty++;
			}
		}
		return empty;
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
			updateRoomRate(activeRoom);
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

	private static List<Deque<Integer>> newRoomRateWindows()
	{
		List<Deque<Integer>> windows = new ArrayList<>();
		for (int i = 0; i < PizazzRoom.ENTRIES.length; i++)
		{
			windows.add(new ArrayDeque<>());
		}
		return windows;
	}

	private void resetRoomRates()
	{
		for (Deque<Integer> window : roomRateWindows)
		{
			window.clear();
		}
	}

	/**
	 * Records the given room's current effective points as one more sample in its rolling
	 * window - one sample per active tick, oldest samples dropped once the window exceeds
	 * {@link MtaGoalsConfig#rateWindowMinutes()}. Clears the window (rather than accumulating)
	 * whenever the value drops below the most recent sample (e.g. the player spent points on a
	 * purchase), since that's not a real slowdown and would otherwise show a negative rate.
	 */
	private void updateRoomRate(PizazzRoom room)
	{
		Deque<Integer> window = roomRateWindows.get(room.ordinal());
		int points = effectivePointsForRate(room);

		if (!window.isEmpty() && points < window.peekLast())
		{
			window.clear();
		}

		window.addLast(points);

		int maxSamples = Math.max(config.rateWindowMinutes(), 1) * TICKS_PER_MINUTE + 1;
		while (window.size() > maxSamples)
		{
			window.removeFirst();
		}
	}

	/**
	 * The value fed into a room's rate window. Alchemist earns training gold into the inventory
	 * well before it's deposited into banked points, so the banked total alone can stay flat for
	 * minutes at a time even while the player is actively alching - without this, the rate would
	 * never see any gain between deposits and getPointsPerMinute() would stay null forever under
	 * a "hold gold, deposit occasionally" playstyle. Folding held gold in as fractional
	 * banked-point-equivalents keeps the rate responsive to real-time progress instead.
	 */
	private int effectivePointsForRate(PizazzRoom room)
	{
		int banked = currentPoints[room.ordinal()];
		if (room == PizazzRoom.ALCHEMIST)
		{
			return banked + getAlchemistGold() / ALCHEMIST_GOLD_PER_POINT;
		}
		return banked;
	}

	/**
	 * @return training gold (see {@link #ALCHEMIST_GOLD_ITEM_IDS}) currently held in the
	 * inventory, alched but not yet deposited.
	 */
	int getAlchemistGold()
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory == null)
		{
			return 0;
		}
		int gold = 0;
		for (Item item : inventory.getItems())
		{
			for (int goldItemId : ALCHEMIST_GOLD_ITEM_IDS)
			{
				if (item.getId() == goldItemId)
				{
					gold += item.getQuantity();
					break;
				}
			}
		}
		return gold;
	}

	/**
	 * @return how many Alchemist points the currently held (but not yet deposited) training gold
	 * is worth once deposited - used to show how far depositing would push progress.
	 */
	int getAlchemistHeldPoints()
	{
		return getAlchemistGold() / ALCHEMIST_GOLD_PER_POINT;
	}

	/**
	 * @return the given room's points-per-minute rate over its rolling window, or null if there
	 * isn't yet enough active-tick data (or no points have been gained) to trust an estimate.
	 */
	Double getPointsPerMinute(PizazzRoom room)
	{
		Deque<Integer> window = roomRateWindows.get(room.ordinal());
		if (window.size() < MIN_TICKS_FOR_RATE_ESTIMATE + 1)
		{
			return null;
		}
		int gained = window.peekLast() - window.peekFirst();
		if (gained <= 0)
		{
			return null;
		}
		int ticksElapsed = window.size() - 1;
		return gained * (double) TICKS_PER_MINUTE / ticksElapsed;
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
