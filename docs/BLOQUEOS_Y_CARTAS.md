# MDVSocial - Jugadores bloqueados y cartas

## Menú "Amigos"

| Opción | Java | Bedrock |
|---|---|---|
| Lista de Amigos | `COMMAND_PLAYER friends` | `OPEN_BEDROCK_FRIENDS` |
| Grupo de Aventura | `COMMAND_PLAYER party` | `OPEN_BEDROCK_PARTY` |
| Cartas y Buzón | `OPEN_MENU correo` (Ver cartas, Enviar carta) | igual |
| Jugadores Bloqueados | `OPEN_BLOCKED_PLAYERS` (lista + Bloquear jugador) | igual |

El menú "correo" ya no bloquea ni desbloquea: eso vive en "Jugadores Bloqueados".

## Bloqueos (sistema general)

- Se guardan en `player-data.db`, tabla `player_blocks (owner, blocked, created_at)`.
- `PlayerBlockService` es la única fuente de verdad. La consulta para cualquier
  sistema es una sola:

  ```java
  // ¿"owner" bloqueó a "target"?
  MDVSocialAPI.isBlocked(owner, target);
  // ¿"from" puede contactar a "to"?
  MDVSocialAPI.canContact(from, to);
  ```

- Es unidireccional: si A bloquea a B, B no puede enviarle cartas a A.
- Hoy lo respetan las cartas, las invitaciones de clan por carta, las
  invitaciones a Grupos de Aventura, las solicitudes de amistad y el TPA.
- Comandos: `/bloquear <jugador>`, `/desbloquear <jugador>`, `/bloqueados`.
  `/carta bloquear|desbloquear|bloqueados` siguen funcionando.
- Acciones de menú: `OPEN_BLOCKED_PLAYERS`, `START_BLOCK_PLAYER`,
  `START_UNBLOCK_PLAYER`. Las antiguas `START_MAIL_BLOCK` y `START_MAIL_UNBLOCK`
  son alias de las nuevas.
- Límite por jugador: `blocked-players.max-entries` (por defecto 200).
- Migración: al arrancar, las listas `mailbox.<uuid>.blocked` del correo se
  copian una sola vez a la tabla nueva y se borran del buzón.

## Cartas

- **Mensaje genérico:** si el destinatario te bloqueó ves `mail-not-delivered`
  ("No se pudo enviar la carta a X"), que no menciona el bloqueo.
- **Espera entre envíos:** `mail.send-cooldown-seconds` (por defecto 30).
  Se salta con `mdvsocial.mail.cooldown.bypass`. Solo cuenta si la carta se envió.
- **Sin códigos de color:** las cartas de jugadores se guardan y se muestran sin
  `&`/`§`/hex. Las cartas del servidor (globales y bienvenida) conservan sus colores.
- **Límite del buzón offline:** se recuerda el último límite calculado con el
  jugador conectado (tabla `player_mailbox_limits`), y se usa cuando está
  desconectado. Si sus permisos cambian estando offline, se actualiza al entrar.

## Servidores existentes

Los archivos ya creados no se sobrescriben:

- `MenusBedrock/menuamigos.yml` y `correo.yml` reciben solo los botones nuevos
  (al final). Los botones viejos de bloquear/desbloquear del correo siguen
  funcionando, pero conviene quitarlos o borrar el archivo para regenerarlo.
- `Menus/menuamigos.yml` y `Menus/correo.yml` (Java) se crean solo si no existen.
- Los mensajes `mail-block-prompt`, `mail-blocked`, `mail-blocked-by-target`…
  ya no se usan; sus reemplazos son las claves `player-*`, `blocked-list-*` y
  `mail-not-delivered`.
