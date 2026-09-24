# MDVSocial 1.6.11 — Tus protecciones

Gestor de protecciones integrado en MDVSocial, usando la API pública de ProtectionStones 2.10.5.
Conserva las funciones anteriores de homes, correo, títulos y menús sociales.

## Instalación

1. Detén el servidor y guarda una copia del JAR actual y de las carpetas de MDVSocial, WorldGuard y ProtectionStones.
2. Sustituye el JAR anterior de MDVSocial por `MDVSocial-1.6.11.jar`. No dejes las dos versiones juntas.
3. Conserva tus configuraciones y bases de datos actuales. No es necesario borrar carpetas.
4. Mantén ProtectionStones 2.10.5, WorldGuard y sus dependencias instalados. Para formularios Bedrock, Floodgate.
5. Inicia el servidor y abre `/protes` (también `/protecciones` y `/misprotes`).

La base del ZIP compila para Paper 1.21.6 y Java 21; esta actualización mantiene ese objetivo.
No se ha hecho una prueba dentro de tu servidor ni con un cliente Bedrock real.

## Funciones

- Lista las protecciones propias en todos los mundos cargados, con paginación si corresponde.
- Java: inventario que clona el ítem oficial de ProtectionStones, conservando material, nombre, lore, brillo y datos personalizados; añade coordenadas y miembros al lore del menú.
- Bedrock: formularios nativos. La lista muestra nombre, material y coordenadas; el detalle muestra el lore oficial como texto. Los formularios no tienen un ítem de inventario con tooltip como Java.
- Detalle con mundo, X/Y/Z del bloque colocado, miembros y eliminación remota. No hay botón ni acción de teletransporte.
- Miembros: ver, agregar y quitar con confirmación. No permite quitar propietarios desde esta lista.
- Java permite seleccionar jugadores conectados. Para uno desconectado conocido, pulsa «Escribir un nombre» y usa `/protes agregar <nombre>` dentro de 2 minutos; `/protes cancelar` vuelve al menú.
- Bedrock permite escribir el nombre en un formulario. Usa el nombre exacto, incluido el prefijo de Floodgate. El jugador debe haber entrado antes y ser conocido por el servidor; no se fabrican UUID por nombre.
- Las protecciones fusionadas aparecen por bloque. La eliminación afecta al bloque seleccionado mediante la API; los miembros se comparten con el grupo, y el menú lo indica.

## Límites y permisos

El máximo se consulta con `PSPlayer.getGlobalRegionLimits()`, por lo que se usan tus permisos actuales:

| Permiso de rango | Máximo |
| --- | ---: |
| `protectionstones.limit.1` | 1 |
| `protectionstones.limit.2` | 2 |
| `protectionstones.limit.3` | 3 |
| `protectionstones.limit.4` | 4 |

El menú no impone un segundo límite ni altera los permisos de colocación. Para que el rango superior permita 4, asígnale `.4` y no un límite mayor. La colocación sigue siendo responsabilidad de ProtectionStones. Si su API devuelve «sin límite», el menú lo dice. Las protecciones que excedan el límite tras bajar de rango siguen visibles y gestionables; no se eliminan automáticamente.

Permisos del menú (habilitados por defecto):

- `mdvsocial.protections.use`: abrir.
- `mdvsocial.protections.members`: gestionar miembros; también requiere `protectionstones.members`.
- `mdvsocial.protections.remove`: eliminar; también requiere `protectionstones.unclaim` y `protectionstones.unclaim.remote`.

Ejemplo de LuckPerms para tu rango de 3 protecciones, reemplazando `nombre_del_rango`:

```text
/lp group nombre_del_rango permission set protectionstones.limit.3 true
/lp group nombre_del_rango permission set protectionstones.members true
/lp group nombre_del_rango permission set protectionstones.unclaim true
/lp group nombre_del_rango permission set protectionstones.unclaim.remote true
```

## Devolución del bloque

La eliminación pide confirmación e identifica la protección y sus coordenadas. Se vuelve a comprobar el propietario y los permisos al confirmar.

Se exige un espacio vacío en el inventario para devolver el bloque original. Sin espacio, no se elimina la protección. El bloque se entrega **después** de que ProtectionStones confirme la eliminación; si otro plugin cancela `PSRemoveEvent`, no se entrega nada. Los bloques ocultos se manejan mediante la API de ProtectionStones.

Se respeta `behaviour.no_drop` de cada bloque de ProtectionStones: si está activado no se devuelve el ítem, y la confirmación lo avisa. Tampoco se permite eliminar una protección alquilada o cuyo tipo ya no esté configurado. Si otro plugin llena el inventario durante el evento de eliminación, el bloque recuperado se deja junto al jugador para evitar perderlo.

Configuración opcional para añadir al `config.yml` existente; estos valores son los predeterminados incluso si no agregas la sección:

```yaml
protections-menu:
  enabled: true
  return-block: true
```

Puedes recargar esos valores con `/mdvsocial reload`. Las modificaciones de regiones y miembros se guardan mediante el mecanismo normal de WorldGuard/ProtectionStones, sin una segunda base de datos.

## Botón en menús sociales existentes

Los menús nuevos incluidos en el JAR ya traen el botón. Los archivos personalizados que ya existen en tu servidor se conservan. Añade estos bloques si quieres incorporarlo allí.

En `plugins/MDVSocial/Menus/main.yml`, dentro de `items` (cambia el slot si el 22 está ocupado):

```yaml
  protecciones:
    slot: 22
    material: EMERALD_BLOCK
    name: '&aTus protecciones'
    lore:
      - '&7Coordenadas, miembros y eliminación remota.'
      - '&eClick para gestionar.'
    action: COMMAND_PLAYER
    commands:
      - 'mdvsocial:protes'
    permission: mdvsocial.protections.use
```

En `plugins/MDVSocial/MenusBedrock/main.yml`, dentro de `buttons`:

```yaml
  protecciones:
    text: "&aTus protecciones\n&7Coordenadas y miembros"
    action: COMMAND_PLAYER
    commands:
      - 'mdvsocial:protes'
    permission: mdvsocial.protections.use
```

Después usa `/mdvsocial reload`.

## Verificación recomendada en el servidor

Prueba una cuenta con `.3` y otra con `.4`; distintos materiales/lore; una protección oculta; agregar/quitar un jugador Java y uno Bedrock, incluyendo uno desconectado; confirmar y cancelar la eliminación; inventario lleno; cambio de propietario; y una protección fusionada si usas esa función. Reinicia y comprueba que los cambios de miembros y eliminaciones persisten.

Las pruebas automáticas del proyecto comprueban permisos revocados, pérdida de propietario, inventario lleno, cancelación/fallo de la API y devolución posterior a la eliminación. Compilar y ejecutar: `mvn clean verify` con Java 21.

Referencia de API cotejada: https://github.com/espidev/ProtectionStones/tree/2.10.5/src/main/java/dev/espi/protectionstones
