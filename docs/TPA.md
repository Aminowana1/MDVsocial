# MDVSocial - TPA (viajes entre jugadores)

Antes vivía en MDVTools. Ahora es de MDVSocial y MDVTools ya no registra `/tpa`.

## Comandos

| Comando | Qué hace |
|---|---|
| `/tpa <jugador>` | Pide viajar hasta ese jugador |
| `/tpa aceptar`, `/tpaccept`, `/aceptartp` | Acepta la solicitud más reciente |
| `/tpa rechazar`, `/tpdeny`, `/rechazartp` | Rechaza la solicitud más reciente |
| `/tpa cancelar`, `/tpacancel` | Cancela tu solicitud enviada |
| `/tpa ayuda` | Ayuda (`tpa.help`) |
| `/tpa aceptarsolicitud <uuid>` / `rechazarsolicitud <uuid>` | Usados por los botones del chat |

Permiso: `mdvsocial.tpa` (por defecto todos). Sustituye a `mdvtools.tpa`.
El autocompletado muestra a tus amigos primero.

## Cómo funciona

- Cada jugador tiene una sola solicitud enviada; una nueva reemplaza la anterior.
- Caduca a los `request-timeout-seconds` y hay `cooldown-seconds` entre envíos.
- Al aceptar empieza una espera de `teleport-delay-seconds`. En los primeros
  `movement-grace-seconds` puedes moverte; después moverte, recibir daño o atacar
  la cancela (`cancel-delay-on-*`).
- Al llegar tienes `invulnerability-after-teleport-seconds` de protección. Atacar la
  termina. El vacío, `/kill` y el borde del mundo siempre hacen daño.
- **Bloqueos:** si el destinatario te bloqueó, la solicitud no llega y ves
  `request-not-delivered` ("No se pudo enviar..."), igual que con cartas y grupos.
- Al desconectarse alguien se cancela todo lo que le afecta y se avisa al otro.
- **Grupo de Aventura:** si quien pide es de tu grupo, la solicitud se acepta sola y
  se avisa a los dos (`party-auto-accepted`, `party-auto-accepted-target`).
  Para tratarlas como cualquier otra: `tpa.party-auto-accept.ignore: true`.
- **Menús:** al enviar una solicitud se cierran los menús de quien la envía (Java y
  Bedrock). Si se rechaza (cooldown, jugador no encontrado...) el menú sigue abierto.
  En Bedrock, Floodgate no puede cerrar un formulario desde el servidor; solo Geyser
  puede si corre en este mismo servidor. Por eso basta con no volver a abrir ninguno.

## Avisos al recibir una solicitud (`tpa.notice`)

| Cliente | Config | Contenido |
|---|---|---|
| Java | `notice.java.format` | Líneas JSON con botones ACEPTAR / RECHAZAR |
| Bedrock | `notice.bedrock.message` | Chat + quick accept (agacharse) |
| Bedrock sin quick accept | `notice.bedrock.message-without-quick-accept` | Chat |

Tokens: `{player}`, `{uuid}`, `{seconds}` (para responder) y, en Bedrock,
`{sneaks}` y `{quick_seconds}`.

Quick accept usa el tipo `quick-accept.types.tpa`. Si quien pide es tu amigo y
`notice.bedrock.skip-confirmation-for-friends` es `true`, se acepta directamente;
si no, se abre "Viaje de X" con Aceptar / Rechazar / Más tarde.

## Menú Bedrock "Viajes (TPA)" (MenusBedrock/tpa.yml)

Desde "Social y Amigos" (`OPEN_BEDROCK_TPA`). Muestra:

- Solicitar viaje: el selector "Escoger jugador", con tus amigos primero.
- Solicitudes (n): las recibidas, con aceptar o rechazar.
- Cancelar mi solicitud: solo si tienes una enviada.

En servidores con un `menuamigos.yml` ya creado, el botón `tpa` se añade solo
pero queda al final (después de "Volver"); muévelo a su sitio a mano.

Contadores para cualquier texto de MenusBedrock: `{tpa_requests}` y `{tpa_requests_badge}`.

Los botones "Solicitar viaje" de las opciones de amigo/jugador siguen usando el
comando `tpa {target}`.

## Mensajes

`tpa.messages.*`, con el prefijo `tpa.prefix`. Tokens entre llaves (`{player}`);
el formato antiguo `%player%` también funciona. Un mensaje vacío no se envía.

## Migración desde MDVTools

Al arrancar, si el `config.yml` de MDVSocial no tiene sección `tpa` y existe
`plugins/MDVTools/config.yml` con una, se copia sola al final del config de
MDVSocial:

- Mismas claves y valores; `%token%` pasa a `{token}`.
- El prefijo de MDVTools (`messages.prefix`) pasa a `tpa.prefix`.
- `messages.request-received` no se copia: ese texto ahora es la primera línea de
  `tpa.notice.java.format`.

La sección `tpa` que quede en el config de MDVTools ya no se usa y se puede borrar.

## Código (`com.mdvcraft.mdvsocial.tpa`)

| Clase | Papel |
|---|---|
| `TpaModule` | Fachada: crea todo, `enable` / `reload` / `shutdown` |
| `TpaService` | Enviar, aceptar, rechazar, cancelar, caducar |
| `TpaRequestBook` | Solicitudes pendientes, sin Bukkit (testeable) |
| `TpaTeleporter` | Espera, teletransporte y protección al llegar |
| `TpaNotifier` | Avisos Java/Bedrock y quick accept |
| `TpaListener` | Moverse, daño, ataques y desconexión |
| `TpaCommand` | Comandos y autocompletado |
| `TpaSettings` / `TpaMessages` | Configuración y textos |
| `TpaHost` | Lo que necesita del resto del plugin (bloqueos, amigos, Bedrock) |
| `MDVToolsTpaImport` | Importación única de la config de MDVTools |

Para otros plugins: `MDVSocialPlugin#tpa().service()`.
