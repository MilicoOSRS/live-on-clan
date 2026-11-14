package com.liveon;

import com.google.gson.JsonObject;

final class RecentActivity
{
	static final String DROP_OF_DAY = "DROP_OF_DAY";

	String type;
	String player_name;
	String title;

	/** The pinned card shown while nobody has dropped anything today. */
	static RecentActivity noDropToday()
	{
		RecentActivity activity = new RecentActivity();
		activity.type = DROP_OF_DAY;
		activity.player_name = "";
		activity.title = "Drop do dia: Nenhum drop hoje ainda. Seja o primeiro!";
		return activity;
	}

	/** Builds the pinned "Drop do dia" row from the site's top-today payload; null when there is no drop yet. */
	static RecentActivity dropOfDay(JsonObject drop)
	{
		if (drop == null || !drop.has("player_name") || drop.get("player_name").isJsonNull()
			|| !drop.has("item_name") || drop.get("item_name").isJsonNull()) return null;
		long quantity = drop.has("quantity") && !drop.get("quantity").isJsonNull() ? drop.get("quantity").getAsLong() : 1;
		long value = drop.has("value") && !drop.get("value").isJsonNull() ? drop.get("value").getAsLong() : 0;
		String source = drop.has("source") && !drop.get("source").isJsonNull() ? drop.get("source").getAsString().trim() : "";
		RecentActivity activity = new RecentActivity();
		activity.type = DROP_OF_DAY;
		activity.player_name = drop.get("player_name").getAsString();
		activity.title = "Drop do dia: " + (quantity > 1 ? quantity + "x " : "") + drop.get("item_name").getAsString()
			+ (value > 0 ? " (" + shortGp(value) + ")" : "") + (source.isEmpty() ? "" : " em " + source);
		return activity;
	}

	static String shortGp(long value)
	{
		// Promote to the next unit before rounding, so 999,960,000 reads "1.0B" and not "1000.0M".
		if (value >= 999_950_000L) return String.format(java.util.Locale.ROOT, "%.1fB", value / 1e9);
		if (value >= 999_500L) return String.format(java.util.Locale.ROOT, "%.1fM", value / 1e6);
		if (value >= 1_000L) return Math.round(value / 1e3) + "K";
		return value + " gp";
	}
}
