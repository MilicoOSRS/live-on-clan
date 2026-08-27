package com.liveon;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Data;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemVariationMapping;

/**
 * Reads the same OSRS Wiki-derived NPC drop-rate data used by Dink.
 * The bundled data and transformation are retained under Dink's BSD-2 license.
 */
@Slf4j
@Singleton
class DropRarityService
{
	private final ItemManager itemManager;
	private final Map<String, Collection<RareDrop>> dropsBySource = new HashMap<>();

	@Inject
	DropRarityService(Gson gson, ItemManager itemManager)
	{
		this.itemManager = itemManager;
		try (InputStream stream = getClass().getResourceAsStream("/npc_drops.json");
			 Reader reader = new InputStreamReader(Objects.requireNonNull(stream), StandardCharsets.UTF_8))
		{
			Map<String, List<RawDrop>> raw = gson.fromJson(reader,
				new TypeToken<Map<String, List<RawDrop>>>() { }.getType());
			raw.forEach((source, drops) -> dropsBySource.put(source, drops.stream()
				.map(RawDrop::transform)
				.flatMap(Collection::stream)
				.collect(Collectors.toCollection(ArrayList::new))));
		}
		catch (Exception exception)
		{
			log.debug("Unable to load NPC drop rarity data", exception);
		}
	}

	OptionalDouble getRarity(String source, int itemId, int quantity)
	{
		ItemComposition composition = itemManager.getItemComposition(itemId);
		int canonical = composition.getNote() != -1 ? composition.getLinkedNoteId() : itemId;
		String itemName = composition.getMembersName();
		Set<Integer> variants = new HashSet<>(ItemVariationMapping.getVariations(ItemVariationMapping.map(canonical)));
		return dropsBySource.getOrDefault(source, Collections.emptyList()).stream()
			.filter(drop -> drop.minQuantity <= quantity && quantity <= drop.maxQuantity)
			.filter(drop -> drop.itemId == itemId || (variants.contains(drop.itemId)
				&& itemName.equals(itemManager.getItemComposition(drop.itemId).getMembersName())))
			.mapToDouble(RareDrop::getProbability)
			.reduce(Double::sum);
	}

	OptionalDouble getRarityByItemName(String source, String itemName)
	{
		if (source == null || itemName == null) return OptionalDouble.empty();
		return dropsBySource.getOrDefault(source, Collections.emptyList()).stream()
			.filter(drop -> itemName.equalsIgnoreCase(itemManager.getItemComposition(drop.itemId).getMembersName()))
			.mapToDouble(RareDrop::getProbability)
			.reduce(Double::sum);
	}

	OptionalInt findItemId(String source, String itemName)
	{
		if (source == null || itemName == null) return OptionalInt.empty();
		return dropsBySource.getOrDefault(source, Collections.emptyList()).stream()
			.filter(drop -> itemName.equalsIgnoreCase(itemManager.getItemComposition(drop.itemId).getMembersName()))
			.mapToInt(RareDrop::getItemId)
			.findFirst();
	}

	@Value
	private static class RareDrop
	{
		int itemId;
		int minQuantity;
		int maxQuantity;
		double probability;
	}

	@Data
	private static class RawDrop
	{
		@SerializedName("i") private int itemId;
		@SerializedName("r") private Integer rolls;
		@SerializedName("d") private double denominator;
		@SerializedName("q") private Integer quantity;
		@SerializedName("m") private Integer quantityMin;
		@SerializedName("n") private Integer quantityMax;

		Collection<RareDrop> transform()
		{
			int rounds = rolls == null ? 1 : rolls;
			int min = quantityMin == null ? quantity : quantityMin;
			int max = quantityMax == null ? quantity : quantityMax;
			double chance = 1.0 / denominator;
			if (rounds == 1)
			{
				return Collections.singletonList(new RareDrop(itemId, min, max, chance));
			}
			List<RareDrop> result = new ArrayList<>(rounds);
			for (int successes = 1; successes <= rounds; successes++)
			{
				double density = binomial(rounds, successes) * Math.pow(chance, successes)
					* Math.pow(1.0 - chance, rounds - successes);
				result.add(new RareDrop(itemId, min * successes, max * successes, density));
			}
			return result;
		}

		private static long binomial(int n, int k)
		{
			long result = 1;
			for (int i = 1; i <= k; i++) result = result * (n - k + i) / i;
			return result;
		}
	}
}
