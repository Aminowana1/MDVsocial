# MDVSocial - Avisos de invitación a grupo y Quick Accept

## Requisito

El mensaje de invitación a party de **MMOCore debe estar silenciado** en su
`messages.yml`. MDVSocial envía el suyo en su lugar.

Al arrancar, MDVSocial comprueba que puede leer las invitaciones de MMOCore. Si
no puede (por ejemplo tras actualizar MMOCore), escribe un error en consola
pidiendo reactivar el mensaje de MMOCore, porque nadie recibiría avisos.

## Cómo se detectan

`PartyInviteWatcher` lee una vez por ciclo el mapa global de solicitudes de
MMOCore (`party-invites.check-interval-ticks`, por defecto 20 = 1 s) y avisa
una sola vez por invitación. Cubre `/grupo`, los menús, la GUI de MMOCore y
otros plugins. Las invitaciones hechas desde MDVSocial se avisan al instante.

- Coste: proporcional a las invitaciones pendientes, no a los jugadores conectados.
- Tras un `/reload` las invitaciones que ya existían no se vuelven a avisar.
- Si el invitado bloqueó a quien invita, no recibe ningún aviso.

## Mensajes

| Cliente | Config | Contenido |
|---|---|---|
| Java | `party-invites.java.format` | Líneas JSON con `&`, botones `/mmocore:party accept/deny {uuid}` |
| Bedrock | `party-invites.bedrock.message` | Chat: quién invita, dónde verlo y el gesto de quick accept |
| Bedrock sin quick accept | `party-invites.bedrock.message-without-quick-accept` | Chat: quién invita y dónde verlo |

Tokens: `{player}` (quien invita), `{uuid}` (su UUID), `{sneaks}` y `{seconds}`
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
