# WorldEdit access policy

Fern now blocks WorldEdit command execution for all players except the verified Floodgate XUID grant for Yuanfreces.

The allow-list entry is in `config/forge-admins.yml` and requires:

```yaml
worldedit: true
```

The guard covers `//...`, `/worldedit`, and `/wea` command forms. Unknown players and console-originated WorldEdit commands are denied by default. Restart the server after replacing the Fern JAR and configuration.
