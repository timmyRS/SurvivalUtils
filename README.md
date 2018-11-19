# SurvivalUtils

Some essential features to improve the experience on your survival server.

## Features

- Sleep Coordination
- Teleportation Requests (`tpa` & `tpahere`)
- Warps

### Planned Features

- Homes
- `/spawn`
- Trading

## Permissions

Permission | Description
-----------|-----------
`survivalutils.tpa` | Allows the player to send teleportation requests using `/tpa`.
`survivalutils.wrap` | Allows the player to warp to a warp point using `/warp`.
`survivalutils.setwarp` | Allows the player to set a warp point using `/setwarp`.
`survivalutils.reload` | Allows the player to reload the configuration using `/reloadsurvivalutils`.

In order to allow you to disable any features you don't want, these permissions are not granted by default.

If you don't have a permission manager, you can use the `permissions.yml` as follows:

    default:
      default: true
      children:
        survivalutils.tpa: true
        survivalutils.warp: true
    op:
      default: 'op'
      children:
        survivalutils.setwarp: true
        survivalutils.reload: true