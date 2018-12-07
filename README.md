# SurvivalUtils

Essential features to improve the experience on your survival server:

- Warps
  - Warp Signs
  - Per-Warp Permissions
- Homes
  - Infinite Configurable Limits
- Teleportation Requests (tpa)
  - TP Here Requests (tpahere)
  - Cancellable
- Colored Signs
- AFK Detections
  - Anti-AFK Farming
  - Enhanced AFK Kick
- Sleep Coordination
  - Skip At Percentage

All features are disabled/OP-only by default to allow for maximum configurability.

## Permissions

- `survivalutils.tpa` grants all `survivalutils.tpa.*` permissions:
  - `survivalutils.tpa.tpa` allows the player to use `/tpa`.
  - `survivalutils.tpa.tpahere` allows the player to use `/tpahere`.
  - `survivalutils.tpa.tpaccept` allows the player to use `/tpaccept`.
  - `survivalutils.tpa.tpcancel` allows the player to use `/tpcancel`.
- `survivalutils.home` allows the player to use `/home`, `/sethome`, `/delhome`, and `/homes`.
- `survivalutils.homelimit.x` allows the player to use home limit 'x' as defined in the config.yml.
- `survivalutils.allowafk` allows the player to bypass all AFK detections.
- `survivalutils.warp` allows the player to use `/warp`, `/warps`, and warp commands, if enabled.
- `survivalutils.warps` allows the player to warp everywhere.
- `survivalutils.warps.x` allows the player to warp to warp 'x'.
- `survivalutils.warpsigns` allows the player to place warp signs.
- `survivalutils.managewarps` allows the player to use `/setwarp` and `/delwarp`.
- `survivalutils.coloredsigns` allows the player to use colors on signs.
- `survivalutils.reload` allows the player to use `/survivalutils reload`.

Remember: All permissions are OP-only by default to allow for maximum configurability.

If you don't have a permission manager, you can use the permissions.yml as follows:

    default:
      default: true
      children:
        survivalutils.tpa: true
        survivalutils.home: true
        survivalutils.warp: true
        survivalutils.warps: true

Similarly, you can use the permissions.yml to remove permissions from OPs:

    op:
      default: 'op'
      children:
        survivalutils.allowafk: false

## Configuration

- `homeLimits`
  - `default`: The default home limit
  - `op`: The home limit for OPs
  - Other keys can be used to define custom permissions. For example, if you place `vip: 20` in here, players with the `survivalutils.homelimit.vip` permissions can create up to 20 homes.
- `antiAFKFarming`
  - `enabled`: Prevent AFK farming? (true/false)
  - `seconds`: After `seconds` seconds of not moving, a player will be prevented from attacking and interacting.
- `afkKick`
  - `enabled`: Enable AFK kicking? (true/false)
  - `seconds`: After `seconds` seconds of not moving, a player will be kicked.
  - `message`: The kick reason message.
- `sleepCoordination`
  - `enabled`: Enable Sleep Coordination? (true/false)
  - `message`: The message that will be sent to all players in a dimension when at least one player is sleeping.
  - `intervalTicks`: The amount of ticks to wait before broadcasting the message. (1 second = 20 ticks)
  - `skipPercent`: The percent of players that have to sleep for the night or storm to end.
- `createWarpCommands`: Create warp commands, e.g. `/spawn` to alias `/warp spawn`? (true/false)
- `warpSigns`
  - `line`: The first line of warp signs
  - `color`: The [color code](https://wiki.vg/Chat#Colors) to use for the first line of warp signs
- `warps`: All created warps. I don't recommend you manually modify this.

Don't forget to run `/survivalutils reload` to reload the configuration once you're done changing it.
