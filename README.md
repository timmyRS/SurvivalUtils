# SurvivalUtils

 Improve your Survival server's experience with trading, warps, homes, tpa, and more!

## Features

- Trading (Work in Progress)
- Warps
- Homes
- Teleportation Requests (`tpa` & `tpahere`)
- AFK Detection (to prevent AFK farms)
- Sleep Coordination

## Permissions

Permission | Description
-----------|-----------
`survivalutils.trade` | Allows the player to use `/trade`.
`survivalutils.tpa` | Allows the player to use `/tpa`, `/tpahere`, `/tpaccept`, and `/tpacancel`.
`survivalutils.home` | Allows the player to use `/home`, `/sethome`, `/delhome`, and `/homes`.
`survivalutils.homelimit.x` | Allows the player to use home limit 'x' as defined in the config.yml.
`survivalutils.wrap` | Allows the player to use `/warp` and `/warps`.
`survivalutils.setwarp` | Allows the player to use `/setwarp`.
`survivalutils.delwarp` | Allows the player to use `/delwarp`.
`survivalutils.reload` | Allows the player to use `/survivalutils reload`.

In order to allow you to disable any features you don't want, only OPs have these permissions by default.

If you don't have a permission manager, you can use the `permissions.yml` as follows:

    default:
      default: true
      children:
        survivalutils.trade: true
        survivalutils.tpa: true
        survivalutils.warp: true
        survivalutils.home: true
