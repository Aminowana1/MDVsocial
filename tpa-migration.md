# Migración del TPA de MDVTools a MDVSocial

El TPA ahora vive en MDVSocial. MDVTools 1.0.29 ya no registra ningún comando de
TPA ni lee su configuración. Detalles del sistema en [docs/TPA.md](docs/TPA.md).

## Pasos en el servidor

1. **Instala las dos versiones a la vez.**
   Sube juntos el MDVSocial nuevo y MDVTools 1.0.29 y reinicia. Si conviven una
   versión antigua de MDVTools y el MDVSocial nuevo, los dos registran `/tpa` y
   Bukkit elige uno según el orden de carga.

2. **Comprueba que la configuración se importó.**
   Al arrancar, si el `config.yml` de MDVSocial no tiene sección `tpa`, se copia
   sola la de `plugins/MDVTools/config.yml` al final del archivo. En consola
   aparece:

   ```
   Configuración de TPA importada desde MDVTools/config.yml.
   ```

   Se conservan tus valores y el prefijo de MDVTools (como `tpa.prefix`), y
   `%player%` pasa a `{player}`. Después puedes borrar la sección `tpa` del
   `config.yml` de MDVTools: ya no se usa.

3. **Actualiza los permisos.**
   El permiso pasa de `mdvtools.tpa` a `mdvsocial.tpa` (por defecto lo tienen
   todos). Si en LuckPerms dabas o negabas `mdvtools.tpa`, cámbialo:

   ```
   /lp group <grupo> permission set mdvsocial.tpa true
   ```

4. **Revisa el mensaje de solicitud recibida (solo si lo personalizaste).**
   `tpa.messages.request-received` de MDVTools no se importa. Ese texto ahora es
   la primera línea de `tpa.notice.java.format` en el `config.yml` de MDVSocial.
   Si lo tenías personalizado, cópialo ahí.

5. **Coloca el botón "Viajes (TPA)" en el menú Bedrock.**
   En un `MenusBedrock/menuamigos.yml` ya existente, el botón `tpa` se añade solo
   pero queda al final, después de "Volver". Muévelo a su sitio a mano, o borra el
   archivo para que se regenere con el orden por defecto.

## Qué comprobar después

- `/tpa <jugador>` desde Java muestra los botones ACEPTAR / RECHAZAR al otro jugador.
- Un jugador Bedrock recibe el aviso en el chat y puede responder agachándose
  2 veces rápido o desde Social y Amigos » Viajes (TPA).
- Si un jugador te bloqueó, tu solicitud muestra "No se pudo enviar..." y no le llega.
- `/mdvsocial reload` aplica los cambios de la sección `tpa`.
