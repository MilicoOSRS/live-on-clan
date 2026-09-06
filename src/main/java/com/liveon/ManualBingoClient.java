package com.liveon;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.Set;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

@Slf4j
final class ManualBingoClient
{
	private final Gson gson;
	private final ClanMessagesPanel panel;
	private final Supplier<String> account;
	private final Supplier<Boolean> staff;
	private final BiFunction<String, String, Call> transport;
	private final Set<Call> calls = ConcurrentHashMap.newKeySet();
	private final AtomicBoolean fetching = new AtomicBoolean();
	private volatile DropRules dropRules = DropRules.EMPTY;
	private volatile boolean closed;

	ManualBingoClient(Gson gson, ClanMessagesPanel panel, Supplier<String> account, Supplier<Boolean> staff,
		Supplier<Boolean> deputyOwner,
		BiFunction<String, String, Call> transport)
	{
		this.gson = gson; this.panel = panel; this.account = account; this.staff = staff;
		this.transport = transport;
		panel.bingo().configure(this::save, this::refresh, deputyOwner);
	}

	void refresh()
	{
		if (closed || account.get().isEmpty() || !fetching.compareAndSet(false, true)) return;
		request(null);
	}

	private void save(JsonObject payload)
	{
		if (closed || account.get().isEmpty() || !staff.get()) return;
		payload.addProperty("playerName", account.get());
		request(payload);
	}

	private void request(JsonObject payload)
	{
		String player = account.get();
		Call call = transport.apply(payload == null ? "bingo" : "admin/bingo", payload == null ? null : gson.toJson(payload));
		if (call == null) { fetching.set(false); return; }
		calls.add(call);
		call.enqueue(new Callback() {
			@Override public void onFailure(Call request, IOException error) {
				calls.remove(request); if (payload == null) fetching.set(false);
				if (!closed) panel.bingo().status("Sem conexão; recarregue");
				log.debug("Manual Bingo request failed", error);
			}
			@Override public void onResponse(Call request, Response response) throws IOException {
				try (Response ignored = response) {
					if (closed || !player.equals(account.get())) return;
					if (response.body() == null) return;
					JsonObject result = gson.fromJson(response.body().string(), JsonObject.class);
					if (response.isSuccessful() && result.has("tiles")) {
						updateDropRules(player, result);
						panel.updateBingo(result);
						if (payload != null) panel.bingo().status("Progresso salvo");
					} else panel.bingo().status(result.has("error") ? result.get("error").getAsString() : "Bingo indisponível");
				} catch (RuntimeException error) { log.debug("Invalid Bingo response", error); }
				finally { calls.remove(request); if (payload == null) fetching.set(false); }
			}
		});
	}

	private void updateDropRules(String player, JsonObject board)
	{
		Set<String> items = new HashSet<>();
		if (board.has("tiles") && board.get("tiles").isJsonArray())
		{
			board.getAsJsonArray("tiles").forEach(element -> {
				if (!element.isJsonObject() || !element.getAsJsonObject().has("items")
					|| !element.getAsJsonObject().get("items").isJsonArray()) return;
				element.getAsJsonObject().getAsJsonArray("items").forEach(item -> {
					if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString())
					{
						String name = normalize(item.getAsString());
						if (!name.isEmpty()) items.add(name);
					}
				});
			});
		}
		boolean sending = board.has("sending") && board.get("sending").getAsBoolean();
		dropRules = new DropRules(player, sending, Collections.unmodifiableSet(items));
	}

	boolean acceptsDrop(String player, String itemName)
	{
		DropRules rules = dropRules;
		if (closed || !rules.sending || !rules.account.equals(player)) return false;
		String normalized = normalize(itemName);
		for (String rule : rules.items)
		{
			if (rule.endsWith("*") ? normalized.startsWith(rule.substring(0, rule.length() - 1))
				: normalized.equals(rule)) return true;
		}
		return false;
	}

	private static String normalize(String value)
	{
		return value == null ? "" : value.replace('\u00A0', ' ').trim()
			.toLowerCase(java.util.Locale.ROOT);
	}

	private static final class DropRules
	{
		private static final DropRules EMPTY = new DropRules("", false, Collections.emptySet());
		private final String account;
		private final boolean sending;
		private final Set<String> items;

		private DropRules(String account, boolean sending, Set<String> items)
		{
			this.account = account;
			this.sending = sending;
			this.items = items;
		}
	}

	void close()
	{
		closed = true;
		dropRules = DropRules.EMPTY;
		for (Call call : calls) call.cancel();
		calls.clear();
	}
}
