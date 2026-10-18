# MDVSocial - Avisos de grupo y amistad, y Quick Accept

## Requisito

Los mensajes `party-invite` y `friend-request` de **MMOCore deben estar
silenciados** en su `messages.yml`. MDVSocial envía los suyos en su lugar; si no,
el jugador recibe los dos avisos.

Al arrancar, MDVSocial comprueba que puede leer las invitaciones de MMOCore. Si
no puede (por ejemplo tras actualizar MMOCore), escribe un error en consola
pidiendo reactivar el mensaje de MMOCore, porque nadie recibiría avisos.

## Cómo se detectan

`MMOCoreRequestWatcher` lee una vez por ciclo el mapa global de solicitudes de
MMOCore (`mmocore-requests.check-interval-ticks`, por defecto 20 = 1 s; si no
existe se usa el antiguo `party-invites.check-interval-ticks`) y avisa una sola
vez por solicitud, sea invitación a grupo o solicitud de amistad. Cubre `/grupo`,
los menús, la GUI de MMOCore y otros plugins. Las solicitudes hechas desde
MDVSocial se avisan al instante.

- Coste: proporcional a las invitaciones pendientes, no a los jugadores conectados.
- Tras un `/reload` las invitaciones que ya existían no se vuelven a avisar.
- Si quien recibe bloqueó a quien envía, la solicitud se retira de MMOCore en
  silencio: no hay aviso, no aparece en los menús y quien la envió no se entera.
- Las solicitudes de MMOCore caducan a los 2 minutos (fijo en MMOCore).

## Solicitudes de amistad

Mismo sistema que los grupos, configurado en `friend-requests`:

| Cliente | Config |
|---|---|
| Java | `friend-requests.java.format` (botones `/mmocore:friends accept/deny {uuid}`) |
| Bedrock | `friend-requests.bedrock.message` / `message-without-quick-accept` |

- Quick accept usa `quick-accept.types.friend-request` y abre "Solicitud de X"
  con Aceptar, Rechazar y Más tarde (`requests-menu.detail.later` en
  `amigos_lista.yml`). Con `friend-requests.bedrock.skip-confirmation: true`
  acepta directamente (por defecto `false`).
- Si el destinatario te bloqueó: `friend-request-not-delivered` (genérico).
- Si tú lo tienes bloqueado: `friend-request-target-blocked` (te pide desbloquearlo).
- Solicitud repetida: desde MDVSocial avisa `friend-request-already-sent`; desde la
  GUI de MMOCore se retira la repetida en silencio. Los menús muestran una por jugador.
- Solicitud mutua: si el otro ya te había enviado una, se acepta la suya y quedan
  como amigos (MMOCore avisa a ambos).
- Espera entre envíos desde MDVSocial: `friend-requests.send-cooldown-seconds`
  (por defecto 10). Permiso para saltarla: `mdvsocial.friends.cooldown.bypass`.

## Mensajes

| Cliente | Config | Contenido |
|---|---|---|
| Java | `party-invites.java.format` | Líneas JSON con `&`, botones `/mmocore:party accept/deny {uuid}` |
| Bedrock | `party-invites.bedrock.message` | Chat: quién invita, dónde verlo y el gesto de quick accept |
| Bedrock sin quick accept | `party-invites.bedrock.message-without-quick-accept` | Chat: quién invita y dónde verlo |

Tokens: `{player}` (quien invita), `{uuid}` (id de la invitación en MMOCore, el que esperan
`accept`/`deny`; antes se ponía por error el UUID de quien invita), `{sneaks}` y `{seconds}`
(del quick accept).

## Quick Accept (Bedrock)

Agacharse `sneaks-required` veces seguidas, con no más de
`max-sneak-gap-millis` entre agachadas, dentro de `window-seconds` desde que
llega la invitación:

- Si quien invita es tu amigo y `party-invites.bedrock.skip-confirmation-for-friends`
  es `true` (por defecto): se acepta directamente y se abre el menú del grupo.
- Si no: se abre el formulario "Invitación de X" con Aceptar, Rechazar y
  Más tarde (`requests-menu.detail.later` en `party.yml`).
- Si la invitación ya se respondió o caducó, el gesto no hace nada.
- Tolerante a agacharse muy rápido: el tiempo entre agachadas se mide desde que te
  levantas, cuentan también las agachadas que otro plugin cancela, y si el servidor
  solo ve que te levantas (sin haber visto la agachada), eso cuenta como una.
  Una agachada y levantada dentro del mismo tick (50 ms) nunca llega al servidor.
- Cada jugador tiene una sola oferta activa: una invitación nueva reemplaza la
  anterior (que sigue en el menú de Invitaciones).

### Configuración

```yaml
quick-accept:
  enabled: true              # interruptor global
  bedrock-only: true
  sneaks-required: 2
  window-seconds: 30
  max-sneak-gap-millis: 700
  trigger-sound: 'confirm'   # clave de ui-core.sounds
  messages:
    expired: ''              # aviso al acabar el tiempo (vacío = nada)
  types:
    party-invite:            # cualquier clave de arriba se puede sobrescribir por tipo
      enabled: true
    tpa:                     # solicitudes de TPA (ver TPA.md)
      enabled: true
```

### Usarlo para otras solicitudes

El sistema no depende de los grupos (`com.mdvcraft.mdvsocial.quickaccept`):

```java
plugin.getQuickAccept().offer(player, new QuickAcceptRequest(
        "teleport",                                  // quick-accept.types.teleport
        p -> aceptarTeleport(p),                     // al completar el gesto
        p -> sigueHabiendoPeticion(p)));             // opcional: validez
```

`isAvailable(player, type)` dice si se ofrecerá (para elegir el texto del aviso)
y `settings(type)` devuelve los valores efectivos (`sneaksRequired`, `windowSeconds`…).

## Cambios respecto a antes

- Se eliminó `bedrock.party.java-fallback-buttons`: el mensaje Java con botones
  ahora lo envía siempre `party-invites.java`, para todas las invitaciones.
