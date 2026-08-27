package com.liveon;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class WomMembership
{
	static final int LIVE_ON_GROUP_ID = 1945;
	private static final Set<String> STAFF_ROLES = new HashSet<>(Arrays.asList(
		"owner",
		"deputy_owner",
		"overseer",
		"supervisor",
		"moderator",
		"administrator"
	));

	private WomMembership()
	{
	}

	static Result parse(Gson gson, String json)
	{
		Entry[] memberships = gson.fromJson(json, Entry[].class);
		if (memberships == null)
		{
			return Result.NOT_A_MEMBER;
		}

		for (Entry membership : memberships)
		{
			if (membership != null && membership.liveOnGroupId() == LIVE_ON_GROUP_ID)
			{
				String role = membership.role != null ? membership.role : membership.roleName;
				return new Result(true, role);
			}
		}
		return Result.NOT_A_MEMBER;
	}

	static boolean isStaffRole(String role)
	{
		if (role == null)
		{
			return false;
		}
		return STAFF_ROLES.contains(role.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_'));
	}

	static boolean canPublishBroadcast(String role)
	{
		if (!isStaffRole(role))
		{
			return false;
		}
		String normalizedRole = role.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
		return !"administrator".equals(normalizedRole);
	}

	static String normalizePlayerName(String playerName)
	{
		if (playerName == null)
		{
			return "";
		}
		return playerName.replace('\u00A0', ' ').trim();
	}

	static final class Result
	{
		private static final Result NOT_A_MEMBER = new Result(false, null);
		final boolean member;
		final String role;

		private Result(boolean member, String role)
		{
			this.member = member;
			this.role = role;
		}
	}

	private static final class Entry
	{
		private int groupId;
		private String role;
		private String roleName;
		private Group group;

		private int liveOnGroupId()
		{
			return groupId > 0 ? groupId : group == null ? -1 : group.id;
		}
	}

	private static final class Group
	{
		private int id;
	}
}
