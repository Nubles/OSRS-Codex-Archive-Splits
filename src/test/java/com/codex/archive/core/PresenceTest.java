package com.codex.archive.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.codex.archive.core.Presence.Action;
import org.junit.Test;

public class PresenceTest
{
	@Test
	public void anOpenWorldLobbyThenTheInstance()
	{
		Presence p = new Presence();
		p.lobbyNpc(true, 1);
		assertEquals(Action.NONE, p.tick(2, false, false, 0));
		assertTrue(p.isInLobby());
		p.sceneLoaded(10);
		p.lobbyNpc(false, 10);
		assertEquals(Action.NONE, p.tick(11, true, false, 0));
		assertEquals(Action.NONE, p.tick(12, true, false, 0));
		assertEquals(Action.BEGIN, p.tick(13, true, false, 0));
		assertEquals(11, p.beginTick());
		assertEquals(Action.NONE, p.tick(14, true, true, 3));
		// Out through the exit.
		assertEquals(Action.END, p.tick(800, false, true, 789));
	}

	@Test
	public void aLobbyThatIsItselfAnInstance()
	{
		Presence p = new Presence();
		p.sceneLoaded(1);
		p.tick(2, true, false, 0);
		p.lobbyNpc(true, 3);
		assertEquals("the lobby is not the archive", Action.NONE, p.tick(3, true, false, 0));
		assertEquals(Action.NONE, p.tick(4, true, false, 0));
		assertEquals(Action.NONE, p.tick(40, true, false, 0));
		p.sceneLoaded(50);
		p.lobbyNpc(false, 50);
		p.tick(51, true, false, 0);
		p.tick(52, true, false, 0);
		assertEquals(Action.BEGIN, p.tick(53, true, false, 0));
		assertEquals(Action.NONE, p.tick(300, true, true, 249));
		// Back to the lobby: another instance, with its NPCs about again.
		p.sceneLoaded(900);
		p.lobbyNpc(true, 901);
		assertEquals(Action.END, p.tick(901, true, true, 850));
	}

	@Test
	public void anyOtherInstanceIsNotTheArchive()
	{
		Presence p = new Presence();
		p.sceneLoaded(5);
		assertEquals("no lobby seen", Action.NONE, p.tick(8, true, false, 0));
		p.lobbyNpc(true, 10);
		p.lobbyNpc(false, 20);
		assertEquals(Action.NONE, p.tick(21, false, false, 0));
		p.sceneLoaded(400);
		assertEquals("long after the lobby", Action.NONE, p.tick(403, true, false, 0));
	}

	@Test
	public void lateStartGuessesTheDoor()
	{
		Presence p = new Presence();
		p.lobbyNpc(true, 1);
		p.tick(1, true, false, 0);
		p.lobbyNpc(false, 60);
		assertEquals("left the lobby at 60", 60, p.likelyStart(90));
		p.reset();
		assertFalse(p.isInLobby());
		assertFalse(p.isInInstance());
	}
}
