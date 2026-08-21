package emu.grasscutter.command.commands;

import emu.grasscutter.GameConstants;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketSceneEntityAppearNotify;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.command.ConstellationsHandler;

import java.util.List;

import static emu.grasscutter.utils.lang.Language.translate;

@Command(label = "switchelement",usage="none|pyro|hydro|anemo|cryo|geo|electro|dendro",aliases = {"se"}, threading = true)
public class seCommand implements CommandHandler {

    private Element getElementFromString(String elementString) {
        return switch (elementString.toLowerCase()) {
            case "none", "white", "common", "elementless" -> Element.elementless;
            case "fire", "pyro" -> Element.pyro;
            case "water", "hydro" -> Element.hydro;
            case "wind", "anemo", "air" -> Element.anemo;
            case "ice", "cryo" -> Element.cryo;
            case "rock", "geo" -> Element.geo;
            case "electric", "electro" -> Element.electro;
            case "grass", "dendro", "plant" -> Element.dendro;
            default -> null;
        };
    }

    private String changeAvatarElement(Player sender, int avatarId, Element element) {
        Avatar avatar = sender.getAvatars().getAvatarById(avatarId);
        if (avatar == null) {
            return String.format("you do not own avatar %d", avatarId);
        }
        int depotId = element.getSkillRepoId(avatarId);
        AvatarSkillDepotData skillDepot = GameData.getAvatarSkillDepotDataMap().get(depotId);
        if (skillDepot == null) {
            return String.format("skill depot %d for %s is not loaded", depotId, element.name());
        }
        avatar.setSkillDepotData(skillDepot);
        avatar.setCurrentEnergy(1000);
        avatar.save();
        return null;
    }
    @Override
    public void execute(Player sender,Player targetPlayer, List<String> args) {
        String UserName=targetPlayer.getAccount().getUsername();
        if (args.size() < 1) {
            if (sender != null) {
                CommandHandler.sendMessage(targetPlayer, "/se [none|pyro|hydro|anemo|cryo|geo|electro|dendro] <constellation>");
            }
            else {
                Grasscutter.getLogger().info("/se [none|pyro|hydro|anemo|cryo|geo|electro|dendro] <constellation>");
            }
            return;
        }
        Element element = getElementFromString(args.get(0));
        if (element == null) {
            if (sender != null) {
                CommandHandler.sendMessage(targetPlayer, "Error : Invalid Element");
            }
            else {
                Grasscutter.getLogger().info("Error : Invalid Element");
            }
            return;
        }
        int constellation = 0;
        if (args.size() > 1) {
            try {
                constellation = Integer.parseInt(args.get(1));
                if (constellation>6){
                    constellation=6;
                } else if (constellation<0) {
                   constellation = 0;
                }
            }
            catch (Exception e){
                if (sender != null) {
                    CommandHandler.sendMessage(targetPlayer, "Error : Invalid Constellation");
                }
                else {
                    Grasscutter.getLogger().info("Error : Invalid Constellation");
                }
            }
        }
        var currentEntity = targetPlayer.getTeamManager().getCurrentAvatarEntity();
        if (currentEntity == null) {
            if (sender != null) {
                CommandHandler.sendMessage(targetPlayer, "Switch failed : no active character");
            }
            else {
                Grasscutter.getLogger().info("Switch failed : no active character");
            }
            return;
        }
        int activeAvatarId = currentEntity.getAvatar().getAvatarId();
        String failure;
        if (activeAvatarId == GameConstants.MAIN_CHARACTER_MALE || activeAvatarId == GameConstants.MAIN_CHARACTER_FEMALE) {
            failure = changeAvatarElement(targetPlayer, activeAvatarId, element);
            if (failure == null) {
                ConstellationsHandler.change(targetPlayer, element, constellation);
            }
        } else {
            failure = String.format("the active character is %d, switch to the Traveler first",
                    activeAvatarId);
        }
        if (failure == null) {
            int scene = targetPlayer.getSceneId();
            String message;
            try {
                Position targetPlayerPos = targetPlayer.getPosition();
                targetPlayer.getWorld().transferPlayerToScene(targetPlayer, 1, targetPlayerPos);
                targetPlayer.getWorld().transferPlayerToScene(targetPlayer, scene, targetPlayerPos);
                targetPlayer.getScene().broadcastPacket(new PacketSceneEntityAppearNotify(targetPlayer));
                message = String.format("Switched to %s", element.name());
            } catch (Exception e) {
                message = String.format("Failed to switch to %s", element.name());
            }
            if (sender != null) {
                CommandHandler.sendMessage(targetPlayer, message);
            }
            else {
                Grasscutter.getLogger().info(message);
            }
        } else {
            String reason = String.format("Switch failed : %s", failure);
            if (sender != null) {
                CommandHandler.sendMessage(targetPlayer, reason);
            }
            else {
                Grasscutter.getLogger().info(reason);
            }
        }
    }
}
