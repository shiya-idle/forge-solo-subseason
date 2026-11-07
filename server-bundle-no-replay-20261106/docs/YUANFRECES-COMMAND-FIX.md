# Yuanfreces and command fix

The Yuanfreces grant is configured with hexadecimal XUID `000901F1FACCA280`. Floodgate can expose the same XUID as a decimal string. The updated JAR compares both representations, so the grant is now recognized consistently.

Yuanfreces receives Healer, level 1000, skill level 25, Mythic route settings, and operator access. Verified admin profiles are excluded from the normal three-day class-arena teleport flow.

Replace the existing Fern mod JAR and ensure this file exists on the live server:

`config/forge-admins.yml`

The current commands registered by Fern are:

- `/skills`
- `/skills panel`
- `/skills cast <skill-id>`
- `/skills mana true|false`
- `/skills bind <1-18> <skill-id>`
- `/skills unbind <1-18>`
- `/class`
- `/class choose <HEALER|MINER|MARKSMAN|LUMBER|CRAFTSMAN>`
- `/quality list`
- `/quality <power-saving|medium|high|ultra-performance>`
- `/waypoints`
- `/waypoints pvp|class|forge`
- `/waypoint <name>`
- `/claims request ...`
- `/claims confirm ...`
- `/claims cancel`
- `/forge shop list`
- `/forge shop buy <item> <amount>`
- `/forge combiroll list`
- `/forge combiroll craft <materialA> <materialB>`
- `/forge combiroll cast <materialA> <materialB>`
- `/forge drink`

The following commands require operator access: shop/combiroll spawning, difficulty changes, and notices. Yuanfreces receives operator access after reconnecting with Floodgate loaded.
