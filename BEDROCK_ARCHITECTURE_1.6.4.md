# MDVSocial 1.6.4 - Arquitectura Bedrock

El código Bedrock queda separado por responsabilidad para evitar que toda la
navegación, configuración y estado de Forms viva en una única clase.

- `BedrockMenuManager`: renderiza `SimpleForm` estáticos desde `MenusBedrock`.
- `BedrockMenuRepository`: carga/mezcla/parsea los YAML de `MenusBedrock`.
- `BedrockUiSessionManager`: controla sesión por Form, respuestas duplicadas y
  delay específico para touch/controller/keyboard.
- `BedrockMenuContext`: contexto de navegación y target.
- `BedrockMenuDefinition`: modelo parseado de un menú.
- `BedrockMenuButton`: modelo y reglas de visibilidad de un botón.

## Fix móvil

Cada Form obtiene un token único al ser enviado. El callback conserva ese token.
Si llega una respuesta atrasada de un Form que ya fue reemplazado, se descarta.
Esto evita que una respuesta del formulario anterior ejecute `BACK`, `OPEN_MENU`
u otra acción sobre el formulario que acaba de abrirse.

Además, las transiciones usan por defecto:

- Touch: 10 ticks
- Controller: 4 ticks
- Keyboard/mouse: 2 ticks

Los valores son configurables bajo `bedrock.mobile-safety`.
