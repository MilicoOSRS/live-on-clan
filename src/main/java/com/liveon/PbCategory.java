package com.liveon;

final class PbCategory
{
	String boss;
	String mode;
	int team_size;
	String time_type;
	int entries;

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
