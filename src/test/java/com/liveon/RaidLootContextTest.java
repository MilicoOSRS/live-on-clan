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
		assertNull(context.take("Tombs of Amascut", "EVENT", 111));

		context.remember("Theatre of Blood: Hard Mode", 6, 200);
		context.clear();
		assertNull(context.take("Theatre of Blood", "EVENT", 201));
	}

	@Test
	public void staleChallengeModeCannotRelabelALaterNormalCoxChest()
	{
		RaidLootContext context = new RaidLootContext();
		context.remember("Chambers of Xeric: Challenge Mode", 20, 100);

		assertNull(context.take("Chambers of Xeric", "EVENT", 111));
	}

	@Test
	public void recognizesOnlyGenericRaidNames()
	{
		assertTrue(RaidLootContext.isGenericRaid("Chambers of Xeric"));
		assertTrue(RaidLootContext.isGenericRaid("Tombs of Amascut"));
		assertFalse(RaidLootContext.isGenericRaid("Tombs of Amascut: Expert Mode"));
		assertFalse(RaidLootContext.isGenericRaid("Vorkath"));
	}

	@Test
	public void simulatesBothMessageOrdersWithoutSuppressingRaidLoot()
	{
		String[][] cases = {
			{"Chambers of Xeric", "Chambers of Xeric: Challenge Mode"},
			{"Theatre of Blood", "Theatre of Blood: Hard Mode"},
			{"Tombs of Amascut", "Tombs of Amascut: Expert Mode"}
		};
		for (String[] raid : cases)
		{
			RaidLootContext before = new RaidLootContext();
			before.remember(raid[1], 42, 100);
			assertEquals(raid[1], before.take(raid[0], "EVENT", 101).source);
			assertNull(before.take(raid[0], "EVENT", 101));

			RaidLootContext after = new RaidLootContext();
			assertNull(after.take(raid[0], "EVENT", 100));
			after.remember(raid[1], 42, 101);
			assertEquals(raid[1], after.take(raid[0], "EVENT", 101).source);
		}
	}
}
