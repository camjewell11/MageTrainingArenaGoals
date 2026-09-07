package com.camjewell.mtagoals;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.text.DecimalFormat;
import javax.inject.Inject;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentOrientation;
import net.runelite.client.ui.overlay.components.ImageComponent;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.SplitComponent;
import net.runelite.client.util.QuantityFormatter;

class GoalOverlay extends OverlayPanel
{
	private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.0");
	private static final int BORDER_SIZE = 3;
	private static final int VERTICAL_GAP = 2;
	private static final int ICON_GAP = 5;
	private static final Rectangle TOP_PANEL_BORDER = new Rectangle(2, 0, 4, 4);
	private static final Color PROGRESS_BAR_BACKGROUND_COLOR = new Color(61, 56, 49);

	private final MtaGoalsPlugin plugin;
	private final MtaGoalsConfig config;
	private final ItemManager itemManager;

	private final PanelComponent topPanel = new PanelComponent();

	private BufferedImage cachedRewardIcon;
	private RewardItem cachedRewardItem;

	@Inject
	GoalOverlay(MtaGoalsPlugin plugin, MtaGoalsConfig config, ItemManager itemManager)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;

		setPosition(OverlayPosition.TOP_RIGHT);
		setPriority(PRIORITY_MED);
		panelComponent.setPreferredSize(new Dimension(250, 0));
		panelComponent.setBorder(new Rectangle(BORDER_SIZE, BORDER_SIZE, BORDER_SIZE, BORDER_SIZE));
		panelComponent.setGap(new Point(0, VERTICAL_GAP));

		topPanel.setBorder(TOP_PANEL_BORDER);
		topPanel.setBackgroundColor(null);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Goal goal = plugin.getGoal();

		if (goal.getSelected().isEmpty())
		{
			return null;
		}
		if (config.onlyShowInArena() && !plugin.isInArena())
		{
			return null;
		}

		topPanel.getChildren().clear();
		graphics.setFont(FontManager.getRunescapeSmallFont());

		var topLine = LineComponent.builder()
			.left(goal.getDisplayName())
			.leftFont(FontManager.getRunescapeFont())
			.build();

		var bottomLine = LineComponent.builder()
			.left("Progress:")
			.leftFont(FontManager.getRunescapeFont())
			.right(DECIMAL_FORMAT.format(goal.getOverallProgress() * 100) + "%")
			.rightFont(FontManager.getRunescapeFont())
			.rightColor(goal.isMetThreshold() ? Color.GREEN : Color.WHITE)
			.build();

		var textSplit = SplitComponent.builder()
			.first(topLine)
			.second(bottomLine)
			.orientation(ComponentOrientation.VERTICAL)
			.build();

		var rewardImageComponent = new ImageComponent(getRewardImage(goal.getRepresentativeItem()));
		var topInfoSplit = SplitComponent.builder()
			.first(rewardImageComponent)
			.second(textSplit)
			.orientation(ComponentOrientation.HORIZONTAL)
			.gap(new Point(ICON_GAP, 0))
			.build();

		topPanel.getChildren().add(topInfoSplit);
		panelComponent.getChildren().add(topPanel);

		if (config.showRoomBars())
		{
			for (PizazzRoom room : PizazzRoom.ENTRIES)
			{
				addRoomBar(goal, room);
			}
		}

		if (!plugin.isReadingLivePoints())
		{
			var sourceLine = LineComponent.builder()
				.left(config.useManualPoints() ? "Manual totals" : "Waiting for lobby HUD…")
				.leftColor(Color.GRAY)
				.leftFont(FontManager.getRunescapeSmallFont())
				.build();
			panelComponent.getChildren().add(sourceLine);
		}

		return super.render(graphics);
	}

	private void addRoomBar(Goal goal, PizazzRoom room)
	{
		var data = goal.getRoomProgress(room);
		if (data == null)
		{
			return;
		}

		var progressBar = new OutlinedProgressBarComponent();
		progressBar.setForegroundColor(room.color());
		progressBar.setBackgroundColor(PROGRESS_BAR_BACKGROUND_COLOR);
		progressBar.setPercentage(data.percentageToGoal());
		progressBar.setLeftLabel(room.shortName());
		progressBar.setRightLabel(QuantityFormatter.quantityToStackSize(data.currentAmount())
			+ "/" + QuantityFormatter.quantityToStackSize(data.goalAmount()));

		int remaining = Math.max(data.goalAmount() - data.currentAmount(), 0);
		if (remaining > 0)
		{
			progressBar.setCenterLabel("(" + QuantityFormatter.quantityToStackSize(remaining) + ")");
		}

		panelComponent.getChildren().add(progressBar);
	}

	private BufferedImage getRewardImage(RewardItem rewardItem)
	{
		if (cachedRewardItem != rewardItem)
		{
			cachedRewardItem = rewardItem;
			cachedRewardIcon = itemManager.getImage(rewardItem.itemId());
		}
		return cachedRewardIcon;
	}
}
