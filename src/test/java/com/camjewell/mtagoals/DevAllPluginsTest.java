package com.camjewell.mtagoals;

import java.util.ArrayList;
import java.util.List;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.plugins.Plugin;

/**
 * Dev-only launcher that sideloads this plugin together with sibling work-in-progress
 * plugins (Death Tracker, Boss Tracker) in a single client, so they can all be exercised
 * together instead of juggling separate client windows/logins. Run via `./gradlew runAll`,
 * which puts the sibling projects' shadowJars on this launcher's runtime classpath but
 * never on the main/test compile classpath - so plain `./gradlew run`/`test` here still
 * work standalone even if the sibling projects don't exist on disk. Reflection is used
 * only for that reason (loading classes that are on the runtime but not compile
 * classpath); this class is never shipped, so it isn't subject to the plugin's own
 * no-reflection rule.
 */
public class DevAllPluginsTest
{
	private static final String[] SIBLING_PLUGIN_CLASSES = {
		"com.camjewell.deathtracker.DeathTrackerPlugin",
		"com.camjewell.bosstracker.BossTrackerPlugin",
	};

	public static void main(String[] args) throws Exception
	{
		List<Class<? extends Plugin>> plugins = new ArrayList<>();
		plugins.add(MtaGoalsPlugin.class);

		for (String className : SIBLING_PLUGIN_CLASSES)
		{
			Class<? extends Plugin> pluginClass = tryLoad(className);
			if (pluginClass != null)
			{
				plugins.add(pluginClass);
			}
		}

		@SuppressWarnings("unchecked")
		Class<? extends Plugin>[] pluginArray = plugins.toArray(new Class[0]);
		ExternalPluginManager.loadBuiltin(pluginArray);
		RuneLite.main(args);
	}

	private static Class<? extends Plugin> tryLoad(String className)
	{
		try
		{
			return Class.forName(className).asSubclass(Plugin.class);
		}
		catch (ClassNotFoundException e)
		{
			System.err.println("Skipping " + className + " - not found on the runAll classpath. "
				+ "Build its shadowJar first: ./gradlew shadowJar in that project's directory, "
				+ "or just run ./gradlew runAll here, which does this automatically.");
			return null;
		}
	}
}
