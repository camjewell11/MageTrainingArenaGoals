package com.camjewell.mtagoals;

/**
 * The fruit deposited in the Creature Graveyard hopper - peaches are worth twice as many
 * points per fruit as bananas (1 point per 8 peaches vs. 1 point per 16 bananas).
 */
public enum GraveyardFruit
{
	BANANAS("Bananas", 16),
	PEACHES("Peaches", 8);

	private final String displayName;
	private final int fruitPerPoint;

	GraveyardFruit(String displayName, int fruitPerPoint)
	{
		this.displayName = displayName;
		this.fruitPerPoint = fruitPerPoint;
	}

	public int fruitPerPoint()
	{
		return fruitPerPoint;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
