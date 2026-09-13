package com.liveon;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.EqualsAndHashCode;

@Getter
@EqualsAndHashCode
final class MvpEfficiencyEntry
{
	@SerializedName("player_name")
	private String playerName;
	@SerializedName("account_type")
	private String accountType;
	private double gained;
	private MvpEfficiencyContribution[] breakdown;
	private int position;
	private transient int positionChange;

	void setPositionChange(int positionChange)
	{
		this.positionChange = positionChange;
	}
}
