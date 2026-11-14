package com.liveon;

/** Every address of the clan website the plugin links to or reads, in one place. */
final class LiveOnUrls
{
	static final String SITE = "https://liveon.gg";
	static final String APPLY = SITE + "/recrutamento";
	static final String TOP_DROP_TODAY = SITE + "/member/drops/top-today";

	private LiveOnUrls()
	{
	}

	/** Public profile page for a name already converted by {@link ClanMessagesPlugin#playerProfileSlug}. */
	static String playerProfile(String slug)
	{
		return SITE + "/jogador/" + slug;
	}
}
