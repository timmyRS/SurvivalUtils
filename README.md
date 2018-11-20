# SurvivalUtils

Some essential features to improve the experience on your survival server.

## Features

- Sleep Coordination
- Teleportation Requests (`tpa` & `tpahere`)
- Homes
- Warps

### Planned Features

- Trading

## Permissions

Permission | Description
-----------|-----------
`survivalutils.tpa` | Allows the player to use `/tpa`, `/tpahere`, `/tpaccept`, and `/tpacancel`.
`survivalutils.home` | Allows the player to use `/home`, `/sethome`, `/delhome`, and `/homes`.
`survivalutils.homelimit<2-4>` | Allows the player to use home limit 2, 3, or 4, as defined in the config.yml.
`survivalutils.wrap` | Allows the player to warp to a warp point using `/warp` and list all using `/warps`.
`survivalutils.setwarp` | Allows the player to set a warp point using `/setwarp`.
`survivalutils.delwarp` | Allows the player to delete a warp point using `/delwarp`.
`survivalutils.reload` | Allows the player to reload the configuration using `/reloadsurvivalutils`.

In order to allow you to disable any features you don't want, only OPs have these permissions by default.

If you don't have a permission manager, you can use the `permissions.yml` as follows:

    default:
      default: true
      children:
        survivalutils.tpa: true
        survivalutils.warp: true
        survivalutils.home: true
