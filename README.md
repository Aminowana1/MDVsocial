# MDVSocial 1.5.2

Plugin social modular para MDVCRAFT.


## MDVSocial 1.5.2

- La carta automática de bienvenida se entrega silenciosamente por defecto.
- Ya no aparece `Mensaje faltante: mail-received` al primer ingreso.
- El rango del hover y del menú se obtiene del grupo directo de LuckPerms con mayor peso.
- Los nombres visuales de grupos pueden personalizarse en `interactive-chat.profile.group-display-names`.

## MDVSocial 1.5.0

- Nombre interactivo en el chat usando Paper Adventure.
- Hover configurable con nivel, raza, rango, título y clan.
- Clic normal sobre el nombre abre `Menus/jugador_opciones.yml`.
- Shift + clic sobre el nombre inserta `/msg <jugador> `.
- Menú general de jugador separado de `amigo_opciones.yml`.
- Solicitudes de amistad mediante la API real de MMOCore.
- Acciones para clan, carta, Grupo de Aventura y TPA.
- Clic derecho configurable en botones mediante `right-action`.
- Nuevos placeholders de menú: `{target_level}`, `{target_class}`, `{target_rank}`, `{target_title}` y `{target_clan}`.
- Los títulos con `hidden: true` no aparecen en títulos bloqueados, pero siguen siendo equipables por quien tenga permiso.

## MDVSocial 1.4.1

- Menú central configurable `/mdvadmin` (alias `/ma`) desde `Menus/admin.yml`.
- Biblioteca segura `/mdvitems` para obtener copias base de MMOItems por categoría, sin acceso al editor oficial.
- Permisos por menú y por botón en los menús modulares.

## MDVSocial 1.4.0

- Hogares excedentes suspendidos sin eliminar datos de EssentialsX.
- Bloqueo directo de `/home`, `/ehome` y variantes `essentials:` para casas suspendidas.
- Campañas de correo global listables y eliminables por ID.
- Correo automático de bienvenida para jugadores nuevos.
- Restablecimiento automático a `aventurero` cuando un título deja de estar desbloqueado.
- Títulos obligatorios de castigo administrados por staff.
- Posición de rangos configurable con `page` y `slot`.
- Retirada la reparación de AnimatedScoreboard de 1.3.3.

Comandos administrativos nuevos:

- `/mdvsocial mail list [página]`
- `/mdvsocial mail view <id>`
- `/mdvsocial mail delete <id>`
- `/mdvsocial mail welcome-test <jugador>`
- `/mdvsocial title punish <jugador> [título]`
- `/mdvsocial title unpunish <jugador>`
- `/mdvsocial homes status <jugador>`
- `/mdvsocial homes restore <jugador>`


## Incluye

- Menús modulares en `plugins/MDVSocial/Menus/`.
- Títulos cosméticos y rangos visuales.
- Placeholders de título para PlaceholderAPI, incluyendo títulos de otros jugadores.
- Placeholders inteligentes de party para scoreboard.
- Puentes para GUIs externas.
- Menú configurable de opciones de amigo desde la lista de MMOCore.
- Sistema base de cartas/correo interno.
- Responder cartas directamente desde la lectura.
- Las cartas oficiales de MDVCRAFT no pueden bloquearse.

## Placeholders de título

Del jugador que mira/evalúa el placeholder:

- `%mdvsocial_title%`
- `%mdvsocial_title_colored%`
- `%mdvsocial_title_prefix%`
- `%mdvsocial_title_prefix_plain%`
- `%mdvsocial_active_title%`
- `%mdvsocial_title_id%`
- `%mdvsocial_unlocked_titles%`

De otro jugador por nombre:

- `%mdvsocial_title_of_<jugador>%`
- `%mdvsocial_title_colored_of_<jugador>%`
- `%mdvsocial_title_prefix_of_<jugador>%`
- `%mdvsocial_title_prefix_plain_of_<jugador>%`
- `%mdvsocial_title_id_of_<jugador>%`
- `%mdvsocial_active_title_of_<jugador>%`

También acepta UUID con `uuid_`:

- `%mdvsocial_title_colored_of_uuid_<uuid>%`

Ejemplo para menús que primero reemplazan `{player}`:

```yaml
- '&7Título: &r%mdvsocial_title_colored_of_{player}%'
```

## Placeholders de party para scoreboard

Estos placeholders usan MMOCore si está activo. Si el jugador no está en party, las líneas visuales devuelven vacío para no mostrar la sección de grupo.

- `%mdvsocial_party_header%` → `Grupo: actual/max`
- `%mdvsocial_party_member_1%`
- `%mdvsocial_party_member_2%`
- `%mdvsocial_party_member_3%`
- `%mdvsocial_party_member_4%`
- `%mdvsocial_party_member_5%`
- `%mdvsocial_party_count%`
- `%mdvsocial_party_max%`
- `%mdvsocial_party_members%`
- `%mdvsocial_party_in_group%`
- `%mdvsocial_party_spacer%`

El encabezado y las líneas normales de miembros se personalizan en `config.yml`:

```yaml
scoreboard:
  group-header: 'ɢʀᴜᴘᴏ &7(&f{count}&7/&f{max}&7)'
  group-member: '&c{health}❤ &r&f{name}'
```

`group-header` admite `{count}` y `{max}`. `group-member` admite `{health}`,
`{max_health}` y `{name}`; para miembros desconectados, los valores de salud
se muestran como `--`. Los placeholders con sufijos explícitos como
`party_member_1_health` y `party_member_1_name` conservan sus formatos propios.

El máximo se lee desde:

```yaml
social-friend-options:
  party:
    max-members: 5
```

## Sistema de cartas

Comandos:

- `/correo` abre el menú de correo.
- `/carta buzon` abre el buzón.
- `/carta enviar <jugador> <mensaje>` envía una carta.
- `/carta bloquear <jugador>` bloquea cartas de ese jugador.
- `/carta desbloquear <jugador>` desbloquea cartas de ese jugador.
- `/carta bloqueados` muestra bloqueados.
- `/carta cancelar` cancela una escritura por chat.

Acciones para menús modulares:

- `OPEN_MAILBOX`
- `START_MAIL_SEND`
- `START_MAIL_SEND_TARGET`
- `START_MAIL_BLOCK`
- `START_MAIL_UNBLOCK`
- `REPLY_MAIL`
- `INVITE_PARTY_TARGET`

Datos:

- `player-data.yml` guarda títulos.
- `mail-data.yml` guarda cartas y bloqueos.

Las cartas expiran automáticamente según `mail.expire-after-days`.



## MDVSocial 1.2.14

Corrige el menú de casas personales:

- `{home_display}` ahora se reemplaza correctamente.
- `{x}`, `{y}`, `{z}` funcionan como ubicación actual del jugador.
- Los botones de casas vacías usan `homes-menu.items.set.missing` cuando existe.
- El menú acepta configs con `homes-menu.items.teleport` y también el formato antiguo `homes-menu.teleport`.
- El filler del menú puede desactivarse para dejar slots vacíos.

## MDVSocial 1.2.5

Agrega sincronizacion automatica para AnimatedScoreboard cuando el jugador esta en party de MMOCore.

- Al entrar al servidor: `animatedscoreboard.party = false` por seguridad.
- Si el jugador esta en party: `animatedscoreboard.party = true`.
- Si el jugador sale de party: `animatedscoreboard.party = false`.
- La sincronizacion se revisa cada `scoreboard-party-permission.sync-interval-ticks` ticks.

Bloque de config recomendado:

```yaml
scoreboard-party-permission:
  enabled: true
  permission: animatedscoreboard.party
  reset-on-join: true
  sync-interval-ticks: 20
  debug: false
```

Config recomendada en AnimatedScoreboard:

```yaml
worlds:
  global:
  - mdvcraftparty
  - defaultscoreboard

  world:
  - mdvcraftparty
  - defaultscoreboard

  world_the_end:
  - mdvcraftparty
  - defaultscoreboard

  world_nether:
  - mdvcraftparty
  - defaultscoreboard

permissions:
  mdvcraftparty: animatedscoreboard.party
```


## MDVSocial 1.2.7

Agrega integración directa con MDVClans como motor de clanes.

Nuevas acciones de menús modulares:

```yaml
action: OPEN_CONDITIONAL_MENU
condition-placeholder: '%mdvclans_is_in_clan%'
condition-equals: 'true'
true-menu: clan_con_clan
false-menu: clan_sin_clan
```

```yaml
action: MDVCLANS_OPEN
clans-menu: miembros
```

`MDVCLANS_OPEN` ejecuta internamente `/clan abrir <menu>`, así MDVSocial puede tener menús bonitos y MDVClans conserva las UIs dinámicas.


## Cambios 1.2.7

- `Menus/clan_con_clan.yml`: el item Tablero de información muestra placeholders de MDVClans y ejecuta `/clan tablero ver` al hacer click.


## MDVSocial 1.2.14

Agrega MDVSocial como proveedor reutilizable de títulos para otros plugins.

Nuevos placeholders target:

- `%mdvsocial_title_of_<jugador>%`
- `%mdvsocial_title_colored_of_<jugador>%`
- `%mdvsocial_title_prefix_of_<jugador>%`
- `%mdvsocial_title_prefix_plain_of_<jugador>%`
- `%mdvsocial_title_id_of_<jugador>%`
- `%mdvsocial_active_title_of_<jugador>%`

Nueva API pública:

```java
MDVSocialAPI.getEquippedTitle(uuid);
MDVSocialAPI.getEquippedTitleColored(uuid);
MDVSocialAPI.getEquippedTitlePlain(uuid);
MDVSocialAPI.getEquippedTitleId(uuid);
MDVSocialAPI.getEquippedTitlePrefix(uuid, true);
```

Esto permite que MDVClans u otros plugins muestren el título equipado de jugadores distintos al jugador que está mirando el menú.

## MDVSocial Core UI desde 1.3.0

MDVSocial puede usarse como base visual para otros plugins propios de MDVCRAFT. La lógica específica debe quedarse en cada plugin, pero pueden reutilizarse sonidos, colores, inventarios y botones comunes mediante `MDVSocialAPI`.

Ejemplo rápido:

```java
Inventory inv = MDVSocialAPI.createInventory("&8MDVRecetas", 54, true);
inv.setItem(45, MDVSocialAPI.createPreviousPageButton());
inv.setItem(49, MDVSocialAPI.createCloseButton());
inv.setItem(53, MDVSocialAPI.createNextPageButton());
player.openInventory(inv);
MDVSocialAPI.playUISound(player, "open");
```

En el listener del plugin externo se puede leer la acción común:

```java
String action = MDVSocialAPI.getButtonAction(event.getCurrentItem());
if (action.equals("CLOSE")) {
    event.getWhoClicked().closeInventory();
    MDVSocialAPI.playUISound((Player) event.getWhoClicked(), "close");
}
```

## Bedrock / Geyser / Floodgate (1.6.0)

MDVSocial 1.6.0 mantiene `Menus/` para Java y añade `MenusBedrock/` para clientes
Bedrock detectados por Floodgate. Los menús Bedrock usan Forms nativos de
Cumulus, mientras ambas interfaces reutilizan la misma lógica social y datos.
Consulta `notes/UPDATE_1.6.0_NOTES.txt` para instalación y formato de configuración.
