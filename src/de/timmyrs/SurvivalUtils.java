package de.timmyrs;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.MetadataValue;
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
	//static final ArrayList<TradeRequest> tradeRequests = new ArrayList<>();
	static final ArrayList<TeleportationRequest> teleportationRequests = new ArrayList<>();
	private final HashMap<Player, Long> playersLastActivity = new HashMap<>();
	private final ArrayList<Player> afkPlayers = new ArrayList<>();
	private final ArrayList<Player> sleepingPlayers = new ArrayList<>();
	private final HashMap<World, Integer> sleepMessageTasks = new HashMap<>();
	private final ArrayList<Player> colorSignInformed = new ArrayList<>();

	@Override
	public void onEnable()
	{
		playerDataDir = new File(getDataFolder(), "playerdata");
		getConfig().addDefault("homeLimits.default", 10);
		getConfig().addDefault("homeLimits.op", 100);
		getConfig().addDefault("antiAFKFarming.enabled", false);
		getConfig().addDefault("antiAFKFarming.seconds", 30);
		getConfig().addDefault("afkKick.enabled", false);
		getConfig().addDefault("afkKick.seconds", 900);
		getConfig().addDefault("afkKick.message", "You have been kicked for being AFK. Feel free to reconnect now that you're no longer AFK.");
		getConfig().addDefault("sleepCoordination.enabled", false);
		getConfig().addDefault("sleepCoordination.message", "&e%sleeping%/%total% players are sleeping. Won't you join them?");
		getConfig().addDefault("sleepCoordination.intervalTicks", 50);
		getConfig().addDefault("sleepCoordination.skipPercent", 100D);
		getConfig().addDefault("createWarpCommands", false);
		getConfig().addDefault("warpSigns.line", "[Warp]");
		getConfig().addDefault("warpSigns.color", "5");
		getConfig().addDefault("warps", new HashMap<String, Object>());
		getConfig().options().copyDefaults(true);
		saveConfig();
		reloadSurvivalUtilsConfig();
		getCommand("survivalutils").setExecutor(this);
		//getCommand("trade").setExecutor(this);
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
			if(getConfig().getBoolean("antiAFKFarming.enabled") || getConfig().getBoolean("afkKick.enabled"))
			{
				final Map<Player, Long> _playersLastActivity;
				synchronized(playersLastActivity)
				{
					_playersLastActivity = new HashMap<>(playersLastActivity);
				}
				synchronized(afkPlayers)
				{
					final long kickTime = getTime() - getConfig().getInt("afkKick.seconds");
					final long afkTime = getTime() - getConfig().getInt("antiAFKFarming.seconds");
					for(Map.Entry<Player, Long> entry : _playersLastActivity.entrySet())
					{
						if(getConfig().getBoolean("afkKick.enabled") && entry.getValue() < kickTime)
						{
							entry.getKey().kickPlayer(getConfig().getString("afkKick.message").replace("&", "§"));
						}
						else if(getConfig().getBoolean("antiAFKFarming.enabled") && entry.getValue() < afkTime)
						{
							if(!afkPlayers.contains(entry.getKey()))
							{
								afkPlayers.add(entry.getKey());
							}
						}
					}
				}
			}
		}, 100, 100);
	}

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

	private boolean isVanished(Player player)
	{
		for(MetadataValue meta : player.getMetadata("vanished"))
		{
			if(meta.asBoolean())
			{
				return true;
			}
		}
		return false;
	}

	private boolean canTeleport(Player p)
	{
		return p.isOnGround() || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR;
	}

	static long getTime()
	{
		return System.currentTimeMillis() / 1000L;
	}

	private File getConfigFile(Player p)
	{
		if(!playerDataDir.exists() && !playerDataDir.mkdir())
		{
			throw new RuntimeException("Failed to create " + playerDataDir.getPath());
		}
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
		synchronized(sleepingPlayers)
		{
			sleepingPlayers.clear();
			if(getConfig().getBoolean("sleepCoordination.enabled"))
			{
				for(Player p : getServer().getOnlinePlayers())
				{
					if(p.isSleeping())
					{
						sleepingPlayers.add(p);
					}
				}
				handleSleep(0);
			}
		}
		synchronized(playersLastActivity)
		{
			synchronized(afkPlayers)
			{
				if(getConfig().getBoolean("antiAFKFarming.enabled") || getConfig().getBoolean("afkKick.enabled"))
				{
					if(playersLastActivity.size() == 0)
					{
						final long time = getTime();
						for(Player p : getServer().getOnlinePlayers())
						{
							if(!p.hasPermission("survivalutils.allowafk"))
							{
								playersLastActivity.put(p, time);
							}
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
		if((getConfig().getBoolean("antiAFKFarming.enabled") || getConfig().getBoolean("afkKick.enabled")) && !e.getPlayer().hasPermission("survivalutils.allowafk"))
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
		if(getConfig().getBoolean("antiAFKFarming.enabled") || getConfig().getBoolean("afkKick.enabled"))
		{
			synchronized(playersLastActivity)
			{
				playersLastActivity.remove(e.getPlayer());
			}
			synchronized(afkPlayers)
			{
				afkPlayers.remove(e.getPlayer());
			}
			synchronized(colorSignInformed)
			{
				colorSignInformed.remove(e.getPlayer());
			}
		}
	}

	@EventHandler
	public void onPlayerMove(PlayerMoveEvent e)
	{
		if((getConfig().getBoolean("antiAFKFarming.enabled") || getConfig().getBoolean("afkKick.enabled")) && !e.getPlayer().hasPermission("survivalutils.allowafk"))
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

	@EventHandler(priority = EventPriority.HIGH)
	public void onEntityDamageByEntity(EntityDamageByEntityEvent e)
	{
		if(!e.isCancelled() && e.getDamager() instanceof Player && getConfig().getBoolean("antiAFKFarming.enabled"))
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

	@EventHandler(priority = EventPriority.HIGH)
	public void onPlayerInteract(PlayerInteractEvent e)
	{
		if(!e.isCancelled())
		{
			if(getConfig().getBoolean("antiAFKFarming.enabled"))
			{
				synchronized(afkPlayers)
				{
					if(afkPlayers.contains(e.getPlayer()))
					{
						e.setCancelled(true);
						return;
					}
				}
			}
			if(e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock().getState() instanceof Sign)
			{
				final Sign s = (Sign) e.getClickedBlock().getState();
				if(s.getLine(0).equals("§" + getConfig().getString("warpSigns.color") + getConfig().getString("warpSigns.line")))
				{
					final String w = s.getLine(1).toLowerCase();
					if((e.getPlayer().hasPermission("survivalutils.warps") || e.getPlayer().hasPermission("survivalutils.warps." + w)) && getConfig().contains("warps." + w))
					{
						e.setCancelled(true);
						e.getPlayer().teleport(stringToLocation(getConfig().getString("warps." + w)));
					}
				}
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onBlockPlace(BlockPlaceEvent e)
	{
		if(!e.isCancelled() && e.getPlayer().hasPermission("survivalutils.coloredsigns") && e.getBlockPlaced().getState() instanceof Sign)
		{
			synchronized(colorSignInformed)
			{
				if(colorSignInformed.contains(e.getPlayer()))
				{
					return;
				}
				colorSignInformed.add(e.getPlayer());
			}
			e.getPlayer().sendMessage("§aYou can use color codes on your sign with '&'!");
			e.getPlayer().sendMessage("For a list of color codes visit https://wiki.vg/Chat#Colors");
			e.getPlayer().sendMessage("If you want to use '&', use '&&'");
		}
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onSignChange(SignChangeEvent e)
	{
		if(!e.isCancelled())
		{
			if(e.getPlayer().hasPermission("survivalutils.coloredsigns"))
			{
				for(int i = 0; i < 4; i++)
				{
					e.setLine(i, e.getLine(i).replace("&", "§").replace("§§", "&"));
				}
			}
			if(e.getPlayer().hasPermission("survivalutils.warpsigns") && e.getLine(0).trim().equalsIgnoreCase(getConfig().getString("warpSigns.line")))
			{
				e.setLine(0, "§" + getConfig().getString("warpSigns.color") + getConfig().getString("warpSigns.line"));
				e.setLine(1, e.getLine(1).trim());
			}
		}
	}

	private void handleSleep(long subTicks)
	{
		if(getConfig().getBoolean("sleepCoordination.enabled"))
		{
			synchronized(sleepMessageTasks)
			{
				for(Integer i : sleepMessageTasks.values())
				{
					getServer().getScheduler().cancelTask(i);
				}
				sleepMessageTasks.clear();
			}
			final HashMap<World, ArrayList<Player>> worlds = new HashMap<>();
			synchronized(sleepingPlayers)
			{
				final ArrayList<Player> _sleepingPlayers = new ArrayList<>(sleepingPlayers);
				for(Player p : _sleepingPlayers)
				{
					if(p.isOnline() && p.isSleeping())
					{
						if(isVanished(p))
						{
							continue;
						}
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
			final long intervalTicks;
			if((getConfig().getLong("sleepCoordination.intervalTicks") - subTicks) < 1)
			{
				intervalTicks = 1;
			}
			else
			{
				intervalTicks = getConfig().getLong("sleepCoordination.intervalTicks") - subTicks;
			}
			for(Map.Entry<World, ArrayList<Player>> entry : worlds.entrySet())
			{
				final int worldSleepingPlayers = entry.getValue().size();
				if(worldSleepingPlayers > 0)
				{
					long neededForSleep = 0;
					for(Player p : entry.getKey().getPlayers())
					{
						if(isVanished(p))
						{
							continue;
						}
						neededForSleep++;
					}
					neededForSleep = Math.round((double) neededForSleep * getConfig().getDouble("sleepCoordination.skipPercent") * 0.01D);
					if(worldSleepingPlayers >= neededForSleep)
					{
						if(entry.getKey().getPlayers().size() < worldSleepingPlayers)
						{
							synchronized(sleepingPlayers)
							{
								for(Player p : entry.getKey().getPlayers())
								{
									sleepingPlayers.remove(p);
								}
							}
							entry.getKey().setTime(0);
							entry.getKey().setThundering(false);
						}
					}
					else
					{
						final String neededForSleepString = String.valueOf(neededForSleep);
						synchronized(sleepMessageTasks)
						{
							sleepMessageTasks.put(entry.getKey(), getServer().getScheduler().scheduleSyncDelayedTask(this, ()->
							{
								synchronized(sleepMessageTasks)
								{
									sleepMessageTasks.remove(entry.getKey());
								}
								final String message = getConfig().getString("sleepCoordination.message").replace("&", "§").replace("%sleeping%", String.valueOf(entry.getValue().size())).replace("%total%", neededForSleepString);
								for(Player p : entry.getKey().getPlayers())
								{
									p.sendMessage(message);
								}
							}, intervalTicks));
						}
					}
				}
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onPlayerBedEnter(final PlayerBedEnterEvent e)
	{
		if(!e.isCancelled() && getConfig().getBoolean("sleepCoordination.enabled"))
		{
			getServer().getScheduler().scheduleSyncDelayedTask(this, ()->
			{
				if(e.getPlayer().isSleeping())
				{
					final boolean changed;
					synchronized(sleepingPlayers)
					{
						changed = sleepingPlayers.add(e.getPlayer());
					}
					if(changed)
					{
						handleSleep(5);
					}
				}
			}, 5);
		}
	}

	@EventHandler
	public void onPlayerBedLeave(PlayerBedLeaveEvent e)
	{
		if(getConfig().getBoolean("sleepCoordination.enabled"))
		{
			final boolean changed;
			synchronized(sleepingPlayers)
			{
				changed = sleepingPlayers.remove(e.getPlayer());
			}
			if(changed)
			{
				handleSleep(0);
			}
		}
	}

	@EventHandler
	public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent e)
	{
		if(!e.isCancelled())
		{
			final Player p = e.getPlayer();
			if(p.hasPermission("survivalutils.warp") && getConfig().getBoolean("createWarpCommands"))
			{
				final String command = e.getMessage().substring(1).split(" ")[0].toLowerCase();
				if((p.hasPermission("survivalutils.warps") || p.hasPermission("survivalutils.warps." + command)) && getServer().getPluginCommand(command) == null && getConfig().contains("warps." + command))
				{
					e.setCancelled(true);
					if(canTeleport(p))
					{
						p.teleport(stringToLocation(getConfig().getString("warps." + command)));
					}
					else
					{
						p.sendMessage("§cYou may not teleport right now.");
					}
				}
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
					s.sendMessage("https://www.spigotmc.org/resources/survivalutils.62574/");
				}
				break;
			/*case "trade":
				if(s instanceof Player)
				{
					if(a.length == 1)
					{
						// TODO
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
				break;*/
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
						final Player p = (Player) s;
						if(canTeleport(p))
						{
							final String w = a[0].toLowerCase();
							if((p.hasPermission("survivalutils.warps") || p.hasPermission("survivalutils.warps." + w)) && getConfig().contains("warps." + w))
							{
								p.teleport(stringToLocation(getConfig().getString("warps." + w)));
							}
							else
							{
								p.sendMessage("§c'" + w + "' is not a warp point.");
							}
						}
						else
						{
							p.sendMessage("§cYou may not teleport right now.");
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
				int i = 0;
				final StringBuilder message = new StringBuilder();
				for(String name : warps.keySet())
				{
					if(s.hasPermission("survivalutils.warps") || s.hasPermission("survivalutils.warps." + name))
					{
						message.append(" ").append(name);
						i++;
					}
				}
				if(i == 0)
				{
					s.sendMessage("There are no warps.");
				}
				else
				{
					s.sendMessage((i == 1 ? "There is 1 warp:" : "There are " + i + " warps:") + message.toString());
				}
				break;
			}
			case "setwarp":
				if(s instanceof Player)
				{
					if(a.length == 1)
					{
						final String w = a[0].toLowerCase();
						if(getConfig().contains("warps." + w))
						{
							s.sendMessage("§cWarp point '" + w + "' already exists. Run /delwarp " + w + " first.");
						}
						else
						{
							getConfig().set("warps." + w, locationToString(((Player) s).getLocation()));
							s.sendMessage("§aSuccessfully created warp point '" + w + "'.");
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
					final String w = a[0].toLowerCase();
					if(warps.containsKey(w))
					{
						warps.remove(w);
						getConfig().set("warps", warps);
						saveConfig();
						reloadConfig();
						s.sendMessage("§aSuccessfully deleted warp point '" + w + "'.");
					}
					else
					{
						s.sendMessage("§c'" + w + "' is not a warp point.");
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
						if(canTeleport(p))
						{
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
						else
						{
							p.sendMessage("§cYou may not teleport right now.");
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

/*
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
*/

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
