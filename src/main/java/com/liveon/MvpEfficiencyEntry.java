package com.liveon;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

@Getter
final class MvpEfficiencyEntry
{
	@SerializedName("player_name")
	private String playerName;
	private double gained;
}

