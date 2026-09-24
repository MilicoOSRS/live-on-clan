package com.liveon;

import java.util.HashSet;
import java.util.Set;

/** Client-thread state. Only actual pending deliveries and confirmed failures can notify. */
final class RecoveryNotice
{
	enum Message { NONE, WAITING, RECOVERED }
	private final Set<String> pending = new HashSet<>();
	private final Set<String> failedRequests = new HashSet<>();
	private boolean warned;
	private boolean cancelled;
	private int accepted;
	private long emptySince = -1;
	private long lastWarning = Long.MIN_VALUE;

	void queued(String key) { pending.add(key); emptySince = -1; }
	void failed(String key) { queued(key); failedRequests.add(key); }
	void accepted(String key) { failedRequests.remove(key); if (pending.remove(key)) accepted++; }
	void cancelled(String key) { failedRequests.remove(key); if (pending.remove(key)) cancelled = true; }

	Message poll(long now, boolean authenticationFailed, boolean authenticated)
	{
		boolean failure = !failedRequests.isEmpty() || (authenticationFailed && !pending.isEmpty());
		if (failure && !pending.isEmpty() && !warned
			&& (lastWarning == Long.MIN_VALUE || now - lastWarning >= 60000L))
		{
			warned = true;
			lastWarning = now;
			return Message.WAITING;
		}
		if (!pending.isEmpty() || !authenticated) { emptySince = -1; return Message.NONE; }
		if (emptySince < 0) emptySince = now;
		if (now - emptySince < 5000L) return Message.NONE;
		boolean recovered = warned && accepted > 0 && !cancelled;
		warned = cancelled = false;
		accepted = 0;
		return recovered ? Message.RECOVERED : Message.NONE;
	}

	void reset()
	{
		pending.clear();
		failedRequests.clear();
		warned = cancelled = false;
		accepted = 0;
		emptySince = -1;
		lastWarning = Long.MIN_VALUE;
	}
}
