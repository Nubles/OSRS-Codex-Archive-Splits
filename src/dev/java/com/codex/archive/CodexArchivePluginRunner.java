package com.codex.archive;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class CodexArchivePluginRunner
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(CodexArchivePlugin.class);
		RuneLite.main(args);
	}
}
