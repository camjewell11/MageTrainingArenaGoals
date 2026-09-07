package com.camjewell.mtagoals;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class MtaGoalsPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(MtaGoalsPlugin.class);
		RuneLite.main(args);
	}
}
