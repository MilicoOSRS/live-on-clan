package com.liveon;

import org.junit.Test;
import static org.junit.Assert.*;

public class RaidLootContextTest
{
	@Test
	public void matchesModeAndCountToTheCorrectRaidChest()
	{
		RaidLootContext context = new RaidLootContext();
		context.remember("Chambers of Xeric: Challenge Mode", 12, 100);
		context.remember("Tombs of Amascut: Expert Mode", 34, 101);
		context.remember("Theatre of Blood: Entry Mode", 56, 102);

		assertNull(context.take("Tombs of Amascut", "NPC", 103));
		assertEquals("Tombs of Amascut: Expert Mode",
			context.take("Tombs of Amascut", "EVENT", 103).source);
		assertEquals(12, context.take("Chambers of Xeric", "EVENT", 104).count);
		assertEquals(56, context.take("Theatre of Blood", "EVENT", 105).count);
		assertNull(context.take("Theatre of Blood", "EVENT", 106));
	}

	@Test
	public void rejectsUnrelatedStaleAndCrossSessionData()
	{
		RaidLootContext context = new RaidLootContext();
		context.remember("Tombs of Amascut: Expert Mode", 5, 100);
		assertNull(context.take("Chambers of Xeric", "EVENT", 101));
		assertNull(context.take("Tombs of Amascut", "EVENT", 1101));

		context.remember("Theatre of Blood: Hard Mode", 6, 200);
		context.clear();
		assertNull(context.take("Theatre of Blood", "EVENT", 201));
	}

	@Test
	public void recognizesOnlyGenericRaidNames()
	{
		assertTrue(RaidLootContext.isGenericRaid("Chambers of Xeric"));
		assertTrue(RaidLootContext.isGenericRaid("Tombs of Amascut"));
		assertFalse(RaidLootContext.isGenericRaid("Tombs of Amascut: Expert Mode"));
		assertFalse(RaidLootContext.isGenericRaid("Vorkath"));
	}
}
