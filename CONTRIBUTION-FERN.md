# Fern — contribution note

## Waypoint portal hub (isolated v5 candidate)

Implemented and iterated the isolated `/waypoints` portal-hub candidate based on the owner's specification: dimension `fern:forge_waypoint`, arrival `(0,64,0)`, fantasy styling, vanilla portal blocks, and visible destination labels. The hub builds conservatively over a verified floor and clear volume, retries safely when blocked, and keeps commands/routes closed until its marker, labels, and portals validate. The 128-layer dimension stack was retained after Minecraft 26.3 runtime evidence confirmed grass at Y=63 and air at Y=64.

The exact v5 artifacts are:

- JAR: `/workspace/forge-solo-waypoint-candidate-v5-20261122/platform-fabric-waypoint-hub-candidate-v5-20261122.jar`
- JAR SHA-256: `65fa15a84d7915a1050c628f3ec7077b3e058a6d7a51f4ded46595ac121bd759`
- Datapack: `/workspace/forge-solo-waypoint-candidate-v5-20261122/fern-waypoint-hub-candidate-v5-20261122.zip`
- Datapack SHA-256: `7d03ce7aaa11af5728404d501cf6b97e3713227dafe5c85d7d4f6d5e54070812`
- Detailed handoff: `/workspace/forge-solo-waypoint-candidate-v5-20261122/review-and-retest-handoff-v5.md`

Build/package checks, the isolated runtime readiness and obstruction-retry checks, and all five gate keyboard walks passed on those exact artifacts. The gates were crossed from outside their frames and reached their configured dimensions; each remained stable through a 10-second idle check. The Class gate needed one extra alignment step. The Escher walk also showed no bounce or repeat after about 10 seconds.

These player tests used temporary flat destination fixtures in a fresh disposable world. They establish route triggering and short-term stability, not the contents of the real destination maps, Bedrock behavior, or live-server deployment readiness. The candidate remains isolated and was not installed on the live server or copied into the shared checkout.

## Player-list snapshot fix QA

Recorded and coordinated isolated testing of the separate candidate JAR `a94ae3d10e0557433525eb538f3534b9bf5299e6a36527304bfda6dff0fd492d`. Forced disconnect for a missing companion and ordinary disconnect both passed with another supported player online; the remaining player continued receiving updates and no `ConcurrentModificationException` appeared in the candidate/final logs or crash reports. Removal on a tick ending in `...0` was not captured; that timing caveat remains documented. This candidate also remains separate from the active JAR.

## Test log and handoff

The running improvement and QA record is `/workspace/minecraft-test-results/forge-solo-fabric-improvements-20261122.md`. The shared checkout contained pre-existing uncommitted work; this note is the only file added here by Fern. No live world or shared QA save was changed.
