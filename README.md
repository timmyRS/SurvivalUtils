# SurvivalUtils

Some essential features to improve the experience on your survival server.

## Features

- Sleep Coordination
- Teleportation Requests (`tpa` & `tphere`)

### Planned Features

- Warps
- Homes
- Trading

## Permissions

Permission | Default | Description
-----------|---------|------------
`survivalutils.tpa` | No | Allows the player to send teleportation requests using `/tpa`.
`survivalutils.reload` | OP | Allows the player to reload the configuration using `/reloadsurvivalutils`.

If you don't have a permission manager, you can use the `permissions.yml` as follows:

    default:
     default: true
     children:
       survivalutils.tpa: true
