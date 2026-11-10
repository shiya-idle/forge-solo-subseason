# Fern clickable class panel

The server-authoritative class panel now sends clickable buttons in chat, so Java and Bedrock players can use the same flow.

- The panel opens automatically when a player enters `forge_class_arena` after the class-arena arrival teleport.
- A crouching right-click with the class weapon opens it.
- A crouching right-click on empty air inside `forge_class_arena` reopens it after the player closes it.
- Clicking a skill button runs `/skills cast <skill-id>`; mana, cooldown, target, and effect checks remain server-side.
- Clicking `CLOSE` runs `/skills close`.
- A novice player receives clickable class choices instead of a skill list.
