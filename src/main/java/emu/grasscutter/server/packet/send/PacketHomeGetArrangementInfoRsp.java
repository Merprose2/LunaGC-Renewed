package emu.grasscutter.server.packet.send;

import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.Grasscutter;
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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * REL6.6-compatible Home arrangement response.
 *
 * <p>The generated Home arrangement proto writers in the current server use several stale field
 * numbers. This class deliberately bypasses those generated writers and serializes the exact 6.6
 * field layout with {@link CodedOutputStream}.
 *
 * <p>Confirmed from the supplied 6.6 Deobfuscated.proto:
 *
 * <ul>
 *   <li>HomeGetArrangementInfoRsp.scene_arrangement_info_list = 12
 *   <li>HomeSceneArrangementInfo.block_arrangement_info_list = 10
 *   <li>HomeSceneArrangementInfo.main_house = 5
 *   <li>HomeSceneArrangementInfo.born_pos = 1
 *   <li>HomeSceneArrangementInfo.djinn_pos = 11
 *   <li>HomeSceneArrangementInfo.scene_id = 12
 *   <li>HomeBlockArrangementInfo.deploy_furniure_list = 11
 *   <li>HomeBlockArrangementInfo.block_id = 14
 *   <li>HomeFurnitureData.spawn_rot = 2
 *   <li>HomeFurnitureData.spawn_pos = 12
 *   <li>HomeFurnitureData.furniture_id = 7
 * </ul>
 */
public class PacketHomeGetArrangementInfoRsp extends BasePacket {
    /*
     * REL6.6 has two unidentified bools at fields 7 and 14.
     *
     * The readable older layout had one is_set_born_pos bool and one
     * unidentified bool. Do not assert both of them.
     *
     */
    private static final boolean WRITE_SCENE_FLAG_7 = false;
    private static final boolean WRITE_SCENE_FLAG_14 = true;

    public PacketHomeGetArrangementInfoRsp(Player player, List<Integer> sceneIdList) {
        super(PacketOpcodes.HomeGetArrangementInfoRsp);

        var home = player.getCurHomeWorld().getHome();
        List<HomeSceneItem> scenes =
                sceneIdList.stream()
                        .distinct()
                        .map(home::getHomeSceneItem)
                        .toList();

        byte[] payload = encodeResponse(scenes);

        int blockCount =
                scenes.stream()
                        .map(HomeSceneItem::getBlockItems)
                        .filter(blocks -> blocks != null)
                        .mapToInt(blocks -> blocks.size())
                        .sum();

        int deployedFurnitureCount =
                scenes.stream()
                        .map(HomeSceneItem::getBlockItems)
                        .filter(blocks -> blocks != null)
                        .flatMap(blocks -> blocks.values().stream())
                        .map(HomeBlockItem::getDeployFurnitureList)
                        .filter(list -> list != null)
                        .mapToInt(List::size)
                        .sum();

        int persistentFurnitureCount =
                scenes.stream()
                        .map(HomeSceneItem::getBlockItems)
                        .filter(blocks -> blocks != null)
                        .flatMap(blocks -> blocks.values().stream())
                        .map(HomeBlockItem::getPersistentFurnitureList)
                        .filter(list -> list != null)
                        .mapToInt(List::size)
                        .sum();

        int unlockedBlockCount =
                scenes.stream()
                        .filter(scene -> !scene.isRoom())
                        .map(HomeSceneItem::getBlockItems)
                        .filter(blocks -> blocks != null)
                        .flatMap(blocks -> blocks.values().stream())
                        .mapToInt(block -> block.isUnlocked() ? 1 : 0)
                        .sum();

        int doorCount =
                scenes.stream()
                        .map(HomeSceneItem::getDoorList)
                        .filter(list -> list != null)
                        .mapToInt(List::size)
                        .sum();

        int stairCount =
                scenes.stream()
                        .map(HomeSceneItem::getStairList)
                        .filter(list -> list != null)
                        .mapToInt(List::size)
                        .sum();

        Grasscutter.getLogger()
                .debug(
                        "[HomeArrangementWire66] sending uid={}, opcode={}, sceneIds={}, "
                                + "sceneCount={}, blockCount={}, unlockedBlockCount={}, "
                                + "deployedFurnitureCount={}, persistentFurnitureCount={}, "
                                + "doorCount={}, stairCount={}, payloadLength={}",
                        player.getUid(),
                        PacketOpcodes.HomeGetArrangementInfoRsp,
                        sceneIdList,
                        scenes.size(),
                        blockCount,
                        unlockedBlockCount,
                        deployedFurnitureCount,
                        persistentFurnitureCount,
                        doorCount,
                        stairCount,
                        payload.length);

        home.save();
        this.setData(payload);
    }

    private static byte[] encodeResponse(List<HomeSceneItem> scenes) {
        return encode(
                output -> {
                    for (HomeSceneItem scene : scenes) {
                        writeMessage(output, 12, encodeScene(scene));
                    }

                    // retcode = 5; omitted because success is proto3 default 0.
                });
    }

    private static byte[] encodeScene(HomeSceneItem scene) {
        return encode(
                output -> {
                    // HomeSceneArrangementInfo.born_pos = 1
                    writeVector(output, 1, scene.getBornPos());

                    // HomeSceneArrangementInfo.door_list = 3
                    scene.reassignStructureListsIfNull();
                    for (HomeFurnitureItem door : scene.getDoorList()) {
                        writeFurniture(output, 3, door);
                    }

                    // HomeSceneArrangementInfo.main_house = 5
                    writeFurniture(output, 5, scene.getMainHouse());

                    // HomeSceneArrangementInfo.tmp_version = 6
                    writeUInt32(output, 6, scene.getTmpVersion());

                    if (WRITE_SCENE_FLAG_7) {
                        output.writeBool(7, true);
                    }

                    /*
                     * Field 9 is the only ordinary Vector candidate matching
                     * the 5.0 born_rot fingerprint in the 6.6 message.
                     */
                    writeVector(output, 9, scene.getBornRot());

                    // HomeSceneArrangementInfo.block_arrangement_info_list = 10
                    if (scene.getBlockItems() != null) {
                        for (HomeBlockItem block : scene.getBlockItems().values()) {
                            /*
                             * Indoor room blocks historically remain false for is_unlocked.
                             * Persistent structural furniture must not be treated as ordinary
                             * deployed furniture when deciding this flag.
                             */
                            writeMessage(output, 10, encodeBlock(block, scene.isRoom()));
                        }
                    }

                    // HomeSceneArrangementInfo.djinn_pos = 11
                    writeVector(output, 11, scene.getDjinnPos());

                    // HomeSceneArrangementInfo.scene_id = 12
                    writeUInt32(output, 12, scene.getSceneId());

                    // HomeSceneArrangementInfo.bgm_id = 13
                    writeUInt32(output, 13, scene.getHomeBgmId());

                    if (WRITE_SCENE_FLAG_14) {
                        output.writeBool(14, true);
                    }

                    // HomeSceneArrangementInfo.comfort_value = 15
                    writeUInt32(output, 15, scene.calComfort());

                    // HomeSceneArrangementInfo.stair_list = 8
                    for (HomeFurnitureItem stair : scene.getStairList()) {
                        writeFurniture(output, 8, stair);
                    }
                });
    }

    private static byte[] encodeBlock(HomeBlockItem block, boolean roomScene) {
        return encode(
                output -> {
                    // HomeBlockArrangementInfo.furniture_group_list = 1
                    // Not represented by the current HomeBlockItem model.

                    // HomeBlockArrangementInfo.furniture_custom_suite_list = 2
                    // Not represented by the current HomeBlockItem model.

                    // HomeBlockArrangementInfo.is_unlocked = 3
                    //
                    // Indoor blocks from the default mansion arrangement historically do not
                    // serialize this as true. The block-dependent walls, floors and ceilings
                    // come from persistent_furniture_list instead.
                    if (!roomScene && block.isUnlocked()) {
                        output.writeBool(3, true);
                    }

                    // HomeBlockArrangementInfo.deploy_animal_list = 4
                    if (block.getDeployAnimalList() != null) {
                        for (HomeAnimalItem animal : block.getDeployAnimalList()) {
                            writeMessage(output, 4, encodeAnimal(animal));
                        }
                    }

                    // HomeBlockArrangementInfo.dot_pattern_list = 5
                    // Not represented by the current HomeBlockItem model.

                    // HomeBlockArrangementInfo.deploy_npc_list = 6
                    if (block.getDeployNPCList() != null) {
                        for (HomeNPCItem npc : block.getDeployNPCList()) {
                            writeMessage(output, 6, encodeNpc(npc));
                        }
                    }

                    // HomeBlockArrangementInfo.furniture_suite_list = 9
                    if (block.getSuiteList() != null) {
                        for (HomeSuiteItem suite : block.getSuiteList()) {
                            writeMessage(output, 9, encodeSuite(suite));
                        }
                    }

                    // HomeBlockArrangementInfo.persistent_furniture_list = 10
                    if (block.getPersistentFurnitureList() != null) {
                        for (HomeFurnitureItem furniture : block.getPersistentFurnitureList()) {
                            writeFurniture(output, 10, furniture);
                        }
                    }

                    // HomeBlockArrangementInfo.deploy_furniure_list = 11
                    if (block.getDeployFurnitureList() != null) {
                        for (HomeFurnitureItem furniture : block.getDeployFurnitureList()) {
                            writeFurniture(output, 11, furniture);
                        }
                    }

                    // HomeBlockArrangementInfo.comfort_value = 12
                    writeUInt32(output, 12, block.calComfort());

                    // HomeBlockArrangementInfo.weekend_djinn_info_list = 13
                    // Not represented by the current HomeBlockItem model.

                    // HomeBlockArrangementInfo.block_id = 14
                    writeUInt32(output, 14, block.getBlockId());

                    // HomeBlockArrangementInfo.field_list = 15
                    // Farming fields are not implemented in HomeBlockItem.
                });
    }

    private static byte[] encodeFurniture(HomeFurnitureItem furniture) {
        return encode(
                output -> {
                    // HomeFurnitureData.spawn_rot = 2
                    writeVector(output, 2, furniture.getSpawnRot());

                    // HomeFurnitureData.parent_furniture_index = 4
                    writeInt32(output, 4, furniture.getParentFurnitureIndex());

                    // HomeFurnitureData.guid = 6
                    writeUInt32(output, 6, furniture.getGuid());

                    // HomeFurnitureData.furniture_id = 7
                    writeUInt32(output, 7, furniture.getFurnitureId());

                    // HomeFurnitureData.version = 8
                    writeUInt32(output, 8, furniture.getVersion());

                    // HomeFurnitureData.spawn_pos = 12
                    writeVector(output, 12, furniture.getSpawnPos());
                });
    }


    /*
     * REL6.6 field 542 contains per-block position/rotation anchors. The
     * current resources do not provide verified values, so the field remains
     * intentionally absent until real client data is recovered.
     */

    private static byte[] encodeNpc(HomeNPCItem npc) {
        return encode(
                output -> {
                    // HomeNpcData.avatar_id = 3
                    writeUInt32(output, 3, npc.getAvatarId());

                    // HomeNpcData.spawn_pos = 9
                    writeVector(output, 9, npc.getSpawnPos());

                    // HomeNpcData.spawn_rot = 11
                    writeVector(output, 11, npc.getSpawnRot());

                    // HomeNpcData.costume_id = 12
                    writeUInt32(output, 12, npc.getCostumeId());
                });
    }

    private static byte[] encodeAnimal(HomeAnimalItem animal) {
        return encode(
                output -> {
                    // HomeAnimalData.furniture_id = 2
                    writeUInt32(output, 2, animal.getFurnitureId());

                    // HomeAnimalData.spawn_pos = 13
                    writeVector(output, 13, animal.getSpawnPos());

                    // HomeAnimalData.spawn_rot = 14
                    writeVector(output, 14, animal.getSpawnRot());
                });
    }

    private static byte[] encodeSuite(HomeSuiteItem suite) {
        return encode(
                output -> {
                    // HomeFurnitureSuiteData.is_allow_summon = 6
                    if (suite.isAllowSummon()) {
                        output.writeBool(6, true);
                    }

                    // HomeFurnitureSuiteData.included_furniture_index_list = 8 (packed int32)
                    writePackedInt32(output, 8, suite.getIncludedFurnitureIndexList());

                    // HomeFurnitureSuiteData.spawn_pos = 10
                    writeVector(output, 10, suite.getPos());

                    // HomeFurnitureSuiteData.suite_id = 13
                    writeUInt32(output, 13, suite.getSuiteId());

                    // HomeFurnitureSuiteData.guid = 15
                    writeUInt32(output, 15, suite.getGuid());
                });
    }

    private static void writeFurniture(
            CodedOutputStream output, int fieldNumber, HomeFurnitureItem furniture)
            throws IOException {
        if (furniture != null) {
            writeMessage(output, fieldNumber, encodeFurniture(furniture));
        }
    }

    private static void writeVector(
            CodedOutputStream output, int fieldNumber, Position position) throws IOException {
        if (position != null) {
            writeMessage(output, fieldNumber, encodeVector(position));
        }
    }

    private static byte[] encodeVector(Position position) {
        return encode(
                output -> {
                    output.writeFloat(1, position.getX());
                    output.writeFloat(2, position.getY());
                    output.writeFloat(3, position.getZ());
                });
    }

    private static void writeMessage(
            CodedOutputStream output, int fieldNumber, byte[] message) throws IOException {
        output.writeByteArray(fieldNumber, message);
    }

    private static void writeUInt32(CodedOutputStream output, int fieldNumber, int value)
            throws IOException {
        if (value != 0) {
            output.writeUInt32(fieldNumber, value);
        }
    }

    private static void writeInt32(CodedOutputStream output, int fieldNumber, int value)
            throws IOException {
        if (value != 0) {
            output.writeInt32(fieldNumber, value);
        }
    }

    private static void writePackedInt32(
            CodedOutputStream output, int fieldNumber, List<Integer> values) throws IOException {
        if (values == null || values.isEmpty()) {
            return;
        }

        int dataSize = 0;
        for (int value : values) {
            dataSize += CodedOutputStream.computeInt32SizeNoTag(value);
        }

        output.writeTag(fieldNumber, 2);
        output.writeUInt32NoTag(dataSize);

        for (int value : values) {
            output.writeInt32NoTag(value);
        }
    }

    private static byte[] encode(Encoder encoder) {
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            CodedOutputStream output = CodedOutputStream.newInstance(stream);
            encoder.write(output);
            output.flush();
            return stream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not encode REL6.6 Home arrangement", exception);
        }
    }

    @FunctionalInterface
    private interface Encoder {
        void write(CodedOutputStream output) throws IOException;
    }
}
