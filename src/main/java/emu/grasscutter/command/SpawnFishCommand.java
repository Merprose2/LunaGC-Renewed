package emu.grasscutter.command.commands;

import emu.grasscutter.command.*;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Position;

import java.util.List;

@Command(
    label = "spawnfish",
    usage = "spawnfish [fishId] [amount]",
    permission = "player.spawnfish"
)
public final class SpawnFishCommand implements CommandHandler {
    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        if (targetPlayer == null) return;

        int fishId = 1; // Default: Medaka (Fish ID 1)
        int count = 1;

        if (!args.isEmpty()) {
            try {
                fishId = Integer.parseInt(args.get(0));
            } catch (Exception ignored) {}
        }
        if (args.size() > 1) {
            try {
                count = Math.min(10, Integer.parseInt(args.get(1)));
            } catch (Exception ignored) {}
        }

        var fishData = GameData.getFishDataMap().get(fishId);
        if (fishData == null) {
            CommandHandler.sendMessage(sender, "Fish ID " + fishId + " not found in FishExcelConfigData.json!");
            return;
        }

        var monsterData = GameData.getMonsterDataMap().get(fishData.getMonsterId());
        if (monsterData == null) {
            CommandHandler.sendMessage(sender, "Monster ID " + fishData.getMonsterId() + " not found!");
            return;
        }

        for (int i = 0; i < count; i++) {
            float angle = (float) (Math.random() * 2 * Math.PI);
            float dist = 2.0f + (float) (Math.random() * 2.0f);
            Position pos = new Position(
                targetPlayer.getPosition().getX() + (float)(dist * Math.cos(angle)),
                targetPlayer.getPosition().getY(),
                targetPlayer.getPosition().getZ() + (float)(dist * Math.sin(angle))
            );

            EntityMonster fish = new EntityMonster(
                targetPlayer.getScene(),
                monsterData,
                pos,
                new Position(0, (float)(Math.random() * 360), 0),
                1
            );

            fish.setFishId(fishId);
            fish.setFishPoolEntityId(0);
            fish.setPoseId(fishData.getInitPose());

            targetPlayer.getScene().addEntity(fish);
        }

        CommandHandler.sendMessage(sender, "Spawned " + count + "x fish (ID " + fishId + ")");
    }
}