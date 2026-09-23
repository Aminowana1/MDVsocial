# MDVSocial 1.6.10 — Restauración de homes y correo SQLite

## Casas (EssentialsX)
- `casa1`, `casa2`, etc. siguen asociadas exclusivamente a su ranura numérica.
- Las homes existentes de EssentialsX con nombres no ajustados al formato `casa{number}` se muestran en huecos libres (sin desplazar las casas numeradas).
- Se muestran también casas preexistentes hasta 9 si superan el número predeterminado de ranuras (las superiores al límite permitido aparecen bloqueadas, pero son eliminables si `allow-delete-locked` está activo).
- El cálculo del bloqueo se alinea con la posición visible, no con el orden de lectura de Essentials.
- No se borran ni se renombran homes de EssentialsX.

## Correo
- Nuevo fichero `plugins/MDVSocial/mail-data.db` (SQLite, WAL).
- Migración automática del `mail-data.yml` antiguo SOLO en el primer arranque de la base de datos. El YAML queda intacto como respaldo; no lo borres antes de confirmar la migración.
- Caché en memoria + persistencia asíncrona en SQLite; solo se escribe el buzón/campaña que cambió, no todos los usuarios en cada mensaje. Las escrituras se encolan en orden y se completan al detener el plugin.
- `player-data.db` y el resto de datos de MDVSocial no se tocan.

## Instalación
1. Detén el servidor y guarda una copia completa de `plugins/MDVSocial/`, `plugins/Essentials/userdata/` y del JAR anterior.
2. Compila mediante GitHub Actions Maven (Java 21) o `mvn clean package`.
3. Instala el JAR `target/MDVSocial-1.6.10.jar` sin eliminar la carpeta de datos.
4. Reinicia el servidor por completo, no uses `/reload` de Bukkit.
5. Verifica homes de un usuario afectado y mensajes enviados/leídos, y confirma que apareció `mail-data.db`.

**Nota:** Este archivo ZIP incluye el proyecto Maven; no incluye un JAR nuevo verificado. Para que la migración sea segura, no reemplacés los datos reales con los de una copia antigua y conservá `mail-data.yml`.
