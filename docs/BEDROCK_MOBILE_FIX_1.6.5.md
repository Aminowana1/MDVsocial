# MDVSocial 1.6.5 - Bedrock mobile navigation fix

## Root cause fixed

1.6.4 used a global time debounce between different Forms. A tap on the newly-opened Form could be marked as consumed and then discarded if it arrived inside the debounce window. This was especially easy to reproduce on touch clients.

1.6.5 removes cross-form debounce completely. Every Form has a unique token and accepts exactly one valid response. The selected action is dispatched on the Bukkit thread 1 tick later for every Bedrock input type.

## Form sending

Forms are built before sending and are sent through the resolved `FloodgatePlayer` instance:

`floodgatePlayer.sendForm(builder.build())`

## Configuration

Recommended optional section:

```yaml
bedrock:
  form-navigation:
    delay-ticks: 1
    debug: false

  mobile-safety:
    enabled: true
    dynamic-page-size: 5
```

The old timing keys from 1.6.4 are ignored for navigation.

## If the server has old MenusBedrock files

MDVSocial intentionally preserves existing administrator-edited YAML files. If a previous build left a wrong action or `target-menu` in the server's `plugins/MDVSocial/MenusBedrock/`, updating the JAR will not overwrite that custom value.

For a clean test:

1. Stop the server.
2. Rename `plugins/MDVSocial/MenusBedrock` to `MenusBedrock-backup`.
3. Start the server once so 1.6.5 generates fresh defaults.
4. Test `/social` on mobile.
5. Reapply only your image URLs/text customizations if needed.

The Java `Menus/` folder is not affected.

## Debug

Enable temporarily:

```yaml
bedrock:
  form-navigation:
    debug: true
```

Example route log:

`[BedrockUI] Player: open menu=main page=1 previous= visible=10`
`[BedrockUI] Player: click menu=main index=0 button=perfil action=OPEN_MENU target=menuperfil`
`[BedrockUI] Player: open menu=menuperfil page=1 previous=main visible=5`
