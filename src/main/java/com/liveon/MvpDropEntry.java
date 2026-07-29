package com.liveon;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

@Getter
final class MvpDropEntry
{
	@SerializedName("player_name")
	private String playerName;
	@SerializedName("total_value")
	private long totalValue;
	@SerializedName("drop_count")
	private int dropCount;

	MvpDropEntry()
	{
	}

	MvpDropEntry(String playerName, long totalValue, int dropCount)
	{
		this.playerName = playerName;
		this.totalValue = totalValue;
		this.dropCount = dropCount;
	}
}
