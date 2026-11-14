package com.liveon;

/** Every address of the clan website the plugin links to, in one place. They are only opened in the browser on a click; the plugin never requests them. */
final class LiveOnUrls
{
	static final String SITE = "https://liveon.gg";
	static final String APPLY = SITE + "/recrutamento";

	private LiveOnUrls()
	{
	}

	/** Public profile page for a name already converted by {@link ClanMessagesPlugin#playerProfileSlug}. */
	static String playerProfile(String slug)
	{
		return SITE + "/jogador/" + slug;
	}
}
