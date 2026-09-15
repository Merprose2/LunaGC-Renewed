package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.home.HomeAnimalItem;
import emu.grasscutter.game.home.HomeBlockItem;
import emu.grasscutter.game.home.HomeFurnitureItem;
import emu.grasscutter.game.home.HomeNPCItem;
import emu.grasscutter.game.home.HomeSceneItem;
import emu.grasscutter.game.home.suite.HomeSuiteItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.HomeAnimalDataOuterClass.HomeAnimalData;
import emu.grasscutter.net.proto.HomeBlockArrangementInfoOuterClass.HomeBlockArrangementInfo;
import emu.grasscutter.net.proto.HomeFurnitureDataOuterClass.HomeFurnitureData;
import emu.grasscutter.net.proto.HomeFurnitureSuiteDataOuterClass.HomeFurnitureSuiteData;
import emu.grasscutter.net.proto.HomeGetArrangementInfoRspOuterClass.HomeGetArrangementInfoRsp;
import emu.grasscutter.net.proto.HomeNpcDataOuterClass.HomeNpcData;
import emu.grasscutter.net.proto.HomeSceneArrangementInfoOuterClass.HomeSceneArrangementInfo;
import emu.grasscutter.net.proto.VectorOuterClass.Vector;
import java.util.List;
import java.util.function.Consumer;

/**
 * Home (serenitea pot) arrangement response.
 *
 * <p>Everything is assembled with the generated proto accessors. The class used to hand serialize a
 * fixed REL6.6 layout whose numbers no longer match the schema, which made the client read blocks,
 * furniture and positions from the wrong fields.
 */
public class PacketHomeGetArrangementInfoRsp extends BasePacket {

    public PacketHomeGetArrangementInfoRsp(Player player, List<Integer> sceneIdList) {
        super(PacketOpcodes.HomeGetArrangementInfoRsp);

        var home = player.getCurHomeWorld().getHome();
        var proto = HomeGetArrangementInfoRsp.newBuilder();

        sceneIdList.stream()
                .distinct()
                .map(home::getHomeSceneItem)
                .forEach(scene -> proto.addSceneArrangementInfoList(toProto(scene)));

        home.save();
        this.setData(proto.build());
    }

    private static HomeSceneArrangementInfo toProto(HomeSceneItem scene) {
        scene.reassignStructureListsIfNull();

        var proto =
                HomeSceneArrangementInfo.newBuilder()
                        .setSceneId(scene.getSceneId())
                        .setTmpVersion(scene.getTmpVersion())
                        .setBgmId(scene.getHomeBgmId())
                        .setComfortValue(scene.calComfort());

        if (scene.getMainHouse() != null) {
            proto.setMainHouse(toFurniture(scene.getMainHouse()));
        }

        for (HomeFurnitureItem door : scene.getDoorList()) {
            proto.addDoorList(toFurniture(door));
        }

        for (HomeFurnitureItem stair : scene.getStairList()) {
            proto.addStairList(toFurniture(stair));
        }

        if (scene.getBlockItems() != null) {
            for (HomeBlockItem block : scene.getBlockItems().values()) {
                proto.addBlockArrangementInfoList(toBlock(block, scene.isRoom()));
            }
        }

        setVector(proto::setBornPos, scene.getBornPos());
        setVector(proto::setDjinnPos, scene.getDjinnPos());

        /*
         * born_rot (and the unidentified bools the old encoder guessed at) are not part of this
         * version of the message: HomeSceneArrangementInfo carries the named born_pos/djinn_pos
         * vectors plus obfuscated ones, and an obfuscated number is not something to guess at.
         */
        return proto.build();
    }

    private static HomeBlockArrangementInfo toBlock(HomeBlockItem block, boolean roomScene) {
        var proto =
                HomeBlockArrangementInfo.newBuilder()
                        .setBlockId(block.getBlockId())
                        .setComfortValue(block.calComfort())
                        /*
                         * Indoor blocks of the default mansion arrangement stay locked: their walls,
                         * floors and ceilings come from the persistent furniture list instead.
                         */
                        .setIsUnlocked(!roomScene && block.isUnlocked());

        if (block.getDeployFurnitureList() != null) {
            for (HomeFurnitureItem furniture : block.getDeployFurnitureList()) {
                proto.addDeployFurniureList(toFurniture(furniture));
            }
        }

        if (block.getPersistentFurnitureList() != null) {
            for (HomeFurnitureItem furniture : block.getPersistentFurnitureList()) {
                proto.addPersistentFurnitureList(toFurniture(furniture));
            }
        }

        if (block.getDeployAnimalList() != null) {
            for (HomeAnimalItem animal : block.getDeployAnimalList()) {
                proto.addDeployAnimalList(toAnimal(animal));
            }
        }

        if (block.getDeployNPCList() != null) {
            for (HomeNPCItem npc : block.getDeployNPCList()) {
                proto.addDeployNpcList(toNpc(npc));
            }
        }

        if (block.getSuiteList() != null) {
            for (HomeSuiteItem suite : block.getSuiteList()) {
                proto.addFurnitureSuiteList(toSuite(suite));
            }
        }

        return proto.build();
    }

    private static HomeFurnitureData toFurniture(HomeFurnitureItem furniture) {
        var proto =
                HomeFurnitureData.newBuilder()
                        .setFurnitureId(furniture.getFurnitureId())
                        .setGuid(furniture.getGuid())
                        .setVersion(furniture.getVersion())
                        .setParentFurnitureIndex(furniture.getParentFurnitureIndex());

        setVector(proto::setSpawnPos, furniture.getSpawnPos());
        setVector(proto::setSpawnRot, furniture.getSpawnRot());

        return proto.build();
    }

    private static HomeNpcData toNpc(HomeNPCItem npc) {
        var proto =
                HomeNpcData.newBuilder()
                        .setAvatarId(npc.getAvatarId())
                        .setCostumeId(npc.getCostumeId());

        setVector(proto::setSpawnPos, npc.getSpawnPos());
        setVector(proto::setSpawnRot, npc.getSpawnRot());

        return proto.build();
    }

    private static HomeAnimalData toAnimal(HomeAnimalItem animal) {
        var proto = HomeAnimalData.newBuilder().setFurnitureId(animal.getFurnitureId());

        setVector(proto::setSpawnPos, animal.getSpawnPos());
        setVector(proto::setSpawnRot, animal.getSpawnRot());

        return proto.build();
    }

    private static HomeFurnitureSuiteData toSuite(HomeSuiteItem suite) {
        var proto =
                HomeFurnitureSuiteData.newBuilder()
                        .setSuiteId(suite.getSuiteId())
                        .setGuid(suite.getGuid())
                        .setIsAllowSummon(suite.isAllowSummon());

        if (suite.getIncludedFurnitureIndexList() != null) {
            proto.addAllIncludedFurnitureIndexList(suite.getIncludedFurnitureIndexList());
        }

        setVector(proto::setSpawnPos, suite.getPos());

        return proto.build();
    }

    private static void setVector(Consumer<Vector> setter, Position position) {
        if (position != null) {
            setter.accept(position.toProto());
        }
    }
}