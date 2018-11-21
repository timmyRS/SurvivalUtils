package de.timmyrs;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
public class SurvivalUtils extends JavaPlugin implements Listener, CommandExecutor
{
	private File playerDataDir;
	static final ArrayList<TradeRequest> tradeRequests = new ArrayList<>();
	static final ArrayList<TeleportationRequest> teleportationRequests = new ArrayList<>();
	private final HashMap<Player, Long> playersLastActivity = new HashMap<>();
	private final ArrayList<Player> afkPlayers = new ArrayList<>();
	private final ArrayList<Player> sleepingPlayers = new ArrayList<>();
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
		playerDataDir = new File(getDataFolder(), "playerdata");
		if(!playerDataDir.exists() && !playerDataDir.mkdir())
		{
			throw new RuntimeException("Failed to create " + playerDataDir.getPath());
		}
		getConfig().addDefault("sleepCoordination.enabled", true);
		getConfig().addDefault("sleepCoordination.message", "&e%sleeping%/%total% players are sleeping. Won't you join them?");
		getConfig().addDefault("sleepCoordination.intervalSeconds", 20);
		getConfig().addDefault("homeLimits.default", 10);
		getConfig().addDefault("homeLimits.op", 100);
		getConfig().addDefault("afkDetection.enabled", false);
		getConfig().addDefault("afkDetection.kick", false);
		getConfig().addDefault("afkDetection.kickMessage", "You have been kicked for being AFK. Feel free to reconnect now that you're no longer AFK.");
		getConfig().addDefault("createWarpCommands", false);
		getConfig().addDefault("warps", new HashMap<String, Object>());
		getConfig().options().copyDefaults(true);
		saveConfig();
		reloadSurvivalUtilsConfig();
		getCommand("survivalutils").setExecutor(this);
		getCommand("trade").setExecutor(this);
		getCommand("tpa").setExecutor(this);
		getCommand("tpahere").setExecutor(this);
		getCommand("tpaccept").setExecutor(this);
		getCommand("tpcancel").setExecutor(this);
		getCommand("home").setExecutor(this);
		getCommand("homes").setExecutor(this);
		getCommand("sethome").setExecutor(this);
		getCommand("delhome").setExecutor(this);
		getCommand("warp").setExecutor(this);
		getCommand("warps").setExecutor(this);
		getCommand("setwarp").setExecutor(this);
		getCommand("delwarp").setExecutor(this);
		getServer().getPluginManager().registerEvents(this, this);
		getServer().getScheduler().scheduleSyncRepeatingTask(this, ()->
		{
			synchronized(teleportationRequests)
			{
				final ArrayList<TeleportationRequest> _teleportationRequests = new ArrayList<>(teleportationRequests);
				final long time = getTime();
				for(TeleportationRequest tr : _teleportationRequests)
				{
					if(tr.expires < time)
					{
						teleportationRequests.remove(tr);
						tr.from.sendMessage("§eYour teleportation request to " + tr.to.getName() + " has expired.");
					}
				}
			}
			if(getConfig().getBoolean("afkDetection.enabled"))
			{
				final Map<Player, Long> _playersLastActivity;
				synchronized(playersLastActivity)
				{
					_playersLastActivity = new HashMap<>(playersLastActivity);
				}
				synchronized(afkPlayers)
				{
					final long afkTime = getTime() - 60;
					for(Map.Entry<Player, Long> entry : _playersLastActivity.entrySet())
					{
						if(!afkPlayers.contains(entry.getKey()) && entry.getValue() < afkTime)
						{
							if(getConfig().getBoolean("afkDetection.kick"))
							{
								entry.getKey().kickPlayer(getConfig().getString("afkDetection.kickMessage").replace("&", "§"));
							}
							else
							{
								afkPlayers.add(entry.getKey());
							}
						}
					}
				}
			}
		}, 100, 100);
	}

	static long getTime()
	{
		return System.currentTimeMillis() / 1000L;
	}

	private File getConfigFile(Player p)
	{
		return new File(playerDataDir, p.getUniqueId().toString().replace("-", "") + ".yml");
	}

	private int getHomeLimit(Player p)
	{
		if(p.isOp())
		{
			return getConfig().getInt("homeLimits.op");
		}
		final Map<String, Object> limits = getConfig().getConfigurationSection("homeLimits").getValues(false);
		for(String limit : limits.keySet())
		{
			if(!limit.equals("default") && !limit.equals("op") && p.hasPermission("survivalutils.homelimit." + limit))
			{
				return getConfig().getInt("homeLimits." + limit);
			}
		}
		return getConfig().getInt("homeLimits.default");
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
		synchronized(playersLastActivity)
		{
			synchronized(afkPlayers)
			{
				if(getConfig().getBoolean("afkDetection.enabled"))
				{
					if(playersLastActivity.size() == 0)
					{
						final long time = getTime();
						for(Player p : getServer().getOnlinePlayers())
						{
							playersLastActivity.put(p, time);
						}
						afkPlayers.clear();
					}
				}
				else
				{
					playersLastActivity.clear();
					afkPlayers.clear();
				}
			}
		}
		saveConfig();
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent e)
	{
		if(getConfig().getBoolean("afkDetection.enabled"))
		{
			synchronized(playersLastActivity)
			{
				playersLastActivity.put(e.getPlayer(), getTime());
			}
		}
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent e)
	{
		if(getConfig().getBoolean("afkDetection.enabled"))
		{
			synchronized(playersLastActivity)
			{
				playersLastActivity.remove(e.getPlayer());
			}
			synchronized(afkPlayers)
			{
				afkPlayers.remove(e.getPlayer());
			}
		}
	}

	@EventHandler
	public void onPlayerMove(PlayerMoveEvent e)
	{
		if(getConfig().getBoolean("afkDetection.enabled"))
		{
			synchronized(playersLastActivity)
			{
				playersLastActivity.put(e.getPlayer(), getTime());
			}
			synchronized(afkPlayers)
			{
				afkPlayers.remove(e.getPlayer());
			}
		}
	}

	@EventHandler
	public void onEntityDamageByEntity(EntityDamageByEntityEvent e)
	{
		if(e.getDamager() instanceof Player && getConfig().getBoolean("afkDetection.enabled"))
		{
			final Player p = (Player) e.getDamager();
			synchronized(afkPlayers)
			{
				if(afkPlayers.contains(p))
				{
					e.setCancelled(true);
				}
			}
		}
	}

	@EventHandler
	public void onPlayerInteract(PlayerInteractEvent e)
	{
		if(getConfig().getBoolean("afkDetection.enabled"))
		{
			synchronized(afkPlayers)
			{
				if(afkPlayers.contains(e.getPlayer()))
				{
					e.setCancelled(true);
				}
			}
		}
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

	@EventHandler
	public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent e)
	{
		if(getConfig().getBoolean("createWarpCommands"))
		{
			final String command = e.getMessage().substring(1).split(" ")[0].toLowerCase();
			if(getServer().getPluginCommand(command) == null && getConfig().contains("warps." + command))
			{
				e.setCancelled(true);
				e.getPlayer().teleport(stringToLocation(getConfig().getString("warps." + command)));
			}
		}
	}

	@Override
	public boolean onCommand(CommandSender s, Command c, String l, String[] a)
	{
		switch(c.getName())
		{
			case "survivalutils":
				if(a.length > 0 && a[0].equalsIgnoreCase("reload") && s.hasPermission("survivalutils.reload"))
				{
					reloadSurvivalUtilsConfig();
					s.sendMessage("§aReloaded the configuration.");
				}
				else
				{
					s.sendMessage("https://github.com/timmyrs/SurvivalUtils");
				}
				break;
			case "trade":
				if(s instanceof Player)
				{
					if(a.length == 1)
					{
						// TODO
						s.sendMessage("§eTrading is coming soon!");
					}
					else
					{
						s.sendMessage("§cSyntax: /trade <player>");
					}
				}
				else
				{
					s.sendMessage("§cThis command is only for players.");
				}
				break;
			case "tpa":
			case "tpahere":
				if(s instanceof Player)
				{
					if(a.length == 1)
					{
						final Player t = getServer().getPlayer(a[0]);
						if(t != null && t.isOnline())
						{
							if(t.hasPermission("survivalutils.tpa"))
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
								s.sendMessage("§c" + t.getName() + " is missing the survivalutils.tpa permission, so they can't /tpaccept.");
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
							s.sendMessage("§c'" + a[0].toLowerCase() + "' is not a warp point.");
						}
					}
					else
					{
						s.sendMessage("§cSyntax: /warp <name>");
					}
				}
				else
				{
					s.sendMessage("§cThis command is only for players.");
				}
				break;
			case "warps":
			{
				final Map<String, Object> warps = getConfig().getConfigurationSection("warps").getValues(false);
				if(warps.size() > 0)
				{
					final StringBuilder message = new StringBuilder(warps.size() == 1 ? "There is 1 warp:" : "There are " + warps.size() + " warps:");
					for(String name : warps.keySet())
					{
						message.append(" ").append(name);
					}
					s.sendMessage(message.toString());
				}
				else
				{
					s.sendMessage("There are no warps.");
				}
				break;
			}
			case "setwarp":
				if(s instanceof Player)
				{
					if(a.length == 1)
					{
						if(getConfig().contains("warps." + a[0].toLowerCase()))
						{
							s.sendMessage("§cWarp point '" + a[0].toLowerCase() + "' already exists. Run /delwarp " + a[0].toLowerCase() + " first.");
						}
						else
						{
							getConfig().set("warps." + a[0].toLowerCase(), locationToString(((Player) s).getLocation()));
							s.sendMessage("§aSuccessfully created warp point '" + a[0].toLowerCase() + "'.");
							saveConfig();
						}
					}
					else
					{
						s.sendMessage("§cSyntax: /setwarp <name>");
					}
				}
				else
				{
					s.sendMessage("§cThis command is only for players.");
				}
				break;
			case "delwarp":
			{
				if(a.length == 1)
				{
					final Map<String, Object> warps = getConfig().getConfigurationSection("warps").getValues(false);
					if(warps.containsKey(a[0].toLowerCase()))
					{
						warps.remove(a[0].toLowerCase());
						getConfig().set("warps", warps);
						saveConfig();
						reloadConfig();
						s.sendMessage("§aSuccessfully deleted warp point '" + a[0].toLowerCase() + "'.");
					}
					else
					{
						s.sendMessage("§c'" + a[0].toLowerCase() + "' is not a warp point.");
					}
				}
				else
				{
					s.sendMessage("§cSyntax: /delwarp <name>");
				}
				break;
			}
			case "home":
				if(s instanceof Player)
				{
					if(a.length > 1)
					{
						s.sendMessage("§cSyntax: /home <name>");
					}
					else
					{
						final String homename;
						if(a.length == 1)
						{
							homename = a[0].toLowerCase();
						}
						else
						{
							homename = "home";
						}
						final Player p = (Player) s;
						final YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(getConfigFile(p));
						if(playerConfig.contains("homes"))
						{
							final Map<String, Object> homes = playerConfig.getConfigurationSection("homes").getValues(false);
							if(homes.containsKey(homename))
							{
								p.teleport(stringToLocation((String) homes.get(homename)));
							}
							else if(homes.size() == 1)
							{
								p.teleport(stringToLocation((String) homes.values().iterator().next()));
							}
							else
							{
								p.sendMessage("§cYou don't have a home named '" + homename + "'.");
							}
						}
						else
						{
							p.sendMessage("You're homeless. :^)");
						}
					}
				}
				else
				{
					s.sendMessage("§cThis command is only for players.");
				}
				break;
			case "homes":
				if(s instanceof Player)
				{
					final Player p = (Player) s;
					final YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(getConfigFile(p));
					if(playerConfig.contains("homes"))
					{
						final Map<String, Object> homes = playerConfig.getConfigurationSection("homes").getValues(false);
						if(homes.size() > 0)
						{
							final StringBuilder message = new StringBuilder("You have ").append(homes.size()).append("/").append(getHomeLimit(p)).append(" homes:");
							for(String name : homes.keySet())
							{
								message.append(" ").append(name);
							}
							s.sendMessage(message.toString());
						}
						else
						{
							p.sendMessage("You're homeless. :^)");
						}
					}
					else
					{
						p.sendMessage("You're homeless. :^)");
					}
				}
				else
				{
					s.sendMessage("§cThis command is only for players.");
				}
				break;
			case "sethome":
				if(s instanceof Player)
				{
					if(a.length > 1)
					{
						s.sendMessage("§cSyntax: /sethome <name>");
					}
					else
					{
						final String homename;
						if(a.length == 1)
						{
							homename = a[0].toLowerCase();
						}
						else
						{
							homename = "home";
						}
						final Player p = (Player) s;
						final File playerConfigFile = getConfigFile(p);
						final YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerConfigFile);
						if(getHomeLimit(p) == 0 || (playerConfig.contains("homes") && playerConfig.getConfigurationSection("homes").getValues(false).size() >= getHomeLimit(p)))
						{
							p.sendMessage("§cYou can't create more than " + getHomeLimit(p) + " homes.");
						}
						else if(playerConfig.contains("homes." + homename))
						{
							p.sendMessage("§cYou already have a home named '" + homename + "'. Run /delhome " + homename + " first.");
						}
						else
						{
							playerConfig.set("homes." + homename, locationToString(p.getLocation()));
							try
							{
								playerConfig.save(playerConfigFile);
							}
							catch(IOException e)
							{
								throw new RuntimeException(e.getMessage());
							}
							s.sendMessage("§aSuccessfully created home '" + homename + "'.");
						}
					}
				}
				else
				{
					s.sendMessage("§cThis command is only for players.");
				}
				break;
			case "delhome":
				if(s instanceof Player)
				{
					if(a.length == 1)
					{
						final Player p = (Player) s;
						final File playerConfigFile = getConfigFile(p);
						final YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerConfigFile);
						if(playerConfig.contains("homes." + a[0].toLowerCase()))
						{
							final Map<String, Object> homes = playerConfig.getConfigurationSection("homes").getValues(false);
							homes.remove(a[0].toLowerCase());
							playerConfig.set("homes", homes);
							try
							{
								playerConfig.save(playerConfigFile);
							}
							catch(IOException e)
							{
								throw new RuntimeException(e.getMessage());
							}
							s.sendMessage("§aSuccessfully deleted home '" + a[0].toLowerCase() + "'.");
						}
						else
						{
							p.sendMessage("§cYou don't have a home named '" + a[0].toLowerCase() + "'.");
						}
					}
					else
					{
						s.sendMessage("§cSyntax: /delhome <name>");
					}
				}
				else
				{
					s.sendMessage("§cThis command is only for players.");
				}
				break;
		}
		return true;
	}
}

class TradeRequest
{
	final Player from;
	final Player to;

	TradeRequest(Player from, Player to)
	{
		this.from = from;
		this.to = to;
	}
}

class TeleportationRequest
{
	final Player from;
	final Player to;
	final boolean here;
	final long expires;

	TeleportationRequest(Player from, Player to, boolean here)
	{
		this.from = from;
		this.to = to;
		this.here = here;
		this.expires = SurvivalUtils.getTime() + 60;
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
