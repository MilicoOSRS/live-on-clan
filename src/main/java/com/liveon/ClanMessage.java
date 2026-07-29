package com.liveon;

import lombok.Getter;

@Getter
final class ClanMessage
{
	private String id;
	private String author;
	private String message;
	private String mode;
	// Accept both the legacy SQLite 0/1 representation and JSON booleans.
	private Object pinned;

	ClanMessage()
	{
	}

	ClanMessage(String id, String author, String message, String mode, boolean pinned)
	{
		this.id = id;
		this.author = author;
		this.message = message;
		this.mode = mode;
		this.pinned = pinned;
	}
}
