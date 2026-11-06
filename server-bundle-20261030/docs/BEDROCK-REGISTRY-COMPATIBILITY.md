# Bedrock registry compatibility

The Fern Fabric JAR uses vanilla `SoundEvent` entries for cast and notice audio. It does not register custom `forge_solo_subseason` blocks, items, or sounds that would be required by a vanilla/Geyser client during Fabric registry synchronization.

Bedrock clients still connect through Geyser-Fabric and Floodgate. Their visual assets come from the Bedrock `.mcpack`; custom Java-only client HUD features remain unavailable to Bedrock and use Fern's server-side fallback HUD.
