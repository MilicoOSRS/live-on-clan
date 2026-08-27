package com.liveon;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WomMembershipTest
{
	@Test
	public void administratorKeepsStaffAccessWithoutBroadcastAccess()
	{
		assertTrue(WomMembership.isStaffRole("administrator"));
		assertFalse(WomMembership.canPublishBroadcast("administrator"));
	}

	@Test
	public void higherStaffRolesCanPublishBroadcasts()
	{
		assertTrue(WomMembership.canPublishBroadcast("moderator"));
		assertTrue(WomMembership.canPublishBroadcast("deputy_owner"));
		assertTrue(WomMembership.canPublishBroadcast("owner"));
		assertFalse(WomMembership.canPublishBroadcast("member"));
	}
}
