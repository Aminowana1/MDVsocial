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

## Menú Bedrock "Social y Amigos" (menuamigos.yml)

Los botones muestran pendientes solo cuando hay alguno:

| Botón | Marca | Cuenta |
|---|---|---|
| Amigos y Solicitudes | `{friend_requests_badge}` | solicitudes de amistad |
| Grupo de Aventura | `{party_invites_badge}` | invitaciones a grupos |
| Cartas y Buzón | `{unread_mail_badge}` | cartas sin leer |

- Con 0 la marca desaparece. Formato: `bedrock.badges.format` (por defecto `' ({count})'`).
- En menús internos se usan `{friend_requests}`, `{party_invites}` y `{unread_mail}`,
  que sí muestran 0 (por ejemplo "Ver Cartas (0)").
- Las marcas funcionan en cualquier texto de MenusBedrock.

## Grupo de Aventura (party.yml)

| Sin grupo | Con grupo ("Nuevo Grupo de Aventura") |
|---|---|
| Crear Grupo de Aventura | Invitar jugador |
| Invitaciones (n) | Invitaciones (n) |
| Volver | Salir del grupo |
| | Volver |

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
