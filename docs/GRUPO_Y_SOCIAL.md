# MDVSocial - Grupo de Aventura y menú Social y Amigos

## /grupo

```
/grupo                       crea un Grupo de Aventura
/grupo Player1234 StevePro   lo crea (si no tienes) e invita a esos jugadores
```

- Hasta 5 nombres. Tab autocompleta jugadores conectados, tus amigos primero, sin
  repetir los ya escritos.
- Si ya tienes grupo no se crea otro: solo se envían las invitaciones.
- Los jugadores desconectados o mal escritos se avisan uno por uno; el resto se invita.
- Bedrock: siempre abre el formulario del grupo al terminar.
- Java: sin nombres ejecuta `party-command.java-menu-command` (por defecto `party`);
  con nombres no abre nada para que se vean los avisos en el chat.
- Permiso: `mdvsocial.party.use` (por defecto todos).

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

Formulario reutilizable: botón "Escribir nombre" y debajo los jugadores
conectados, paginados con `bedrock.player-picker.page-size` (por defecto 6).

| Uso | Amigos | Excluye |
|---|---|---|
| Invitar jugador (grupo) | primero, con etiqueta "Amigo" | miembros del grupo |
| Añadir amigo | no aparecen | tus amigos |

Desde código: `plugin.bedrock().picker().open(player, new PlayerPickerForms.Request(...), 0)`.

## Servidores existentes

Los YAML de `MenusBedrock` ya creados no se sobrescriben; solo se añaden claves
nuevas (`title-party`, `invite-picker`, `add-picker`, `escoger_jugador.yml`).
Para ver el nuevo título "Social y Amigos" y los contadores, edita
`menuamigos.yml` y `correo.yml` o bórralos para regenerarlos.
