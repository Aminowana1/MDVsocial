# MDVSocial - Bases de datos y copias de seguridad

## Qué se guarda dónde

| Archivo | Contenido |
|---|---|
| `player-data.db` | Títulos desbloqueados, título activo, castigos, bloqueos, límite de buzón |
| `mail-data.db` | Cartas y buzones |
| `titles.yml`, `ranks.yml` | Definición de títulos y rangos (un título que no exista aquí no cuenta como desbloqueado) |

Los títulos de rango no se guardan: dependen del `unlock-permission` (LuckPerms).

## WAL: por qué copiar solo la .db no basta

Las dos bases usan SQLite en modo WAL. Cada cambio se escribe primero en
`<base>.db-wal` y se vuelca a la `.db` más tarde (checkpoint). Mientras el servidor
está encendido, la `.db` sola puede no tener los cambios recientes.

MDVSocial vuelca el WAL:

- cada `storage.checkpoint-interval-minutes` (5 por defecto; 0 = solo al apagar);
- al apagar el servidor o recargar el plugin.

Aun así, si copias las bases con el servidor encendido, copia también los `-wal` y
`-shm`, o usa `/mdvsocial backup`.

## /mdvsocial backup

Permiso `mdvsocial.admin`. Crea copias completas de las dos bases en
`plugins/MDVSocial/backups/<fecha>/` con `VACUUM INTO`: un solo archivo por base,
consistente y sin `-wal`, seguro con el servidor encendido. Incluye las cartas que
aún estaban en memoria. Se conservan las `storage.backups.keep` más recientes (10;
0 = todas).

## Restaurar

1. Apaga el servidor.
2. Borra de `plugins/MDVSocial` la base a restaurar **y** sus `-wal` / `-shm`. Un
   `-wal` de otra base al lado de la restaurada puede aplicarse encima y dañarla.
3. Copia la `.db` de `backups/<fecha>/`. Si viene de una copia del servidor
   encendido, copia la `.db` junto con su `-wal` y `-shm`.
4. Enciende el servidor.

Para revisar una copia antes de restaurarla:

```
sqlite3 player-data.db "SELECT uuid, title_id FROM player_unlocked_titles LIMIT 20;"
```
