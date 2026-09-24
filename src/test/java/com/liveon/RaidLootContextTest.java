package com.liveon;

import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class RaidLootContextTest
{
	// Real message from a solo CoX completion that dropped a unique (screenshot: "Your
	// completed Chambers of Xeric count is: 1,360." followed by "Special loot: ... Milico -
	// Twisted buckler"). This message is what lets the reward chest resolve a non-null
	// completion instead of falling back to killCount == null.
	@Test
	public void parsesRealKillCountMessageAndFeedsRaidLootContext()
	{
		Map.Entry<String, Integer> parsed = ClanMessagesPlugin.parseLootCountMessage(
			"Your completed Chambers of Xeric count is: 1,360.");
		assertEquals("Chambers of Xeric", parsed.getKey());
		assertEquals(1360, (int) parsed.getValue());

		RaidLootContext context = new RaidLootContext();
		context.remember(parsed.getKey(), parsed.getValue(), 100);
		RaidLootContext.Completion completion = context.take("Chambers of Xeric", "EVENT", 103);
		assertNotNull("the chest should resolve a completion when the count message arrived first", completion);
		assertEquals(1360, completion.count);
	}

	@Test
	public void aChestWithNoPrecedingCountMessageStillResolvesWithNullCompletion()
	{
		// This is the solo CoX run we actually tested: no "Your completed ... count is" message
		// appeared at all, so the chest must fall back to a null completion rather than crash.
		RaidLootContext context = new RaidLootContext();
		assertNull(context.take("Chambers of Xeric", "EVENT", 100));
	}

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
		assertNull(context.take("Tombs of Amascut", "EVENT", 201));

		context.remember("Theatre of Blood: Hard Mode", 6, 200);
		context.clear();
		assertNull(context.take("Theatre of Blood", "EVENT", 201));
	}

	@Test
	public void staleChallengeModeCannotRelabelALaterNormalCoxChest()
	{
		RaidLootContext context = new RaidLootContext();
		context.remember("Chambers of Xeric: Challenge Mode", 20, 100);

		// Past MAX_AGE_TICKS (100) - still nowhere near the several minutes a real raid
		// takes, so this can't be confused with an actually-late completion message.
		assertNull(context.take("Chambers of Xeric", "EVENT", 201));
	}

	// Real measured gap from a live ToB/ToA test: the kill-count message arrived ~26
	// real seconds (~43 ticks) before the chest. The old 10-tick window left kc null for
	// this; the widened window must resolve it.
	@Test
	public void resolvesAKillCountMessageThatArrivedRealSecondsBeforeTheChest()
	{
		RaidLootContext context = new RaidLootContext();
		context.remember("Theatre of Blood: Entry Mode", 69, 100);
		RaidLootContext.Completion completion = context.take("Theatre of Blood", "EVENT", 143);
		assertNotNull(completion);
		assertEquals(69, completion.count);
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
