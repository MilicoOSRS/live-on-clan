package com.liveon;

import java.util.Collections;
import java.util.List;

final class PbRankingResponse
{
	String boss;
	String mode;
	int team_size;
	String time_type;
	int total;
	List<Entry> ranking = Collections.emptyList();
	Entry own;

	static final class Entry
	{
		String player_name;
		double seconds;
		String updated_at;
		int position;
	}
}
