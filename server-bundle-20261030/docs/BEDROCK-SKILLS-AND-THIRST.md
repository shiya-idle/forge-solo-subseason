# Bedrock skills and thirst

Bedrock players are server-authoritative participants. They can cast skills with `/skills cast <skill-id>` and open the text skill panel with `/skills` or `/skills panel`.

The action bar now shows health, mana, thirst, and the first six assigned skill slots. These are visible skill indicators, not clickable Bedrock buttons. The `/skills` panel lists the skill IDs needed for casting. A true clickable Floodgate form is not included because this Fabric mod deliberately has no hard Floodgate API dependency.

Thirst drains at 0.35 points per second instead of 0.75. It refills when the player is in water, drinks a plain water potion, or throws a plain splash water potion. Other potions do not refill thirst. The empty-thirst health penalty remains 2% of maximum health per second.
