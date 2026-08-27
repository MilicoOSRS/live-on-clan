package com.liveon;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

final class ClanTag
{
	int id;
	String code;
	String color;
	List<ClanTagMember> members = new ArrayList<>();

	@Override
	public String toString()
	{
		return code == null ? "" : code;
	}
}

final class ClanTagMember
{
	int id;
	@SerializedName("player_name")
	String playerName;
}

final class ClanTagsResponse
{
	boolean canManage;
	List<ClanTag> tags = new ArrayList<>();
}
