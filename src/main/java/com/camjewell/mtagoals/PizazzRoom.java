package com.camjewell.mtagoals;

import java.awt.Color;

/**
 * The four independent Pizazz point currencies earned in the Mage Training Arena.
 * Every reward shop purchase costs a combination of all four.
 */
public enum PizazzRoom
{
	TELEKINETIC("Telekinetic", "Telekinetic Theatre", Color.decode("#42a5f5")),
	GRAVEYARD("Graveyard", "Creature Graveyard", Color.decode("#8d6e63")),
	ENCHANTMENT("Enchantment", "Enchanting Chamber", Color.decode("#ab47bc")),
	ALCHEMIST("Alchemist", "Alchemist's Playground", Color.decode("#66bb6a"));

	public static final PizazzRoom[] ENTRIES = values();

	private final String shortName;
	private final String roomName;
	private final Color color;

	PizazzRoom(String shortName, String roomName, Color color)
	{
		this.shortName = shortName;
		this.roomName = roomName;
		this.color = color;
	}

	public String shortName()
	{
		return shortName;
	}

	public String roomName()
	{
		return roomName;
	}

	public Color color()
	{
		return color;
	}
}
