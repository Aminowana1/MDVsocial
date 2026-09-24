# MDVSocial 1.6.12 — Optimización

Actualización sobre tu fuente 1.6.11. Conserva comandos, menús Java/Bedrock, protecciones, permisos, miembros, homes, correo, títulos y castigos. No cambia los límites de los rangos ni añade teletransporte a las protecciones.

## Qué cambió

| Área | Antes | Ahora |
| --- | --- | --- |
| Correo al iniciar | Cargaba todos los buzones y campañas en memoria | Carga cada buzón al necesitarlo; conserva hasta 128 secciones recientes por defecto |
| Escrituras de correo | Una tarea nueva por guardado; cola sin límite | Agrupa cambios por sección; como máximo 256 secciones pendientes y una tarea escritora |
| Títulos y castigos | Consultas SQLite repetidas por cada campo, incluso en revisiones periódicas | Perfiles recientes en caché, hasta 8192; las lecturas repetidas usan memoria y los valores sin cambios no se vuelven a escribir |
| Desconexiones | Algunas sesiones permanecían hasta completar/cancelar acciones | Limpia composición de correo, perfil y permisos temporales, además de las sesiones de menús |
| Protecciones | Consulta de regiones de todos los mundos en el hilo del servidor | Consulta mediante la API de PS en un único trabajador; máximo 64 consultas esperando y una consulta activa por jugador |
| Trabajo periódico | Recorría todos los jugadores en una ejecución | Comparte un presupuesto por tick para perfiles de chat, títulos, permisos de party y revisión del ítem social |
| Correo global | Recorrió todos los destinatarios en una ejecución | Envío, consulta y eliminación de campañas por lotes, con mensaje al completar |
| Homes suspendidas | Escritura del archivo en el hilo del servidor | Guardado en segundo plano, reemplazo atómico cuando el sistema lo permite y una sola instantánea pendiente |
| Biblioteca MMOItems | Conservaba cada ítem consultado hasta recargar | Caché de hasta 256 ítems por defecto |
| Ítem social | Reconstruía sus metadatos en cada revisión | Reutiliza una plantilla y clona el ítem cuando tiene que entregarlo/reemplazarlo |

Las acciones de inventario, los ítems, los bloques y los cambios de regiones siguen ejecutándose en el hilo del servidor. El escaneo asíncrono usa `PSPlayer.getPSRegions`, que la API de ProtectionStones documenta para consultas asíncronas. El propietario y los permisos se vuelven a verificar antes de eliminar o cambiar miembros.

## Instalación

1. Apaga el servidor y guarda una copia del JAR anterior y la carpeta de MDVSocial.
2. Reemplaza el JAR 1.6.11 por `MDVSocial-1.6.12.jar`, dejando una sola versión instalada.
3. Conserva todas tus configuraciones, bases SQLite y archivos de datos. No borres ni regeneres carpetas.
4. Inicia el servidor. Se mantienen Paper 1.21.6 / Java 21 como objetivo de compilación y la integración con ProtectionStones 2.10.5.

No requiere migrar tus protecciones: siguen perteneciendo a ProtectionStones/WorldGuard. El formato de las bases de correo y títulos permanece compatible. Las migraciones de archivos antiguos se conservan; el correo antiguo se valida antes de confirmar su importación.

## Configuración opcional

Funciona con tu configuración actual. Estos son los nuevos valores predeterminados, que puedes agregar al nivel principal de `config.yml`:

```yaml
performance:
  mail-cache-size: 128
  profile-cache-size: 8192
  item-cache-size: 256
  work-budget-ms: 2
  work-items-per-tick: 32
```

- Las cachés expulsan lo menos usado; eso **no elimina datos guardados**. Un buzón o perfil expulsado vuelve a cargarse cuando hace falta.
- El límite de correo cuenta secciones/buzones, no cartas ni megabytes. Un buzón muy grande puede ocupar bastante memoria por sí solo; no se recortan cartas para ahorrar RAM.
- El presupuesto de trabajo se comprueba entre operaciones. Una operación individual lenta puede superar esos milisegundos; no es un límite absoluto del tiempo total de MDVSocial.
- Reducir el presupuesto reparte más el trabajo, pero demora más las actualizaciones y operaciones masivas.
- Usa `/mdvsocial reload` después de cambiar estos ajustes. La caché de correo se reconstruye con el nuevo límite.

Mantén tu sección `protections-menu` y los permisos `protectionstones.limit.1` a `.4` tal como los tengas configurados.

## Comportamientos a tener en cuenta

- El menú de protecciones puede tardar un momento en abrir mientras se consulta la API. Las solicitudes repetidas del mismo jugador se agrupan. Si la cola está llena, muestra un aviso para volver a intentarlo; no acumula solicitudes indefinidamente.
- El correo global avisa que está trabajando por lotes y muestra el resultado al terminar. No permite solapar operaciones administrativas de campañas que puedan interferirse.
- Una parada o recarga ordenada termina los trabajos administrativos aceptados y espera los guardados pendientes. Eso puede prolongar la parada si hay un envío grande en curso.
- Si el disco no logra seguir el ritmo, la cola de correo aplica espera en vez de crecer sin límite o descartar mensajes. Un fallo de disco se registra como error.
- Los cambios de títulos/castigos mantienen escritura inmediata para conservar su persistencia. La primera lectura de un perfil o buzón que no esté en caché aún consulta SQLite sincrónicamente. También permanecen algunos accesos puntuales de la base, como búsquedas de nombres y el fallback de Essentials a YAML. No se presenta esta versión como totalmente libre de operaciones de disco en el hilo principal.
- Como con otros guardados asíncronos, un cierre forzado del proceso puede perder cambios que todavía no llegaron al disco. Usa una parada ordenada.

## Verificación

Compilación final correcta: **18 pruebas superadas, 0 fallos, 0 errores y 0 omitidas**. La suite incluye las 8 pruebas existentes de eliminación de protecciones y pruebas nuevas con SQLite real, expulsión de caché, reapertura, migración, trabajo por ticks y guardado de homes.

Resultados de las pruebas sintéticas:

- 10.000 buzones existentes: 0 contenidos cargados al abrir la base; enumerar sus identificadores tampoco carga los contenidos. Lectura de todos los buzones con un máximo configurado de 64 residentes, conservando mensajes, bloqueos y marcas de bienvenida.
- 5.000 perfiles: después de cargarlos, 150.000 lecturas de campos sin cargas adicionales de perfiles; la limpieza libera los 5.000.
- Actualizaciones rápidas con caché de solo 2 buzones: conservación del último contenido al expulsar y volver a abrir la base.
- Eliminación/recreación de buzones, migración de correo, listas de títulos, castigos y campos adicionales conservados.
- 1.000 solicitudes de guardado de homes: al cerrar queda la última instantánea completa, sin temporales sobrantes.

Compilación y pruebas: `mvn verify` con Java 21.

**Estas pruebas no simulan miles de clientes Minecraft conectados.** No hay una medición de RAM total, TPS/MSPT ni latencia de menús con tu conjunto de plugins. Antes de afirmar una capacidad de miles de jugadores simultáneos hay que medirlo en el servidor, incluyendo WorldGuard, ProtectionStones, MMOCore, PlaceholderAPI y el hardware.

Tras instalar, comprueba Java y Bedrock: abrir menús, miembros de protecciones, eliminación con inventario lleno/cancelación, homes suspendidas, cartas y correo global, títulos/castigos, recarga y un reinicio completo.
