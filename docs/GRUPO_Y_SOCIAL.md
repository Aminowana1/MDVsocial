# MDVSocial - Grupo de Aventura y menú Social y Amigos

## /grupo

```
/grupo                       crea un Grupo de Aventura
/grupo Player1234 StevePro   lo crea (si no tienes) e invita a esos jugadores
```

- Hasta 5 nombres. Tab autocompleta jugadores conectados, tus amigos primero, sin
  repetir los ya escritos.
- Si ya tienes grupo no se crea otro: solo se envían las invitaciones. Sin nombres
  abre el menú del grupo, sin ningún mensaje.
- Los jugadores desconectados o mal escritos se avisan uno por uno; el resto se invita.
- Bedrock: siempre abre el formulario del grupo al terminar.
- Java: sin nombres ejecuta `party-command.java-menu-command` (por defecto `party`);
  con nombres no abre nada para que se vean los avisos en el chat.
- Permiso: `mdvsocial.party.use` (por defecto todos).

## Invitar agachado (click derecho)

Agachado y con la mano vacía, click derecho a otro jugador (en Bedrock: interactuar
o mantener pulsado sobre él) lo invita a tu grupo.

- Sin grupo: se crea uno (`party-created`) y se envía la invitación.
- En un grupo: solo el líder puede invitar (`party-sneak-invite-not-leader`). Si ya
  es miembro o el grupo está lleno se avisa (`party-target-already-member`, `party-full`).
- Al invitar suena el sonido de la acción rápida de invitaciones
  (`quick-accept.types.party-invite.trigger-sound`, o `quick-accept.trigger-sound`).
- Se respetan los bloqueos como en `/grupo`. Los NPC (Citizens) se ignoran.
- Una invitación cada `cooldown-millis` (1500 por defecto) para no repetirla al
  mantener el click.
- Config: `party-invites.sneak-interact.enabled` (true) para activarlo o desactivarlo.
  Permiso: `mdvsocial.party.use`.

## Al unirte a un grupo

No se abre ningún menú: ni el formulario de Bedrock ni el menú de grupo que MMOCore
abre por su cuenta al aceptar (se cancela durante 2 s tras aceptar, desde MDVSocial,
`/party accept` o los botones del chat). Para volver al comportamiento anterior:
`party-invites.open-menu-on-join: true`.

## Avisos de invitación

Los avisos al invitado (Java con botones, Bedrock con quick accept) se
describen en `QUICK_ACCEPT_E_INVITACIONES.md`.

## Bloqueos y grupos

Si un jugador te bloqueó, no puedes invitarlo a tu grupo desde ningún sitio:
`/grupo`, "Invitar jugador", el selector, las opciones de amigo ni escribiendo
`/party invite <nombre>` (comandos en `blocked-players.party-invite-commands`).
Igual que con las cartas, el aviso es genérico (`party-invite-not-delivered`):
"No se pudo invitar a X al grupo", sin revelar el bloqueo. El jugador sigue
apareciendo en el selector y en el autocompletado, por la misma razón.

## Menú Bedrock "Social y Amigos" (menuamigos.yml)

Los botones muestran pendientes solo cuando hay alguno:

| Botón | Marca | Cuenta |
|---|---|---|
| Amigos y Solicitudes | `{friend_requests_badge}` | solicitudes de amistad |
| Grupo de Aventura | `{party_invites_badge}` | invitaciones a grupos |
| Viajes (TPA) | `{tpa_requests_badge}` | solicitudes de TPA recibidas |
| Cartas y Buzón | `{unread_mail_badge}` | cartas sin leer |

- En el menú principal, el botón "Social y Amigos" usa `{social_notifications_badge}`: la
  suma de todo lo anterior (cartas nuevas y solicitudes de amistad, grupo y TPA).
- Con 0 la marca desaparece. Formato: `bedrock.badges.format` (por defecto `' ({count})'`).
- En menús internos se usan `{friend_requests}`, `{party_invites}`, `{tpa_requests}` y `{unread_mail}`,
  que sí muestran 0 (por ejemplo "Ver Cartas (0)").
- Las marcas funcionan en cualquier texto de MenusBedrock.

## Grupo de Aventura (party.yml)

| Sin grupo | Con grupo ("Nuevo Grupo de Aventura") |
|---|---|
| Crear Grupo de Aventura | Invitar jugador |
| Invitaciones (n) | Invitaciones (n) |
| Volver | Salir del grupo |
| | Volver |

- Si `/grupo` o "Crear" acaban de crear el grupo, el formulario lo dice en su primera
  línea (`created-line` en `party.yml`), además del mensaje `party-created` en el chat,
  que en Bedrock queda tapado por el formulario.
- "Salir del grupo" no pide confirmación si eres el único integrante.

"Invitar desde Amigos" desaparece: el selector de "Invitar jugador" ya muestra a
tus amigos primero.

## Escoger jugador (escoger_jugador.yml)

Todos los menús de Bedrock donde se elige a un jugador usan el mismo formulario:
botón "Escribir nombre" y debajo los jugadores conectados, paginados con
`bedrock.player-picker.page-size` (por defecto 6).

Orden, con su etiqueta: **amigos**, **Grupo de Aventura**, **clan** (MDVClans) y el
resto de conectados; alfabético dentro de cada grupo. Nunca aparecen los jugadores
que bloqueaste. Cada menú quita además los que serían redundantes:

| Menú | No lista |
|---|---|
| Añadir amigo | tus amigos |
| Invitar al grupo | los miembros de tu grupo |
| Invitar al clan | quien ya está en cualquier clan (MDVClans lo rechazaría) |
| Bloquear jugador | (los bloqueados ya no aparecen) |
| Solicitar viaje (TPA) | — |
| Enviar carta | — ("Escribir nombre" sirve para desconectados) |
| Protecciones > Agregar miembro | los miembros de la protección |

Código: el orden y los filtros están en `players.PlayerRanking` (sin Bukkit, con
tests); `MDVSocialPlugin#rankPlayers(viewer, exclude)` lo alimenta con amigos,
grupo, clan y bloqueos, y `PlayerPickerForms` dibuja el formulario:
`plugin.bedrock().picker().open(player, new PlayerPickerForms.Request(...), 0)`.

## Invitar al clan (clan_invitar.yml)

- **Bedrock:** el botón "Invitar jugador" del formulario de miembros de MDVClans
  abre "Escoger jugador" mediante `MDVSocialAPI.openClanInvitePicker(player, onBack)`.
  Al elegir o escribir un nombre se ejecuta `/clan invitar <nombre>` como el jugador
  y no se reabre ningún formulario, para que se vea la respuesta de MDVClans.
- **Java:** el menú de miembros de MDVClans tiene "Invitar jugador" (slot 51,
  `menus.members.invite`); el nombre se escribe en el chat (`cancelar` para salir,
  `invites.chat-input-seconds` para responder).
- **Bloqueos** (MDVClans, `integrations.mdvsocial.respect-blocks`): no puedes invitar
  a quien tienes bloqueado, y si él te bloqueó la invitación no llega (aviso
  genérico). Solo afecta a invitaciones directas: jugadores bloqueados pueden
  coincidir en un clan por otras vías.
- MDVClans expone `getClanMemberUuids(UUID)` para ordenar a los miembros del clan.

## Servidores existentes

Los YAML de `MenusBedrock` ya creados no se sobrescriben; solo se añaden claves
nuevas (`title-party`, `invite-picker`, `add-picker`, `escoger_jugador.yml`).
Para ver el nuevo título "Social y Amigos" y los contadores, edita
`menuamigos.yml` y `correo.yml` o bórralos para regenerarlos.
