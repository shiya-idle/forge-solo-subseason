# Interaction and class update

Skill buttons were removed from the Bedrock action-bar HUD. A player opens the skill panel by crouching and using the class weapon on air. This is crouch + right-click on Java and crouch + light press on Bedrock; both arrive through the server item-use path.

Class weapons:

- Healer: Book
- Miner: Pickaxe; any pickaxe counts, and the starter is iron
- Marksman: Bow or crossbow; the starter is a bow
- Lumber: Axe; any axe counts, and the starter is iron
- Craftsman: Crafting Table

The class weapon is granted only after the player has a class and only when the inventory does not already contain that class weapon. Craftsman opens the server crafting-panel message by using the crafting table on air without crouching.

Normal unclaimed land is breakable again. An existing immutable claim remains modifiable only by its owner. Protected claims and building-effect purge behavior remain enabled.

A normal player is not silently assigned the global `default-class`. Connected playtime is persisted in `config/forge-player-classes.properties`. After 72,000 connected server ticks (three complete 24,000-tick Minecraft days), the player may permanently choose one class with `/class choose <class>`.
