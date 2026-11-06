# Yuanfreces XUID-bound admin profile

The server config grants the profile only when Floodgate reports the exact XUID `000901F1FACCA280`. The username is only a label and is not used for authorization.

Grant:

- Operator: enabled automatically on join
- Class: HEALER
- Route: MYTHIC route slots (the strongest configured route tier)
- Player level: 1000
- Skill level: 25, the configured maximum

The player must join through Geyser/Floodgate for the XUID lookup to work. If Floodgate is missing or does not return the XUID, no operator grant is applied.
