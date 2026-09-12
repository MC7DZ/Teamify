package gg.MC7DZ.teamify.commands;

import gg.MC7DZ.teamify.Teamify;
import gg.MC7DZ.teamify.config.ConfigManager;
import gg.MC7DZ.teamify.gui.BankMenuGui;
import gg.MC7DZ.teamify.gui.ConfirmMenuGui;
import gg.MC7DZ.teamify.gui.EchestMenuGui;
import gg.MC7DZ.teamify.gui.InviteMenuGui;
import gg.MC7DZ.teamify.gui.MainMenuGui;
import gg.MC7DZ.teamify.gui.PlayerSettingsMenuGui;
import gg.MC7DZ.teamify.gui.RequestsMenuGui;
import gg.MC7DZ.teamify.gui.SettingsMenuGui;
import gg.MC7DZ.teamify.gui.TeamListMenuGui;
import gg.MC7DZ.teamify.listeners.PlayerListener;
import gg.MC7DZ.teamify.team.RelationType;
import gg.MC7DZ.teamify.team.Team;
import gg.MC7DZ.teamify.team.TeamManager;
import gg.MC7DZ.teamify.team.TeamRole;
import gg.MC7DZ.teamify.util.MessageUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TeamCommand implements CommandExecutor {
   private final Teamify plugin;

   private static OfflinePlayer resolveOfflinePlayer(String name) {
      Player online = Bukkit.getPlayerExact(name);
      if (online != null) {
         return online;
      }

      for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
         if (name.equalsIgnoreCase(op.getName())) {
            return op;
         }
      }

      return null;
   }

   public TeamCommand(Teamify plugin) {
      this.plugin = plugin;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      ConfigManager cm = this.plugin.getConfigManager();
      if (sender instanceof Player player) {
         TeamManager tm = this.plugin.getTeamManager();
         if (args.length == 0) {
            Team team = tm.getTeamOf(player.getUniqueId());
            if (team == null) {
               if (cm.isPlayerSettingsEnabled()) {
                  new PlayerSettingsMenuGui(player).open();
               } else {
                  player.sendMessage(cm.getMessage("no-team"));
               }

               return true;
            } else if (!cm.isGuiEnabled()) {
               player.sendMessage(cm.getPrefix().append(cm.color("<gray>Use /team info, /team members, etc.")));
               return true;
            } else {
               new MainMenuGui(player, team).open();
               return true;
            }
         } else {
            String sub = args[0].toLowerCase();
            if (sub.equals("mysettings")) {
               this.handleMySettings(player);
               return true;
            }

            if (!player.hasPermission("teams.command." + sub)) {
               player.sendMessage(cm.getMessage("no-permission"));
               return true;
            }

            switch (sub) {
               case "create":
                  this.handleCreate(player, args);
                  break;
               case "invite":
                  this.handleInvite(player, args);
                  break;
               case "kick":
                  this.handleKick(player, args);
                  break;
               case "leave":
                  this.handleLeave(player);
                  break;
               case "disband":
                  this.handleDisband(player);
                  break;
               case "home":
                  this.handleHome(player);
                  break;
               case "sethome":
                  this.handleSetHome(player);
                  break;
               case "chat":
                  this.handleChatToggle(player);
                  break;
               case "info":
                  this.handleInfo(player);
                  break;
               case "list":
                  if (!cm.isListCommandEnabled()) {
                     player.sendMessage(cm.getMessage("command-disabled"));
                     return true;
                  }

                  new TeamListMenuGui(player).open();
                  break;
               case "gui":
                  Team teamx = tm.getTeamOf(player.getUniqueId());
                  if (teamx == null) {
                     player.sendMessage(cm.getMessage("no-team"));
                  } else {
                     new MainMenuGui(player, teamx).open();
                  }
                  break;
               case "join":
                  this.handleJoin(player, args);
                  break;
               case "joinrequest":
                  this.handleJoinRequest(player, args);
                  break;
               case "requests":
                  Team team = tm.getTeamOf(player.getUniqueId());
                  if (team == null) {
                     player.sendMessage(cm.getMessage("no-team"));
                  } else {
                     new RequestsMenuGui(player, team).open();
                  }
                  break;
               case "promote":
                  this.handlePromote(player, args);
                  break;
               case "demote":
                  this.handleDemote(player, args);
                  break;
               case "transfer":
                  this.handleTransfer(player, args);
                  break;
               case "settings":
                  this.handleSettings(player);
                  break;
               case "pvp":
                  this.handlePvpToggle(player);
                  break;
               case "allychat":
                  this.handleAllyChatToggle(player);
                  break;
               case "allyinvite":
                  this.handleAllyInvite(player, args);
                  break;
               case "allyleave":
                  this.handleAllyLeave(player, args);
                  break;
               case "bank":
                  this.handleBank(player, args);
                  break;
               case "description":
                  this.handleDescription(player, args);
                  break;
               case "echest":
                  this.handleEchest(player);
                  break;
               case "reload":
                  this.handleReload(player);
                  break;
               default:
                  player.sendMessage(cm.getPrefix().append(cm.color("<red>Unknown subcommand.")));
            }

            return true;
         }
      } else {
         sender.sendMessage(cm.color("Only players can use this command."));
         return true;
      }
   }

   private void handleMySettings(Player player) {
      ConfigManager cm = this.plugin.getConfigManager();
      if (!cm.isPlayerSettingsEnabled()) {
         player.sendMessage(cm.getMessage("player-settings-disabled"));
      } else {
         new PlayerSettingsMenuGui(player).open();
      }
   }

   private void handleSettings(Player player) {
      ConfigManager cm = this.plugin.getConfigManager();
      TeamManager tm = this.plugin.getTeamManager();
      Team team = tm.getTeamOf(player.getUniqueId());
      if (team == null) {
         player.sendMessage(cm.getMessage("no-team"));
      } else if (!cm.isGuiEnabled()) {
         player.sendMessage(cm.getPrefix().append(cm.color("<gray>Use /team pvp to toggle team PVP.")));
      } else {
         new SettingsMenuGui(player, team).open();
      }
   }

   private void handlePvpToggle(Player player) {
      ConfigManager cm = this.plugin.getConfigManager();
      TeamManager tm = this.plugin.getTeamManager();
      Team team = tm.getTeamOf(player.getUniqueId());
      if (team == null) {
         player.sendMessage(cm.getMessage("no-team"));
      } else if (cm.isFriendlyFireWithinTeam()) {
         player.sendMessage(cm.getMessage("pvp-locked"));
      } else {
         TeamRole role = team.getRole(player.getUniqueId());
         if (!this.plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-toggle-pvp", false)) {
            player.sendMessage(cm.getMessage("not-enough-permission-role"));
         } else {
            team.setPvpEnabled(!team.isPvpEnabled());
            tm.saveTeam(team);
            player.sendMessage(cm.getMessage(team.isPvpEnabled() ? "pvp-enabled" : "pvp-disabled"));
         }
      }
   }

   private void handleEchest(Player player) {
      ConfigManager cm = this.plugin.getConfigManager();
      TeamManager tm = this.plugin.getTeamManager();
      if (!cm.isEchestEnabled()) {
         player.sendMessage(cm.getMessage("echest-disabled"));
      } else {
         Team team = tm.getTeamOf(player.getUniqueId());
         if (team == null) {
            player.sendMessage(cm.getMessage("no-team"));
         } else {
            TeamRole role = team.getRole(player.getUniqueId());
            if (role != null && this.plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-access-echest", false)) {
               UUID activeViewerId = EchestMenuGui.getActiveViewer(team.getId());
               if (activeViewerId != null && !activeViewerId.equals(player.getUniqueId())) {
                  Player activeViewer = Bukkit.getPlayer(activeViewerId);
                  String viewerName = activeViewer != null ? activeViewer.getName() : "Unknown";
                  player.sendMessage(cm.getMessage("echest-in-use", "player", viewerName));
               } else {
                  new EchestMenuGui(player, team).open();
               }
            } else {
               player.sendMessage(cm.getMessage("not-enough-permission-role"));
            }
         }
      }
   }

   private void handleDescription(Player player, String[] args) {
      ConfigManager cm = this.plugin.getConfigManager();
      TeamManager tm = this.plugin.getTeamManager();
      if (!cm.isTeamDescriptionEnabled()) {
         player.sendMessage(cm.getMessage("team-description-disabled"));
      } else {
         Team team = tm.getTeamOf(player.getUniqueId());
         if (team == null) {
            player.sendMessage(cm.getMessage("no-team"));
         } else {
            TeamRole role = team.getRole(player.getUniqueId());
            if (role == null || !this.plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-edit-description", false)) {
               player.sendMessage(cm.getMessage("not-enough-permission-role"));
            } else if (args.length >= 2 && args[1].equalsIgnoreCase("clear")) {
               team.setDescription(null);
               tm.saveTeam(team);
               player.sendMessage(cm.getMessage("team-description-cleared"));
            } else if (args.length >= 2) {
               String description = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
               this.setDescription(player, team, description);
            } else {
               this.plugin.getPlayerListener().awaitInput(player.getUniqueId(), PlayerListener.PendingInputType.TEAM_DESCRIPTION);
               player.sendMessage(cm.getMessage("team-description-prompt"));
            }
         }
      }
   }

   public void setDescription(Player player, Team team, String description) {
      ConfigManager cm = this.plugin.getConfigManager();
      if (!description.equalsIgnoreCase("cancel")) {
         if (description.length() > cm.getMaxDescriptionLength()) {
            player.sendMessage(cm.getMessage("team-description-too-long", "max", String.valueOf(cm.getMaxDescriptionLength())));
         } else {
            team.setDescription(description);
            this.plugin.getTeamManager().saveTeam(team);
            player.sendMessage(cm.getMessage("team-description-changed"));
         }
      }
   }

   private void handleReload(Player player) {
      ConfigManager cm = this.plugin.getConfigManager();
      if (!player.hasPermission("teams.admin")) {
         player.sendMessage(cm.getMessage("no-permission"));
      } else {
         cm.reload();
         this.plugin.getEconomyManager().setup();
         this.plugin.getPlayerManager().loadPlayers();
         player.sendMessage(cm.getMessage("config-reloaded"));
      }
   }

   private void handleCreate(Player player, String[] args) {
      ConfigManager cm = this.plugin.getConfigManager();
      TeamManager tm = this.plugin.getTeamManager();
      if (tm.isInTeam(player.getUniqueId())) {
         player.sendMessage(cm.getMessage("already-in-team"));
      } else if (args.length < 2) {
         player.sendMessage(cm.getPrefix().append(cm.color("<red>Usage: /team create <name> [tag]")));
      } else {
         long remaining = tm.getCreationCooldownRemaining(player.getUniqueId(), cm.getCreationCooldownSeconds());
         if (remaining > 0L) {
            player.sendMessage(cm.getMessage("cooldown-active", "time", remaining + "s"));
         } else {
            String name = args[1];
            if (name.length() < cm.getMinNameLength() || name.length() > cm.getMaxNameLength() || !Pattern.matches(cm.getNameRegex(), name)) {
               player.sendMessage(cm.getMessage("invalid-name"));
            } else if (cm.isBlockDuplicateNames() && tm.getTeamByName(name) != null) {
               player.sendMessage(cm.getMessage("invalid-name"));
            } else {
               String tag = args.length >= 3 ? args[2] : name.substring(0, Math.min(4, name.length())).toUpperCase();
               double creationCost = cm.getCreationCost();
               if (creationCost > 0.0) {
                  if (!this.plugin.getEconomyManager().isEnabled()) {
                     player.sendMessage(cm.getMessage("bank-no-economy"));
                     return;
                  }

                  if (!this.plugin.getEconomyManager().has(player, creationCost)) {
                     player.sendMessage(cm.getMessage("creation-insufficient-funds", "cost", this.plugin.getEconomyManager().format(creationCost)));
                     return;
                  }

                  if (!this.plugin.getEconomyManager().withdrawPlayer(player, creationCost)) {
                     player.sendMessage(cm.getMessage("bank-transaction-failed"));
                     return;
                  }
               }

               Team team = tm.createTeam(name, tag, player.getUniqueId());
               team.setBankBalance(cm.getStartingBalance());

               try {
                  team.setColor(ChatColor.valueOf(cm.getDefaultTeamColorName().toUpperCase()));
               } catch (IllegalArgumentException ignored) {
               }

               tm.markCreationCooldown(player.getUniqueId());
               tm.saveTeam(team);
               this.plugin.getVisibilityManager().refresh(player);
               player.sendMessage(cm.getMessage("team-created", "team", name));
               if (creationCost > 0.0) {
                  player.sendMessage(cm.getMessage("creation-cost-charged", "cost", this.plugin.getEconomyManager().format(creationCost)));
               }
            }
         }
      }
   }

   private void handleInvite(Player player, String[] args) {
      ConfigManager cm = this.plugin.getConfigManager();
      TeamManager tm = this.plugin.getTeamManager();
      Team team = tm.getTeamOf(player.getUniqueId());
      if (team == null) {
         player.sendMessage(cm.getMessage("no-team"));
      } else {
         TeamRole role = team.getRole(player.getUniqueId());
         if (!this.plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-invite", false)) {
            player.sendMessage(cm.getMessage("not-enough-permission-role"));
         } else if (args.length < 2) {
            player.sendMessage(cm.getPrefix().append(cm.color("<red>Usage: /team invite <player>")));
         } else {
            OfflinePlayer target = resolveOfflinePlayer(args[1]);
            if (target == null) {
               player.sendMessage(cm.getPrefix().append(cm.color("<red>That player has never played on this server.")));
            } else {
               this.invitePlayerToTeam(player, team, target);
            }
         }
      }
   }

   public void invitePlayerToTeam(Player player, Team team, OfflinePlayer target) {
      ConfigManager cm = this.plugin.getConfigManager();
      TeamManager tm = this.plugin.getTeamManager();
      TeamRole role = team.getRole(player.getUniqueId());
      if (!this.plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-invite", false)) {
         player.sendMessage(cm.getMessage("not-enough-permission-role"));
      } else {
         int maxMembers = cm.getMaxMembers();
         if (maxMembers > 0 && team.getSize() >= maxMembers) {
            player.sendMessage(cm.getMessage("team-full"));
         } else if (tm.isInTeam(target.getUniqueId())) {
            player.sendMessage(cm.getPrefix().append(cm.color("<red>That player is already in a team.")));
         } else {
            String targetName = target.getName() != null ? target.getName() : "Unknown";
            team.addInvite(target.getUniqueId());
            player.sendMessage(cm.getMessage("invite-sent", "player", targetName));
            Player onlineTarget = target.getPlayer();
            if (onlineTarget != null) {
               MessageUtil.sendClickableInvite(
                  onlineTarget,
                  cm.getMessage("invite-received", "team", team.getName()),
                  cm.color("<green><bold>[Click to Accept]"),
                  "/team join " + team.getName(),
                  cm.color("<gray>Click to join <aqua>" + team.getName())
               );
            }
         }
      }
   }

   private void handleJoin(Player player, String[] args) {
      ConfigManager cm = this.plugin.getConfigManager();
      TeamManager tm = this.plugin.getTeamManager();
      if (tm.isInTeam(player.getUniqueId())) {
         player.sendMessage(cm.getMessage("already-in-team"));
      } else if (args.length < 2) {
         player.sendMessage(cm.getPrefix().append(cm.color("<red>Usage: /team join <name>")));
      } else {
         Team team = tm.getTeamByName(args[1]);
         if (team != null && team.hasInvite(player.getUniqueId())) {
            if (cm.isGuiEnabled()) {
               new InviteMenuGui(player, team).open();
            } else {
               team.removeInvite(player.getUniqueId());
               tm.addMember(team, player.getUniqueId(), TeamRole.MEMBER);
               tm.saveTeam(team);
               this.plugin.getVisibilityManager().refreshTeamAndAllies(team);
               player.sendMessage(cm.getPrefix().append(cm.color("<green>You joined <aqua>" + team.getName() + "<green>!")));

               for (UUID memberId : team.getMembers().keySet()) {
                  if (!memberId.equals(player.getUniqueId())) {
                     Player member = Bukkit.getPlayer(memberId);
                     if (member != null) {
                        member.sendMessage(cm.getMessage("player-joined-broadcast", "player", player.getName()));
                     }
                  }
               }
            }
         } else {
            player.sendMessage(cm.getPrefix().append(cm.color("<red>No pending invite from that team.")));
         }
      }
   }

   private void handleJoinRequest(Player player, String[] args) {
      ConfigManager cm = this.plugin.getConfigManager();
      TeamManager tm = this.plugin.getTeamManager();
      if (!cm.isSendJoinRequestEnabled()) {
         player.sendMessage(cm.getMessage("command-disabled"));
      } else if (tm.isInTeam(player.getUniqueId())) {
         player.sendMessage(cm.getMessage("already-in-team"));
      } else if (args.length < 2) {
         player.sendMessage(cm.getPrefix().append(cm.color("<red>Usage: /team joinrequest <name>")));
      } else {
         Team team = tm.getTeamByName(args[1]);
         if (team == null) {
            player.sendMessage(cm.getMessage("team-not-found"));
         } else {
            int maxMembers = cm.getMaxMembers();
            if (maxMembers > 0 && team.getSize() >= maxMembers) {
               player.sendMessage(cm.getMessage("team-full"));
            } else if (team.hasJoinRequest(player.getUniqueId())) {
               player.sendMessage(cm.getMessage("join-request-already-sent", "team", team.getName()));
            } else {
               team.addJoinRequest(player.getUniqueId());
               tm.saveTeam(team);
               player.sendMessage(cm.getMessage("join-request-sent", "team", team.getName()));

               for (UUID memberId : team.getMembers().keySet()) {
                  TeamRole memberRole = team.getRole(memberId);
                  if (memberRole != null && this.plugin.getConfig().getBoolean("roles.permissions." + memberRole.name() + ".can-invite", false)) {
                     Player online = Bukkit.getPlayer(memberId);
                     if (online != null) {
                        MessageUtil.sendClickableInvite(
                           online,
                           cm.getMessage("join-request-received", "player", player.getName()),
                           cm.color("<green><bold>[View Requests]"),
                           "/team requests",
                           cm.color("<gray>Click to open the Requests menu")
                        );
                     }
                  }
               }
            }
         }
      }
   }

   private void handleKick(Player player, String[] args) {
      ConfigManager cm = this.plugin.getConfigManager();
      TeamManager tm = this.plugin.getTeamManager();
      Team team = tm.getTeamOf(player.getUniqueId());
      if (team == null) {
         player.sendMessage(cm.getMessage("no-team"));
      } else {
         TeamRole role = team.getRole(player.getUniqueId());
         if (!this.plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-kick", false)) {
            player.sendMessage(cm.getMessage("not-enough-permission-role"));
         } else if (args.length < 2) {
            player.sendMessage(cm.getPrefix().append(cm.color("<red>Usage: /team kick <player>")));
         } else {
            OfflinePlayer target = resolveOfflinePlayer(args[1]);
            if (target == null) {
               player.sendMessage(cm.getPrefix().append(cm.color("<red>That player has never played on this server.")));
            } else if (!team.isMember(target.getUniqueId())) {
               player.sendMessage(cm.getMessage("player-not-in-team"));
            } else if (target.getUniqueId().equals(player.getUniqueId())) {
               player.sendMessage(cm.getMessage("cant-kick-yourself"));
            } else if (target.getUniqueId().equals(team.getOwner())) {
               player.sendMessage(cm.getMessage("cant-kick-owner"));
            } else {
               boolean isOwner = player.getUniqueId().equals(team.getOwner());
               TeamRole targetRole = team.getRole(target.getUniqueId());
               if (!isOwner && targetRole.getWeight() >= role.getWeight()) {
                  player.sendMessage(cm.getMessage("cant-kick-higher-rank"));
               } else {
                  tm.removeMember(team, target.getUniqueId());
                  tm.saveTeam(team);
                  this.plugin.getVisibilityManager().refreshTeamAndAllies(team);
                  Player kickedOnline = target.getPlayer();
                  if (kickedOnline != null) {
                     this.plugin.getVisibilityManager().refresh(kickedOnline);
                     kickedOnline.sendMessage(cm.getMessage("kicked-from-team", "team", team.getName(), "player", player.getName()));
                  }

                  player.sendMessage(cm.getMessage("player-kicked", "player", args[1]));

                  for (UUID memberId : team.getMembers().keySet()) {
                     if (!memberId.equals(player.getUniqueId())) {
                        Player p = Bukkit.getPlayer(memberId);
                        if (p != null) {
                           p.sendMessage(cm.getMessage("player-kicked-broadcast", "player", args[1], "kicker", player.getName()));
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void handleLeave(Player player) {
      ConfigManager cm = this.plugin.getConfigManager();
      TeamManager tm = this.plugin.getTeamManager();
      Team team = tm.getTeamOf(player.getUniqueId());
      if (team == null) {
         player.sendMessage(cm.getMessage("no-team"));
      } else if (team.getOwner().equals(player.getUniqueId())) {
         player.sendMessage(cm.getPrefix().append(cm.color("<red>Transfer ownership or disband instead of leaving.")));
      } else {
         tm.removeMember(team, player.getUniqueId());
         tm.saveTeam(team);
         this.plugin.getVisibilityManager().refreshTeamAndAllies(team);
         this.plugin.getVisibilityManager().refresh(player);
         player.sendMessage(cm.getMessage("left-team", "team", team.getName()));

         for (UUID memberId : team.getMembers().keySet()) {
            Player p = Bukkit.getPlayer(memberId);
            if (p != null) {
               p.sendMessage(cm.getMessage("player-left-broadcast", "player", player.getName()));
            }
         }
      }
   }

   private void handleDisband(Player player) {
      ConfigManager cm = this.plugin.getConfigManager();
      TeamManager tm = this.plugin.getTeamManager();
      Team team = tm.getTeamOf(player.getUniqueId());
      if (team == null) {
         player.sendMessage(cm.getMessage("no-team"));
      } else if (!team.getOwner().equals(player.getUniqueId())) {
         player.sendMessage(cm.getMessage("not-enough-permission-role"));
      } else {
         Runnable doDisband = () -> {
            ArrayList<UUID> memberIds = new ArrayList<>(team.getMembers().keySet());
            String teamName = team.getName();
            tm.disbandTeam(team);

            for (UUID memberId : memberIds) {
               Player p = Bukkit.getPlayer(memberId);
               if (p != null) {
                  this.plugin.getVisibilityManager().refresh(p);
                  if (!memberId.equals(player.getUniqueId())) {
                     p.sendMessage(cm.getMessage("team-disbanded-member", "team", teamName));
                  }
               }
            }

            player.sendMessage(cm.getMessage("team-disbanded"));
         };
         if (cm.isDisbandConfirmationRequired() && cm.isGuiEnabled()) {
            new ConfirmMenuGui(player, doDisband, () -> player.sendMessage(cm.getPrefix().append(cm.color("<gray>Disband cancelled.")))).open();
         } else {
            doDisband.run();
         }
      }
   }

   private void handleHome(Player player) {
      ConfigManager cm = this.plugin.getConfigManager();
      TeamManager tm = this.plugin.getTeamManager();
      if (!cm.isHomeCommandEnabled()) {
         player.sendMessage(cm.getMessage("command-disabled"));
      } else {
         Team team = tm.getTeamOf(player.getUniqueId());
         if (team == null) {
            player.sendMessage(cm.getMessage("no-team"));
         } else {
            Location home = team.getHome(0);
            if (home == null) {
               player.sendMessage(cm.getMessage("no-home-set"));
            } else {
               int delay = cm.getHomeTeleportDelay();
               if (delay <= 0) {
                  player.teleport(home);
               } else {
                  player.sendMessage(cm.getPrefix().append(cm.color("<gray>Teleporting in " + delay + " seconds...")));
                  Location startLoc = player.getLocation();
                  boolean cancelOnMove = cm.isCancelOnMove();
                  Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                     if (cancelOnMove && player.getLocation().distanceSquared(startLoc) > 1.0) {
                        player.sendMessage(cm.getPrefix().append(cm.color("<red>Teleport cancelled - you moved.")));
                     } else {
                        player.teleport(home);
                     }
                  }, delay * 20L);
               }
            }
         }
      }
   }

   private void handleSetHome(Player player) {
      ConfigManager cm = this.plugin.getConfigManager();
      TeamManager tm = this.plugin.getTeamManager();
      if (!cm.isSetHomeCommandEnabled()) {
         player.sendMessage(cm.getMessage("command-disabled"));
      } else {
         Team team = tm.getTeamOf(player.getUniqueId());
         if (team == null) {
            player.sendMessage(cm.getMessage("no-team"));
         } else {
            TeamRole role = team.getRole(player.getUniqueId());
            if (!this.plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-set-home", false)) {
               player.sendMessage(cm.getMessage("not-enough-permission-role"));
            } else {
               team.setHome(0, player.getLocation());
               tm.saveTeam(team);
               player.sendMessage(cm.getPrefix().append(cm.color("<green>Team home set!")));
            }
         }
      }
   }

   private void handleChatToggle(Player player) {
      ConfigManager cm = this.plugin.getConfigManager();
      if (!cm.isTeamChatEnabled()) {
         player.sendMessage(cm.getPrefix().append(cm.color("<red>Team chat is disabled.")));
      } else {
         this.plugin.getPlayerListener().toggleTeamChat(player.getUniqueId());
         boolean nowOn = this.plugin.getPlayerListener().isTeamChatToggled(player.getUniqueId());
         player.sendMessage(cm.getPrefix().append(cm.color(nowOn ? "<green>Team chat enabled." : "<gray>Team chat disabled.")));
      }
   }

   private void handleInfo(Player player) {
      ConfigManager cm = this.plugin.getConfigManager();
      TeamManager tm = this.plugin.getTeamManager();
      Team team = tm.getTeamOf(player.getUniqueId());
      if (team == null) {
         player.sendMessage(cm.getMessage("no-team"));
      } else {
         player.sendMessage(
            cm.getPrefix()
               .append(
                  cm.color(
                     "<aqua>Team: <white>"
                        + team.getName()
                        + " <gray>| <aqua>Tag: <white>"
                        + team.getTag()
                        + " <gray>| <aqua>Level: <white>"
                        + team.getLevel()
                        + " <gray>| <aqua>Members: <white>"
                        + team.getSize()
                  )
               )
         );
         if (cm.isTeamDescriptionEnabled() && team.getDescription() != null && !team.getDescription().isEmpty()) {
            player.sendMessage(cm.color("<gray>" + team.getDescription()));
         }
      }
   }

   private void handlePromote(Player player, String[] args) {
      ConfigManager cm = this.plugin.getConfigManager();
      TeamManager tm = this.plugin.getTeamManager();
      Team team = tm.getTeamOf(player.getUniqueId());
      if (team == null) {
         player.sendMessage(cm.getMessage("no-team"));
      } else {
         TeamRole role = team.getRole(player.getUniqueId());
         if (!this.plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-promote", false)) {
            player.sendMessage(cm.getMessage("not-enough-permission-role"));
         } else if (args.length < 2) {
            player.sendMessage(cm.getPrefix().append(cm.color("<red>Usage: /team promote <player>")));
         } else {
            OfflinePlayer target = resolveOfflinePlayer(args[1]);
            if (target == null) {
               player.sendMessage(cm.getPrefix().append(cm.color("<red>That player has never played on this server.")));
            } else if (target.getUniqueId().equals(player.getUniqueId())) {
               player.sendMessage(cm.getMessage("cant-promote-yourself"));
            } else if (!team.isMember(target.getUniqueId())) {
               player.sendMessage(cm.getMessage("player-not-in-team"));
            } else {
               TeamRole current = team.getRole(target.getUniqueId());
               if (current == TeamRole.LEADER) {
                  player.sendMessage(cm.getMessage("already-highest-rank"));
               } else {
                  TeamRole next = current.next();
                  if (next.getWeight() >= role.getWeight()) {
                     player.sendMessage(cm.getMessage("cant-promote-own-rank"));
                  } else {
                     team.setRole(target.getUniqueId(), next);
                     tm.saveTeam(team);
                     player.sendMessage(cm.getMessage("player-promoted", "player", args[1], "role", next.name()));
                     Player targetOnline = target.getPlayer();
                     if (targetOnline != null) {
                        targetOnline.sendMessage(cm.getMessage("promoted-notify", "role", next.name(), "team", team.getName()));
                     }

                     for (UUID memberId : team.getMembers().keySet()) {
                        if (!memberId.equals(player.getUniqueId()) && !memberId.equals(target.getUniqueId())) {
                           Player p = Bukkit.getPlayer(memberId);
                           if (p != null) {
                              p.sendMessage(cm.getMessage("promoted-broadcast", "player", args[1], "role", next.name()));
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void handleDemote(Player player, String[] args) {
      ConfigManager cm = this.plugin.getConfigManager();
      TeamManager tm = this.plugin.getTeamManager();
      Team team = tm.getTeamOf(player.getUniqueId());
      if (team == null) {
         player.sendMessage(cm.getMessage("no-team"));
      } else {
         TeamRole role = team.getRole(player.getUniqueId());
         if (!this.plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-promote", false)) {
            player.sendMessage(cm.getMessage("not-enough-permission-role"));
         } else if (args.length < 2) {
            player.sendMessage(cm.getPrefix().append(cm.color("<red>Usage: /team demote <player>")));
         } else {
            OfflinePlayer target = resolveOfflinePlayer(args[1]);
            if (target == null) {
               player.sendMessage(cm.getPrefix().append(cm.color("<red>That player has never played on this server.")));
            } else if (target.getUniqueId().equals(player.getUniqueId())) {
               player.sendMessage(cm.getMessage("cant-demote-yourself"));
            } else if (!team.isMember(target.getUniqueId())) {
               player.sendMessage(cm.getMessage("player-not-in-team"));
            } else if (target.getUniqueId().equals(team.getOwner())) {
               player.sendMessage(cm.getMessage("cant-demote-owner"));
            } else {
               TeamRole current = team.getRole(target.getUniqueId());
               if (current.getWeight() >= role.getWeight()) {
                  player.sendMessage(cm.getMessage("cant-demote-higher-rank"));
               } else {
                  TeamRole newRole = current.previous();
                  team.setRole(target.getUniqueId(), newRole);
                  tm.saveTeam(team);
                  player.sendMessage(cm.getMessage("player-demoted", "player", args[1], "role", newRole.name()));
                  Player targetOnline = target.getPlayer();
                  if (targetOnline != null) {
                     targetOnline.sendMessage(cm.getMessage("demoted-notify", "role", newRole.name(), "team", team.getName()));
                  }

                  for (UUID memberId : team.getMembers().keySet()) {
                     if (!memberId.equals(player.getUniqueId()) && !memberId.equals(target.getUniqueId())) {
                        Player p = Bukkit.getPlayer(memberId);
                        if (p != null) {
                           p.sendMessage(cm.getMessage("demoted-broadcast", "player", args[1], "role", newRole.name()));
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void handleTransfer(Player player, String[] args) {
      ConfigManager cm = this.plugin.getConfigManager();
      TeamManager tm = this.plugin.getTeamManager();
      Team team = tm.getTeamOf(player.getUniqueId());
      if (team == null) {
         player.sendMessage(cm.getMessage("no-team"));
      } else if (!team.getOwner().equals(player.getUniqueId())) {
         player.sendMessage(cm.getMessage("not-enough-permission-role"));
      } else if (args.length < 2) {
         player.sendMessage(cm.getPrefix().append(cm.color("<red>Usage: /team transfer <player>")));
      } else {
         OfflinePlayer target = resolveOfflinePlayer(args[1]);
         if (target == null) {
            player.sendMessage(cm.getPrefix().append(cm.color("<red>That player has never played on this server.")));
         } else if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(cm.getPrefix().append(cm.color("<red>You already own this team.")));
         } else if (!team.isMember(target.getUniqueId())) {
            player.sendMessage(cm.getMessage("player-not-in-team"));
         } else {
            Runnable doTransfer = () -> {
               TeamRole targetOldRole = team.getRole(target.getUniqueId());
               team.setRole(target.getUniqueId(), TeamRole.LEADER);
               team.setRole(player.getUniqueId(), targetOldRole);
               team.setOwner(target.getUniqueId());
               tm.saveTeam(team);
               player.sendMessage(cm.getMessage("ownership-transferred", "player", args[1], "role", targetOldRole.name()));
               Player targetOnline = target.getPlayer();
               if (targetOnline != null) {
                  targetOnline.sendMessage(cm.getMessage("ownership-transferred-notify", "team", team.getName()));
               }

               for (UUID memberId : team.getMembers().keySet()) {
                  if (!memberId.equals(player.getUniqueId()) && !memberId.equals(target.getUniqueId())) {
                     Player p = Bukkit.getPlayer(memberId);
                     if (p != null) {
                        p.sendMessage(cm.getMessage("ownership-transferred-broadcast", "player", args[1]));
                     }
                  }
               }
            };
            if (cm.isTransferConfirmationRequired() && cm.isGuiEnabled()) {
               new ConfirmMenuGui(player, doTransfer, () -> player.sendMessage(cm.getPrefix().append(cm.color("<gray>Ownership transfer cancelled.")))).open();
            } else {
               doTransfer.run();
            }
         }
      }
   }

   private void handleBank(Player player, String[] args) {
      ConfigManager cm = this.plugin.getConfigManager();
      TeamManager tm = this.plugin.getTeamManager();
      if (!cm.isBankEnabled()) {
         player.sendMessage(cm.getMessage("bank-disabled"));
      } else {
         Team team = tm.getTeamOf(player.getUniqueId());
         if (team == null) {
            player.sendMessage(cm.getMessage("no-team"));
         } else {
            TeamRole role = team.getRole(player.getUniqueId());
            if (!this.plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-access-bank", false)) {
               player.sendMessage(cm.getMessage("not-enough-permission-role"));
            } else if (args.length < 2) {
               if (cm.isGuiEnabled()) {
                  new BankMenuGui(player, team).open();
               } else {
                  player.sendMessage(cm.getMessage("bank-balance", "amount", this.plugin.getEconomyManager().format(team.getBankBalance())));
               }
            } else {
               String action = args[1].toLowerCase();
               switch (action) {
                  case "balance":
                     player.sendMessage(cm.getMessage("bank-balance", "amount", this.plugin.getEconomyManager().format(team.getBankBalance())));
                     break;
                  case "deposit":
                     if (args.length < 3) {
                        player.sendMessage(cm.getPrefix().append(cm.color("<red>Usage: /team bank deposit <amount>")));
                        return;
                     }

                     this.bankDeposit(player, team, args[2]);
                     break;
                  case "withdraw":
                     if (args.length < 3) {
                        player.sendMessage(cm.getPrefix().append(cm.color("<red>Usage: /team bank withdraw <amount>")));
                        return;
                     }

                     this.bankWithdraw(player, team, args[2]);
                     break;
                  default:
                     player.sendMessage(cm.getPrefix().append(cm.color("<red>Usage: /team bank <deposit|withdraw|balance> [amount]")));
               }
            }
         }
      }
   }

   public void bankDeposit(Player player, Team team, String amountStr) {
      ConfigManager cm = this.plugin.getConfigManager();
      double amount = this.parsePositiveAmount(player, amountStr);
      if (!Double.isNaN(amount)) {
         if (!this.plugin.getEconomyManager().isEnabled()) {
            player.sendMessage(cm.getMessage("bank-no-economy"));
         } else if (!this.plugin.getEconomyManager().has(player, amount)) {
            player.sendMessage(cm.getMessage("bank-insufficient-player-funds"));
         } else {
            double maxBalance = cm.getMaxBalance();
            if (maxBalance > 0.0 && team.getBankBalance() >= maxBalance) {
               player.sendMessage(cm.getMessage("bank-max-balance"));
            } else if (!this.plugin.getEconomyManager().withdrawPlayer(player, amount)) {
               player.sendMessage(cm.getMessage("bank-transaction-failed"));
            } else {
               double deposited = team.deposit(amount, maxBalance);
               double leftover = amount - deposited;
               if (leftover > 0.0) {
                  this.plugin.getEconomyManager().depositPlayer(player, leftover);
               }

               this.plugin.getTeamManager().saveTeam(team);
               player.sendMessage(
                  cm.getMessage(
                     "bank-deposit-success",
                     "amount",
                     this.plugin.getEconomyManager().format(deposited),
                     "balance",
                     this.plugin.getEconomyManager().format(team.getBankBalance())
                  )
               );

               for (UUID memberId : team.getMembers().keySet()) {
                  if (!memberId.equals(player.getUniqueId())) {
                     Player p = Bukkit.getPlayer(memberId);
                     if (p != null) {
                        p.sendMessage(
                           cm.getMessage("bank-deposit-broadcast", "player", player.getName(), "amount", this.plugin.getEconomyManager().format(deposited))
                        );
                     }
                  }
               }
            }
         }
      }
   }

   public void bankWithdraw(Player player, Team team, String amountStr) {
      ConfigManager cm = this.plugin.getConfigManager();
      if (!player.hasPermission("teams.bank.withdraw")) {
         player.sendMessage(cm.getMessage("no-permission"));
      } else {
         TeamRole role = team.getRole(player.getUniqueId());
         if (!this.plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-withdraw-bank", false)) {
            player.sendMessage(cm.getMessage("bank-cant-withdraw-role"));
         } else {
            double amount = this.parsePositiveAmount(player, amountStr);
            if (!Double.isNaN(amount)) {
               if (!this.plugin.getEconomyManager().isEnabled()) {
                  player.sendMessage(cm.getMessage("bank-no-economy"));
               } else if (team.getBankBalance() < amount) {
                  player.sendMessage(cm.getMessage("bank-insufficient-team-funds"));
               } else if (!team.withdraw(amount)) {
                  player.sendMessage(cm.getMessage("bank-insufficient-team-funds"));
               } else if (!this.plugin.getEconomyManager().depositPlayer(player, amount)) {
                  team.deposit(amount);
                  player.sendMessage(cm.getMessage("bank-transaction-failed"));
               } else {
                  this.plugin.getTeamManager().saveTeam(team);
                  player.sendMessage(
                     cm.getMessage(
                        "bank-withdraw-success",
                        "amount",
                        this.plugin.getEconomyManager().format(amount),
                        "balance",
                        this.plugin.getEconomyManager().format(team.getBankBalance())
                     )
                  );

                  for (UUID memberId : team.getMembers().keySet()) {
                     if (!memberId.equals(player.getUniqueId())) {
                        Player p = Bukkit.getPlayer(memberId);
                        if (p != null) {
                           p.sendMessage(
                              cm.getMessage("bank-withdraw-broadcast", "player", player.getName(), "amount", this.plugin.getEconomyManager().format(amount))
                           );
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private double parsePositiveAmount(Player player, String raw) {
      ConfigManager cm = this.plugin.getConfigManager();

      double amount;
      try {
         amount = Double.parseDouble(raw);
      } catch (NumberFormatException ex) {
         player.sendMessage(cm.getMessage("bank-invalid-amount"));
         return Double.NaN;
      }

      if (!(amount <= 0.0) && Double.isFinite(amount)) {
         return amount;
      }

      player.sendMessage(cm.getMessage("bank-invalid-amount"));
      return Double.NaN;
   }

   private void handleAllyChatToggle(Player player) {
      ConfigManager cm = this.plugin.getConfigManager();
      TeamManager tm = this.plugin.getTeamManager();
      if (!cm.isAlliesEnabled()) {
         player.sendMessage(cm.getMessage("allies-disabled"));
      } else if (!cm.isAllyChatEnabled()) {
         player.sendMessage(cm.getMessage("ally-chat-disabled"));
      } else {
         Team team = tm.getTeamOf(player.getUniqueId());
         if (team == null) {
            player.sendMessage(cm.getMessage("no-team"));
         } else {
            this.plugin.getPlayerListener().toggleAllyChat(player.getUniqueId());
            boolean nowOn = this.plugin.getPlayerListener().isAllyChatToggled(player.getUniqueId());
            player.sendMessage(cm.getMessage(nowOn ? "ally-chat-enabled" : "ally-chat-disabled-toggle"));
         }
      }
   }

   private void handleAllyInvite(Player player, String[] args) {
      ConfigManager cm = this.plugin.getConfigManager();
      TeamManager tm = this.plugin.getTeamManager();
      if (!cm.isAlliesEnabled()) {
         player.sendMessage(cm.getMessage("allies-disabled"));
      } else {
         Team team = tm.getTeamOf(player.getUniqueId());
         if (team == null) {
            player.sendMessage(cm.getMessage("no-team"));
         } else {
            TeamRole role = team.getRole(player.getUniqueId());
            if (!this.plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-manage-relations", false)) {
               player.sendMessage(cm.getMessage("not-enough-permission-role"));
            } else if (args.length < 2) {
               player.sendMessage(cm.getPrefix().append(cm.color("<red>Usage: /team allyinvite <team>")));
            } else {
               Team target = tm.getTeamByName(args[1]);
               if (target == null) {
                  player.sendMessage(cm.getMessage("ally-invalid-team"));
               } else if (target.getId().equals(team.getId())) {
                  player.sendMessage(cm.getMessage("ally-cant-self"));
               } else if (tm.areAllied(team, target)) {
                  player.sendMessage(cm.getMessage("already-allied", "team", target.getName()));
               } else if (team.getRelation(target.getId()) == RelationType.ENEMY) {
                  player.sendMessage(cm.getMessage("already-enemies"));
               } else {
                  int maxAllies = cm.getMaxAllies();
                  if (maxAllies > 0 && team.getAllyCount() >= maxAllies) {
                     player.sendMessage(cm.getMessage("max-allies-reached"));
                  } else {
                     TeamManager.AllyInviteResult result = tm.requestAlly(team, target, cm.isMutualAllianceRequired());
                     if (result == TeamManager.AllyInviteResult.ALLY_ADDED) {
                        this.plugin.getVisibilityManager().refreshTeamAndAllies(team);
                        this.plugin.getVisibilityManager().refreshTeamAndAllies(target);

                        for (UUID memberId : team.getMembers().keySet()) {
                           Player p = Bukkit.getPlayer(memberId);
                           if (p != null) {
                              p.sendMessage(cm.getMessage("ally-added", "team", target.getName()));
                           }
                        }

                        for (UUID memberId : target.getMembers().keySet()) {
                           Player p = Bukkit.getPlayer(memberId);
                           if (p != null) {
                              p.sendMessage(cm.getMessage("ally-added", "team", team.getName()));
                           }
                        }
                     } else {
                        player.sendMessage(cm.getMessage("ally-invite-sent", "team", target.getName()));

                        for (UUID memberId : target.getMembers().keySet()) {
                           Player p = Bukkit.getPlayer(memberId);
                           if (p != null) {
                              MessageUtil.sendClickableInvite(
                                 p,
                                 cm.getMessage("ally-invite-received", "team", team.getName()),
                                 cm.color("<green><bold>[Click to Accept]"),
                                 "/team allyinvite " + team.getName(),
                                 cm.color("<gray>Click to accept the alliance with <aqua>" + team.getName())
                              );
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void handleAllyLeave(Player player, String[] args) {
      ConfigManager cm = this.plugin.getConfigManager();
      TeamManager tm = this.plugin.getTeamManager();
      if (!cm.isAlliesEnabled()) {
         player.sendMessage(cm.getMessage("allies-disabled"));
      } else {
         Team team = tm.getTeamOf(player.getUniqueId());
         if (team == null) {
            player.sendMessage(cm.getMessage("no-team"));
         } else {
            TeamRole role = team.getRole(player.getUniqueId());
            if (!this.plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-manage-relations", false)) {
               player.sendMessage(cm.getMessage("not-enough-permission-role"));
            } else if (args.length < 2) {
               player.sendMessage(cm.getPrefix().append(cm.color("<red>Usage: /team allyleave <team>")));
            } else {
               Team target = tm.getTeamByName(args[1]);
               if (target == null) {
                  player.sendMessage(cm.getMessage("ally-invalid-team"));
               } else if (!tm.areAllied(team, target)) {
                  player.sendMessage(cm.getMessage("not-allied", "team", target.getName()));
               } else {
                  tm.removeAlly(team, target);
                  this.plugin.getVisibilityManager().refreshTeamAndAllies(team);
                  this.plugin.getVisibilityManager().refreshTeamAndAllies(target);

                  for (UUID memberId : team.getMembers().keySet()) {
                     Player p = Bukkit.getPlayer(memberId);
                     if (p != null) {
                        p.sendMessage(cm.getMessage("ally-removed", "team", target.getName()));
                     }
                  }

                  for (UUID memberId : target.getMembers().keySet()) {
                     Player p = Bukkit.getPlayer(memberId);
                     if (p != null) {
                        p.sendMessage(cm.getMessage("ally-removed", "team", team.getName()));
                     }
                  }
               }
            }
         }
      }
   }
}
