package com.camjewell.mtagoals;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates the currently ticked reward items into a single combined target per
 * {@link PizazzRoom}, and tracks progress toward that target given the player's
 * current point totals.
 */
class Goal
{
	private final List<RewardItem> selected = new ArrayList<>();
	private String displayName = "";
	private RewardItem representativeItem = RewardItem.NONE;
	private double overallProgress = 0.0;
	private boolean metThreshold = false;

	private final Map<PizazzRoom, RoomProgress> roomProgress = new EnumMap<>(PizazzRoom.class);

	List<RewardItem> getSelected()
	{
		return selected;
	}

	String getDisplayName()
	{
		return displayName;
	}

	RewardItem getRepresentativeItem()
	{
		return representativeItem;
	}

	double getOverallProgress()
	{
		return overallProgress;
	}

	boolean isMetThreshold()
	{
		return metThreshold;
	}

	RoomProgress getRoomProgress(PizazzRoom room)
	{
		return roomProgress.get(room);
	}

	void recalculate(MtaGoalsConfig config, int[] currentPoints)
	{
		selected.clear();
		if (config.trackBeginnerWand())
		{
			selected.add(RewardItem.BEGINNER_WAND);
		}
		if (config.trackApprenticeWand())
		{
			selected.add(RewardItem.APPRENTICE_WAND);
		}
		if (config.trackTeacherWand())
		{
			selected.add(RewardItem.TEACHER_WAND);
		}
		if (config.trackMasterWand())
		{
			selected.add(RewardItem.MASTER_WAND);
		}
		if (config.trackInfinityHat())
		{
			selected.add(RewardItem.INFINITY_HAT);
		}
		if (config.trackInfinityTop())
		{
			selected.add(RewardItem.INFINITY_TOP);
		}
		if (config.trackInfinityBottoms())
		{
			selected.add(RewardItem.INFINITY_BOTTOMS);
		}
		if (config.trackInfinityGloves())
		{
			selected.add(RewardItem.INFINITY_GLOVES);
		}
		if (config.trackInfinityBoots())
		{
			selected.add(RewardItem.INFINITY_BOOTS);
		}
		if (config.trackMagesBook())
		{
			selected.add(RewardItem.MAGES_BOOK);
		}
		if (config.trackBonesToPeaches())
		{
			selected.add(RewardItem.BONES_TO_PEACHES);
		}
		if (config.trackRunePouch())
		{
			selected.add(RewardItem.RUNE_POUCH);
		}

		if (selected.isEmpty())
		{
			displayName = "";
			representativeItem = RewardItem.NONE;
		}
		else if (selected.size() == 1)
		{
			displayName = selected.get(0).itemName();
			representativeItem = selected.get(0);
		}
		else
		{
			displayName = "Selected rewards (" + selected.size() + ")";
			representativeItem = selected.get(0);
		}

		for (PizazzRoom room : PizazzRoom.ENTRIES)
		{
			int goalAmount = 0;
			for (RewardItem item : selected)
			{
				goalAmount += item.cost(room);
			}
			roomProgress.put(room, new RoomProgress(currentPoints[room.ordinal()], goalAmount));
		}

		overallProgress = roomProgress.values().stream()
			.mapToDouble(RoomProgress::percentageToGoal)
			.average()
			.orElse(0.0);

		metThreshold = !selected.isEmpty() && roomProgress.values().stream().allMatch(RoomProgress::isMet);
	}

	static final class RoomProgress
	{
		private final int currentAmount;
		private final int goalAmount;
		private final double percentageToGoal;

		RoomProgress(int currentAmount, int goalAmount)
		{
			this.currentAmount = currentAmount;
			this.goalAmount = goalAmount;
			this.percentageToGoal = goalAmount == 0 ? 1.0 : Math.min((double) currentAmount / goalAmount, 1.0);
		}

		int currentAmount()
		{
			return currentAmount;
		}

		int goalAmount()
		{
			return goalAmount;
		}

		double percentageToGoal()
		{
			return percentageToGoal;
		}

		boolean isMet()
		{
			return currentAmount >= goalAmount;
		}
	}
}
