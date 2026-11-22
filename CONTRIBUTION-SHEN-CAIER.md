# Shen Cai’er contribution — 2026-11-23

## Waypoint Hub v5 review

Reviewed the isolated v5 waypoint-hub implementation and the cited Minecraft 26.3 runtime evidence. Confirmed the dimension stack is 128 blocks from Y=-64 through Y=63, with grass at Y=63 and the player arriving at Y=64; the 119-stone-layer definition matches the runtime probes. Reviewed the 100-tick retry cadence, the occupied-footprint guard, readiness checks for the hub marker, five labels and portal blocks, and the mixin’s hub-only vanilla portal interception. Found no blocker within that review scope.

The runtime log confirms that the deliberately obstructed hub was left untouched, then safely built after the obstruction was cleared; it also confirms all five portal blocks and labels and 16 forced chunks. The exact v5 JAR and datapack subsequently passed five separate keyboard gate walks, each stable through a 10-second idle check. Those tests used temporary flat destination fixtures, so they do not validate real destination-map contents, Bedrock behavior, or a live-server installation.

## Scope

This was an implementation review and QA coordination contribution; I did not change mod source code, the live server, or shared QA worlds. The candidate remains separate from the live server.