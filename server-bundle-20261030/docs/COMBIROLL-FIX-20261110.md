# Combiroll compatibility fix

The Combiroll commands still use the existing `/forge combiroll` command tree, but material arguments now accept both short names and vanilla item names.

Examples:

- `/forge combiroll craft copper iron`
- `/forge combiroll craft copper_ingot iron_ingot`
- `/forge combiroll craft amethyst_shard diamond`
- `/forge combiroll cast netherite_ingot amethyst_shard`

The server canonicalizes material order, keeps crafting transactional, and ignores invalid or empty ownership entries when loading `config/forge-combirolls.properties`.
