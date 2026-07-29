package com.liveon;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Local development launcher used by the Gradle run task.
 */
public class ClanMessagesPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(ClanMessagesPlugin.class);
		RuneLite.main(args);
	}
}
