package de.timmyrs;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.MemorySection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
public class SurvivalUtils extends JavaPlugin implements Listener, CommandExecutor
{
	private final ArrayList<Player> sleepingPlayers = new ArrayList<>();
	static final ArrayList<TeleportationRequest> teleportationRequests = new ArrayList<>();
	private int sleepCoordinationTask = -1;

	private Location stringToLocation(String string)
	{
		final String[] arr = string.split(",");
		if(arr.length != 6)
		{
			return null;
		}
		return new Location(getServer().getWorld(arr[0]), Double.valueOf(arr[1]), Double.valueOf(arr[2]), Double.valueOf(arr[3]), Float.valueOf(arr[4]), Float.valueOf(arr[5]));
	}

	private String locationToString(Location location)
	{
		return location.getWorld().getName() + "," + location.getX() + "," + location.getY() + "," + location.getZ() + "," + location.getYaw() + "," + location.getPitch();
	}

	@Override
	public void onEnable()
	{
		getConfig().addDefault("sleepCoordination.enabled", true);
		getConfig().addDefault("sleepCoordination.message", "&e%sleeping%/%total% players are sleeping. Won't you join them?");
		getConfig().addDefault("sleepCoordination.intervalSeconds", 20);
		getConfig().options().copyDefaults(true);
		saveConfig();
		reloadSurvivalUtilsConfig();
		getCommand("tpa").setExecutor(this);
		getCommand("tpahere").setExecutor(this);
		getCommand("tpaccept").setExecutor(this);
		getCommand("tpcancel").setExecutor(this);
		getCommand("warp").setExecutor(this);
		getCommand("warps").setExecutor(this);
		getCommand("setwarp").setExecutor(this);
		getCommand("reloadsurvivalutils").setExecutor(this);
		getServer().getPluginManager().registerEvents(this, this);
		getServer().getScheduler().scheduleSyncRepeatingTask(this, ()->
		{
			final int time = Math.toIntExact(System.currentTimeMillis() / 1000L);
			synchronized(teleportationRequests)
			{
				final ArrayList<TeleportationRequest> _teleportationRequests = new ArrayList<>(teleportationRequests);
				for(TeleportationRequest tr : _teleportationRequests)
				{
					if(tr.expires < time)
					{
						teleportationRequests.remove(tr);
						tr.from.sendMessage("§eYour teleportation request to " + tr.to.getName() + " has expired.");
					}
				}
			}
		}, 200, 200);
	}

	private void reloadSurvivalUtilsConfig()
	{
		reloadConfig();
		if(sleepCoordinationTask != -1)
		{
			getServer().getScheduler().cancelTask(sleepCoordinationTask);
		}
		if(getConfig().getBoolean("sleepCoordination.enabled"))
		{
			sleepCoordinationTask = getServer().getScheduler().scheduleSyncRepeatingTask(this, ()->
			{
				final HashMap<World, ArrayList<Player>> worlds = new HashMap<>();
				synchronized(sleepingPlayers)
				{
					final ArrayList<Player> _sleepingPlayers = new ArrayList<>(sleepingPlayers);
					for(Player p : _sleepingPlayers)
					{
						if(p.isOnline() && p.isSleeping())
						{
							final ArrayList<Player> worldSleepingPlayers;
							if(worlds.containsKey(p.getWorld()))
							{
								worldSleepingPlayers = worlds.get(p.getWorld());
							}
							else
							{
								worldSleepingPlayers = new ArrayList<>();
							}
							worldSleepingPlayers.add(p);
							worlds.put(p.getWorld(), worldSleepingPlayers);

						}
						else
						{
							sleepingPlayers.remove(p);
						}
					}
				}
				for(Map.Entry<World, ArrayList<Player>> entry : worlds.entrySet())
				{
					if(entry.getValue().size() > 0 && entry.getValue().size() < entry.getKey().getPlayers().size())
					{
						final String message = getConfig().getString("sleepCoordination.message").replace("&", "§").replace("%sleeping%", String.valueOf(entry.getValue().size())).replace("%total%", String.valueOf(entry.getKey().getPlayers().size()));
						for(Player p : entry.getKey().getPlayers())
						{
							p.sendMessage(message);
						}
					}
				}
			}, getConfig().getLong("sleepCoordination.intervalSeconds") * 20L, getConfig().getLong("sleepCoordination.intervalSeconds") * 20L);
		}
		else
		{
			sleepCoordinationTask = -1;
		}
		saveConfig();
	}

	@EventHandler
	public void onPlayerBedEnter(PlayerBedEnterEvent e)
	{
		synchronized(sleepingPlayers)
		{
			if(!sleepingPlayers.contains(e.getPlayer()))
			{
				sleepingPlayers.add(e.getPlayer());
			}
		}
	}

	@EventHandler
	public void onPlayerBedLeave(PlayerBedLeaveEvent e)
	{
		synchronized(sleepingPlayers)
		{
			sleepingPlayers.remove(e.getPlayer());
		}
	}

	@Override
	public boolean onCommand(CommandSender s, Command c, String l, String[] a)
	{
		switch(c.getName())
		{
			case "tpa":
			case "tpahere":
				if(s instanceof Player)
				{
					if(a.length == 1)
					{
						final Player t = getServer().getPlayer(a[0]);
						if(t != null && t.isOnline())
						{
							final Player p = (Player) s;
							if(!t.equals(p))
							{
								TeleportationRequest tr = TeleportationRequest.getFrom(p);
								if(tr != null && !tr.to.equals(t))
								{
									synchronized(teleportationRequests)
									{
										teleportationRequests.remove(tr);
									}
									s.sendMessage("§eYour teleportation request to " + tr.to.getName() + " has been cancelled.");
									tr = null;
								}
								if(tr == null)
								{
									final boolean here = c.getName().equals("tpahere");
									synchronized(teleportationRequests)
									{
										teleportationRequests.add(new TeleportationRequest(p, t, here));
									}
									if(here)
									{
										t.sendMessage("§e" + p.getName() + " has requested you to teleport to them.");
									}
									else
									{
										t.sendMessage("§e" + p.getName() + " has requested to teleport to you.");
									}
									t.sendMessage("You can accept it using /tpaccept " + p.getName());
									p.sendMessage("§aYou've sent a teleportation request to " + t.getName() + ".");
									p.sendMessage("You can cancel it using /tpcancel.");
								}
								else
								{
									p.sendMessage("§cI already got it the first time.");
								}
							}
							else
							{
								s.sendMessage("Yes?");
							}
						}
						else
						{
							s.sendMessage("§c'" + a[0] + "' isn't online.");
						}
					}
					else
					{
						s.sendMessage("§cSyntax: /tpa <player>");
					}
				}
				else
				{
					s.sendMessage("§cThis command is only for players.");
				}
				break;
			case "tpaccept":
				if(s instanceof Player)
				{
					if(a.length == 1)
					{
						final Player t = getServer().getPlayer(a[0]);
						if(t != null && t.isOnline())
						{
							final Player p = (Player) s;
							final TeleportationRequest tr = TeleportationRequest.get(t, p);
							if(tr != null)
							{
								synchronized(teleportationRequests)
								{
									teleportationRequests.remove(tr);
								}
								if(tr.here)
								{
									p.teleport(t);
								}
								else
								{
									t.teleport(p);
								}
							}
							else
							{
								s.sendMessage("§c" + t.getName() + " hasn't sent you a teleportation request recently.");
							}
						}
						else
						{
							s.sendMessage("§c'" + a[0] + "' isn't online.");
						}
					}
					else
					{
						s.sendMessage("§cSyntax: /tpaccept <player>");
					}
				}
				else
				{
					s.sendMessage("§cThis command is only for players.");
				}
				break;
			case "tpcancel":
				if(s instanceof Player)
				{
					final TeleportationRequest tr = TeleportationRequest.getFrom((Player) s);
					if(tr != null)
					{
						synchronized(teleportationRequests)
						{
							teleportationRequests.remove(tr);
						}
						s.sendMessage("§eYour teleportation request to " + tr.to.getName() + " has been cancelled.");
					}
					else
					{
						s.sendMessage("§eYou haven't sent a teleportation request recently.");
					}
				}
				else
				{
					s.sendMessage("§cThis command is only for players.");
				}
				break;
			case "warp":
				if(s instanceof Player)
				{
					if(a.length == 1)
					{
						if(getConfig().contains("warps." + a[0].toLowerCase()))
						{
							((Player) s).teleport(stringToLocation(getConfig().getString("warps." + a[0].toLowerCase())));
						}
						else
						{
							s.sendMessage("§c'" + a[0].toLowerCase() + "' is not a valid warp point.");
						}
					}
					else
					{
						s.sendMessage("§cSyntax: /setwarp <player>");
					}
				}
				else
				{
					s.sendMessage("§cThis command is only for players.");
				}
				break;
			case "warps":
				MemorySection warps = (MemorySection) getConfig().get("warps");
				if(warps != null)
				{
					Map<String, Object> values = warps.getValues(false);
					if(values.size() > 0)
					{
						final StringBuilder message = new StringBuilder("There ").append((values.size() == 1 ? "is 1 warp" : "are " + values.size() + " warps")).append(":");
						for(String name : values.keySet())
						{
							message.append(" ").append(name);
						}
						s.sendMessage(message.toString());
					}
					else
					{
						s.sendMessage("§cThere are no warps.");
					}
				}
				else
				{
					s.sendMessage("§cThere are no warps.");
				}
				break;
			case "setwarp":
				if(s instanceof Player)
				{
					if(a.length == 1)
					{
						getConfig().set("warps." + a[0].toLowerCase(), locationToString(((Player) s).getLocation()));
						s.sendMessage("§aSuccessfully created warp point '" + a[0].toLowerCase() + "'.");
						saveConfig();
					}
					else
					{
						s.sendMessage("§cSyntax: /setwarp <player>");
					}
				}
				else
				{
					s.sendMessage("§cThis command is only for players.");
				}
				break;
			case "reloadsurvivalutils":
				reloadSurvivalUtilsConfig();
				s.sendMessage("§aReloaded the configuration.");
				break;
		}
		return true;
	}
}

class TeleportationRequest
{
	final Player from;
	final Player to;
	final boolean here;
	final int expires;

	TeleportationRequest(Player from, Player to, boolean here)
	{
		this.from = from;
		this.to = to;
		this.here = here;
		this.expires = Math.toIntExact(System.currentTimeMillis() / 1000L) + 60;
	}

	static TeleportationRequest get(Player from, Player to)
	{
		synchronized(SurvivalUtils.teleportationRequests)
		{
			for(TeleportationRequest tr : SurvivalUtils.teleportationRequests)
			{
				if(tr.from.equals(from) && tr.to.equals(to))
				{
					return tr;
				}
			}
		}
		return null;
	}

	static TeleportationRequest getFrom(Player from)
	{
		synchronized(SurvivalUtils.teleportationRequests)
		{
			for(TeleportationRequest tr : SurvivalUtils.teleportationRequests)
			{
				if(tr.from.equals(from))
				{
					return tr;
				}
			}
		}
		return null;
	}
}
