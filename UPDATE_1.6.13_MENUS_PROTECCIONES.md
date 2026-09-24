# MDVSocial 1.6.13 — Menús de protecciones editables

## Instalación

1. Detén el servidor, sustituye el JAR anterior por MDVSocial-1.6.13.jar y vuelve a iniciarlo. Mantén un solo JAR de MDVSocial.
2. No necesitas añadir nada a config.yml para esta actualización.
3. Los archivos nuevos se generan automáticamente dentro de plugins/MDVSocial/Menus y plugins/MDVSocial/MenusBedrock. Los archivos existentes no se sobrescriben.
4. Para cambiar textos, materiales, lore o posiciones, edita los YAML y ejecuta /mdvsocial reload. Las definiciones se leen al iniciar o recargar, no cada vez que un jugador abre un menú.

Las protecciones y sus miembros siguen siendo los datos de ProtectionStones/WorldGuard. No hay migración de regiones ni una segunda base de datos de protecciones.

## Archivos

En ambas carpetas:

| Archivo | Pantalla |
| --- | --- |
| protes.yml | Lista principal de protecciones |
| protes_opciones.yml | Opciones de la protección seleccionada |
| protes_miembros.yml | Lista de miembros |
| protes_buscar.yml | Jugadores conectados y acceso a escribir un nombre |
| protes_confirmar.yml | Confirmación compartida para quitar un miembro o borrar una protección |

Solo en MenusBedrock: protes_nombre.yml configura el formulario para introducir el nombre exacto, incluido el prefijo Bedrock.

En Java, title configura el título; filler el panel; header el objeto superior; items los botones y sus textos; navigation la vuelta y las flechas. En Bedrock, content configura el texto del formulario, items.<botón>.text su etiqueta y navigation los textos de navegación. Los campos de materiales, lore y slots corresponden a Java; los formularios nativos Bedrock utilizan texto.

Conserva managed-by: protections. Estos menús dinámicos se abren con /protes o desde el botón de protecciones del menú social. Sus acciones y comprobaciones de permisos se mantienen en el código.

## Distribución de Java

- Principal de 54 espacios: protecciones en 19, 21, 23 y 25; libro en 4; volver en 49. Sin paginación.
- Si en el futuro hay más de cuatro protecciones, overflow-slots añade 37, 39, 41 y 43, en las mismas columnas dos filas más abajo. Cambiar slots no modifica los permisos ni el límite de ProtectionStones. Si se superan los ocho espacios, amplía la distribución: se muestra un error en lugar de ocultar protecciones silenciosamente.
- Opciones de 54 espacios: ítem original de la protección en 4; mapa con coordenadas en 20; miembros en 22; barrera para borrar en 24; volver en 49.
- Miembros y buscador: 28 entradas por página, agregar/escribir nombre en 8, volver en 49, anterior en 48 y siguiente en 50. Solo aparecen las flechas que llevan a otra página. No hay indicador de número de página.
- Confirmación de 27 espacios: volver/cancelar en 11 y confirmar en 15. El lore de confirmar identifica el objetivo y sus advertencias.
- Relleno negro en todos estos menús. Ninguno tiene botón de cerrar en Java.

Los slots empiezan en 0 y se cuentan de izquierda a derecha. No superpongas contenido, cabecera, controles ni overflow-slots. Si un YAML no se puede cargar o su distribución Java es inválida, la consola avisa y se usa el diseño original sin modificar tu archivo.

## Ítems y textos

El principal y la cabecera de opciones conservan el ítem auténtico de ProtectionStones, su nombre y su lore. preserve-lore: true añade las líneas del menú al lore original. Para conservar también su material y nombre, deja esos campos sin definir en el ítem de protección.

Variables disponibles según la pantalla:

- Principal: {player}, {count}, {limit}, {limit_notice}, {empty_notice}.
- Protección seleccionada o entrada de protección: {protection}, {item_name}, {material}, {world}, {x}, {y}, {z}, {members}, {group_notice}, {details}.
- Entrada de miembro/jugador: {member}, {uuid}.
- Confirmación: {action}, {target}, {warning}; la advertencia de borrado también usa {refund_notice}.
- Buscador: {count} es el número de jugadores mostrados en la lista completa.

La lista de miembros y el buscador usan el perfil real de los jugadores conectados para las cabezas. Los miembros desconectados mantienen su identidad mediante el perfil conocido del servidor. La textura visible depende de que ese perfil tenga una skin disponible; no se hacen consultas externas bloqueantes para obtenerla.

## Funcionamiento y comprobaciones

Se mantienen las optimizaciones de 1.6.12. Las cabezas e ítems de las listas Java se construyen solo para la página visible; Bedrock no construye esos ítems. Se mantienen las comprobaciones de propietario, permisos y formularios/clics vigentes al ejecutar cada acción.

Si corresponde devolver el bloque y el inventario está lleno, la protección no se elimina. Una cancelación de ProtectionStones tampoco devuelve el bloque. Las protecciones fusionadas mantienen las advertencias de miembros compartidos.

Verificación: compilación para Java 21/Paper 1.21.6 y 24 pruebas automatizadas, incluidas distribución, navegación, persistencia de ediciones, perfiles de cabezas, seguridad de eliminación y almacenamiento optimizado. No se realizó una prueba visual dentro de clientes Java/Bedrock ni una prueba de carga con jugadores reales.

