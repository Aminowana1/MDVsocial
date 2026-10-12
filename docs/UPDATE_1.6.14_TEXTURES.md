# MDVSocial 1.6.14

El renderizador de protecciones Java ignoraba el campo texture. Ahora aplica las texturas mediante el mismo soporte de cabezas que los menús generales: acepta Base64 y URL directa, además de los alias custom-head-texture, head-texture, skull-texture y texture-base64.

Se aplica a navegación, botones, cabecera y relleno cuando el material es PLAYER_HEAD. Sin una textura explícita, se conserva el perfil original de la cabeza, incluidas las skins de miembros y jugadores del buscador.

Reemplaza el JAR con el servidor apagado. Conserva tus archivos de configuración. Dentro de navigation.back en Menus/protes.yml y los otros menús de protecciones, conserva el slot y configura material: PLAYER_HEAD, texture, name y lore. El slot habitual de volver es 49, pero en la confirmación pequeña es 11.

No se añade nada a config.yml. Tras instalar esta versión, las ediciones de los YAML se aplican con /mdvsocial reload.
