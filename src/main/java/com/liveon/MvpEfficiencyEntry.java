package com.liveon;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

@Getter
final class MvpEfficiencyEntry
{
	@SerializedName("player_name")
	private String playerName;
	@SerializedName("account_type")
	private String accountType;
	private double gained;
	private MvpEfficiencyContribution[] breakdown;
}
