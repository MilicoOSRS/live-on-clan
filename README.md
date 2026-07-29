# Live On Clan

Plugin RuneLite exclusivo para membros do clan Live On.

## Recursos

- Comunicados do clan e broadcasts fixados.
- Solicitacoes e confirmacoes de ranks.
- Ranking mensal MVP Drops.
- Rankings mensais de EHB e EHP com dados do Wise Old Man.
- Lista de membros ao vivo na Twitch.
- Icones opcionais de Live e MVP no chat do clan.
- Notificacoes opcionais de drops raros no Discord.

## Acesso

O plugin consulta o grupo `1945` do Wise Old Man para verificar se o personagem
pertence ao Live On. Os recursos administrativos sao exibidos somente para os
cargos de staff configurados no grupo.

## Privacidade

Os recursos que acessam a API do clan, consultam lives ou enviam estatisticas
ficam desativados ate o consentimento do usuario nas configuracoes. Ao ativa-los,
o endereco IP e o nome do personagem podem ser enviados aos respectivos servicos
externos.

O envio de drops ao Discord e opcional. Quando a opcao de captura esta ativa, a
imagem do jogo e enviada a API do clan, que a encaminha ao servico de filtros
configurado no servidor.

## Desenvolvimento

Requer Java 11.

```powershell
.\gradlew.bat run
```

Para entrar no cliente de desenvolvimento usando uma conta Jagex, consulte
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).

## Licenca

Distribuido sob a licenca BSD 2-Clause.
