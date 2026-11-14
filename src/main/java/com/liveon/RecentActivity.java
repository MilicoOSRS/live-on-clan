package com.liveon;

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
		activity.title = "Drop do dia: Nenhum drop de 1M+ hoje ainda. Seja o primeiro!";
		return activity;
	}

	/** Builds the pinned "Drop do dia" row from the server's ranking since midnight; null when there is no drop yet. */
	static RecentActivity dropOfDay(MvpDropEntry[] ranking)
	{
		if (ranking == null) return null;
		MvpDropEntry bestEntry = null;
		MvpDropDetail best = null;
		for (MvpDropEntry entry : ranking)
		{
			if (entry == null || entry.getTopDrops() == null || entry.getPlayerName() == null) continue;
			for (MvpDropDetail drop : entry.getTopDrops())
			{
				if (drop != null && drop.getItem() != null && (best == null || drop.getValue() > best.getValue()))
				{
					best = drop;
					bestEntry = entry;
				}
			}
		}
		if (best == null) return null;
		String item = best.getItem().trim().replaceFirst("(?i)^1x\\s+", "");
		RecentActivity activity = new RecentActivity();
		activity.type = DROP_OF_DAY;
		activity.player_name = bestEntry.getPlayerName();
		activity.title = "Drop do dia: " + item + (best.getValue() > 0 ? " (" + shortGp(best.getValue()) + ")" : "");
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
