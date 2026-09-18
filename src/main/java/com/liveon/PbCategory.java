package com.liveon;

final class PbCategory
{
	String boss;
	String mode;
	int team_size;
	String time_type;
	int entries;

	static boolean isAllowed(String boss)
	{
		return isAllowed(boss, "");
	}

	static boolean isAllowed(String boss, String mode)
	{
		if (boss == null) return false;
		String normalizedBoss = normalize(boss);
		String normalizedMode = normalize(mode);
		return !normalizedBoss.contains("agility")
			&& !isJadChallengeValue(normalizedBoss)
			&& !isJadChallengeValue(normalizedMode);
	}

	private static boolean isJadChallengeValue(String value)
	{
		return value.contains("jad challenge") || value.startsWith("fastest wave time")
			|| (value.contains("tzhaar ket rak") && value.contains("challenge"));
	}

	private static String normalize(String value)
	{
		return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT).trim()
			.replaceAll("[^a-z0-9]+", " ").replaceAll("\\s+", " ").trim();
	}

	String label()
	{
		StringBuilder value = new StringBuilder(boss == null ? "" : boss);
		if (mode != null && !mode.isEmpty()) value.append(" · ").append(mode);
		if (team_size > 0) value.append(" · ").append(team_size == 1 ? "Solo" : team_size + " jogadores");
		if ("ROOM".equals(time_type)) value.append(" · Room time");
		else if ("OVERALL".equals(time_type)) value.append(" · Overall time");
		return value.toString();
	}

	@Override public String toString() { return label(); }
}
