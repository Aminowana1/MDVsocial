# MDVSocial - Arquitectura Bedrock

Todo el código de menús Bedrock (Floodgate/Cumulus) vive en el paquete
`com.mdvcraft.mdvsocial.bedrock`. El módulo no conoce `MDVSocialPlugin`:
habla con él solo a través de interfaces (puertos), y el plugin las implementa
con adaptadores.

```
com.mdvcraft.mdvsocial
├── MDVSocialPlugin            crea BedrockModule y delega en él
├── BedrockHostAdapter         implementa BedrockHost
├── BedrockTitlesAdapter       implementa TitlesPort
├── BedrockMailAdapter         implementa MailPort
├── BedrockSocialAdapter       implementa SocialPort
└── bedrock/
    ├── BedrockModule          fachada única del módulo
    ├── port/                  contratos con el plugin
    │   ├── BedrockHost        config, textos, mensajes, sonidos, navegación
    │   ├── TitlesPort         títulos y rangos
    │   ├── MailPort           buzón
    │   └── SocialPort         amigos y party (MMOCore)
    ├── platform/
    │   └── FloodgateGateway   detección de Floodgate y envío de Forms
    ├── session/
    │   └── BedrockUiSessionManager  token por Form, anti respuestas atrasadas
    ├── menu/                  menús estáticos de MenusBedrock/*.yml
    │   ├── model/             BedrockMenuDefinition, BedrockMenuButton, BedrockMenuContext
    │   ├── BedrockMenuRepository   carga recursiva y auto-actualización
    │   ├── BedrockMenuParser       YAML -> modelo
    │   ├── BedrockVisibility       reglas visible-when
    │   └── BedrockMenuRenderer     modelo -> SimpleForm
    ├── action/
    │   ├── BedrockAction          handler de una acción
    │   ├── BedrockActionRegistry  normaliza alias y despacha clics
    │   ├── BuiltinBedrockActions  registra las acciones incluidas
    │   └── BedrockNavigation      volver, reabrir, abrir hijo, comandos
    └── forms/                 formularios dinámicos
        ├── BedrockFormSupport textos, botones con imagen, sesiones, envío
        ├── TitlesForms  MailForms  FriendsForms  PartyForms
        ├── MMOCoreForms       perfil, atributos y razas de MMOCore
        └── LinkMessages       mensajes de tienda/Discord por chat
```

## Flujo de un clic

1. `MDVSocialPlugin.openCustomMenu` detecta un jugador Bedrock y llama a
   `BedrockModule.openMenu`.
2. `BedrockMenuRenderer` toma la definición del repositorio, filtra los botones
   con `BedrockVisibility` y permisos, inicia una sesión y envía el Form.
3. Al responder, `BedrockUiSessionManager` descarta respuestas de Forms
   anteriores o duplicadas.
4. `BedrockActionRegistry` normaliza la acción y ejecuta su handler.

## Añadir una acción nueva

Desde cualquier parte con acceso al módulo:

```java
plugin.bedrock().actions().register((player, button, context) -> {
    // ...
}, "MI_ACCION");
```

No hace falta tocar ningún `switch`. El test
`BedrockActionRegistryTest.everyActionInBundledMenusIsRegistered` falla si un
YAML incluido en el jar usa una acción sin handler.

## Usar Bedrock desde otros managers

- Detectar jugador: `plugin.isBedrockPlayer(player)`.
- Mostrar un SimpleForm con una acción por botón:
  `plugin.bedrock().forms().show(player, builder, actions)`.
- Forms con handler propio (CustomForm, confirmaciones):
  `sessions().begin(player)`, `sessions().run(player, session, action)` y
  `gateway().send(player, form)`.
- Leer un YAML de MenusBedrock: `plugin.bedrock().forms().ui("homes")`.

## Carpeta MenusBedrock

- Los archivos por defecto se copian a la raíz de `plugins/MDVSocial/MenusBedrock/`,
  igual que antes.
- Los menús se cargan **recursivamente**: se pueden agrupar en subcarpetas
  (por ejemplo `MenusBedrock/clan/clan.yml`). El ID del menú es siempre el
  nombre del archivo sin extensión.
- Si dos archivos tienen el mismo ID, gana el menos profundo y se avisa en consola.
- Un menú por defecto movido a una subcarpeta se sigue auto-actualizando en su
  nueva ubicación; no se vuelve a crear en la raíz.
- Los archivos con `managed-by: protections` (`protes_*.yml`) los gestiona
  `ProtectionMenuFiles` y deben quedarse en la raíz.

## Fix móvil

Se mantiene el comportamiento de 1.6.4/1.6.5: cada Form recibe un token único
antes de registrar su handler y solo el token activo puede consumirse una vez.
Desde esta versión, Hogares y la Biblioteca de MMOItems también pasan por el
gestor de sesiones, así que reciben la misma protección.
