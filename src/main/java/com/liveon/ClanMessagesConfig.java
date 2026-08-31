package com.liveon;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("live-on-clan-messages")
public interface ClanMessagesConfig extends Config
{
	String THIRD_PARTY_WARNING =
		"This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers";

	@ConfigItem(
		keyName = "enabled",
		name = "Conectar ao servidor do clan",
		description = "Ativa os recursos online do clan",
		warning = THIRD_PARTY_WARNING,
		position = 0
	)
	default boolean enabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "liveStatusEnabled",
		name = "Mostrar lives online",
		description = "Consulta os canais associados e exibe os membros ao vivo",
		warning = THIRD_PARTY_WARNING,
		position = 1
	)
	default boolean liveStatusEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "statsEnabled",
		name = "Participar do MVP mensal",
		description = "Envia seus drops elegiveis para o ranking mensal do clan",
		warning = THIRD_PARTY_WARNING,
		position = 2
	)
	default boolean statsEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pbRankingEnabled",
		name = "Participar do ranking de PBs",
		description = "Envia seus tempos registrados para o ranking privado do clan; requer conexao ao servidor",
		warning = THIRD_PARTY_WARNING,
		position = 3
	)
	default boolean pbRankingEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "staffAccessKey",
		name = "Chave da staff",
		description = "Chave administrativa fornecida somente aos membros da staff",
		secret = true,
		position = 4,
		hidden = true
	)
	default String staffAccessKey()
	{
		return "";
	}

	@ConfigItem(
		keyName = "discordDropsEnabled",
		name = "Enviar drops ao Discord",
		description = "Publica seus drops raros no Discord do clan",
		warning = THIRD_PARTY_WARNING,
		position = 5
	)
	default boolean discordDropsEnabled()
	{
		return false;
	}

	@Range(min = -20, max = 20)
	@ConfigItem(
		keyName = "sidebarIconPriority",
		name = "Posição na sidebar",
		description = "Use as setas para mover o ícone do plugin na barra lateral",
		position = 6
	)
	default int sidebarIconPriority()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "discordDropMinimumValue",
		name = "Valor minimo do drop",
		description = "Valor GE minimo para publicar um drop",
		hidden = true
	)
	default int discordDropMinimumValue()
	{
		return 3_000_000;
	}

	@ConfigItem(
		keyName = "serverUrl",
		name = "Servidor",
		description = "Endereco da API do clan",
		hidden = true
	)
	default String serverUrl()
	{
		return "https://liveonpl.discloud.app/";
	}

	@ConfigItem(
		keyName = "pollIntervalSeconds",
		name = "Intervalo de atualizacao",
		description = "Frequencia de consulta de novas mensagens",
		hidden = true
	)
	default int pollIntervalSeconds()
	{
		return 30;
	}
}
