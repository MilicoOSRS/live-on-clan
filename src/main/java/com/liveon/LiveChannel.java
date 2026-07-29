package com.liveon;

import com.google.gson.annotations.SerializedName;

final class LiveChannel
{
	int id;
	@SerializedName("player_name")
	String playerName;
	@SerializedName("twitch_login")
	String twitchLogin;
	String url;
	boolean online;
}
