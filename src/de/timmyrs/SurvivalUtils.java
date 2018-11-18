package de.timmyrs;

import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
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

	@Override
	public void onEnable()
	{
		getConfig().addDefault("enableSleepCoordination", true);
		getConfig().options().copyDefaults(true);
		saveConfig();
		reloadConfig();
		getCommand("reloadsurvivalutils").setExecutor(this);
		getCommand("tpa").setExecutor(this);
		getCommand("tpahere").setExecutor(this);
		getCommand("tpaccept").setExecutor(this);
		getCommand("tpcancel").setExecutor(this);
		getServer().getPluginManager().registerEvents(this, this);
		getServer().getScheduler().scheduleSyncRepeatingTask(this, ()->
		{
			if(getConfig().getBoolean("enableSleepCoordination"))
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
						final String message = "§e" + sleepingPlayers.size() + "/" + entry.getKey().getPlayers().size() + " players are sleeping. Won't you join them?";
						for(Player p : entry.getKey().getPlayers())
						{
							p.sendMessage(message);
						}
					}
				}
			}
		}, 400, 400);
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
			case "reloadsurvivalutils":
				reloadConfig();
				saveConfig();
				s.sendMessage("§aReloaded the configuration.");
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
									p.sendMessage("§aYou can cancel it using /tpcancel.");
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
