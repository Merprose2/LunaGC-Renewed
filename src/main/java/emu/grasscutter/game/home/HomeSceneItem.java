package emu.grasscutter.game.home;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.HomeworldDefaultSaveData;
import emu.grasscutter.game.entity.EntityHomeAnimal;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.proto.HomeSceneArrangementInfoOuterClass.HomeSceneArrangementInfo;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Entity
@Data
@Builder(builderMethodName = "of")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class HomeSceneItem {
    @Id int sceneId;
    Map<Integer, HomeBlockItem> blockItems;
    Position bornPos;
    Position bornRot;
    Position djinnPos;
    int homeBgmId;
    HomeFurnitureItem mainHouse;
    List<HomeFurnitureItem> doorList;
    List<HomeFurnitureItem> stairList;
    int tmpVersion;

    public static HomeSceneItem parseFrom(HomeworldDefaultSaveData defaultItem, int sceneId) {
        if (defaultItem == null) {
            Grasscutter.getLogger()
                    .error(
                            "[HomeDefaultSave] Missing default arrangement for scene {}. "
                                    + "Creating an empty scene item to avoid bricking the player account.",
                            sceneId);

            return HomeSceneItem.of()
                    .sceneId(sceneId)
                    .blockItems(new java.util.HashMap<>())
                    .bornPos(new Position())
                    .bornRot(new Position())
                    .djinnPos(new Position())
                    .doorList(List.of())
                    .stairList(List.of())
                    .build();
        }

        var homeBlocks =
                defaultItem.getHomeBlockLists() == null
                        ? List.<HomeworldDefaultSaveData.HomeBlock>of()
                        : defaultItem.getHomeBlockLists();

        if (homeBlocks.isEmpty()) {
            Grasscutter.getLogger()
                    .warn(
                            "[HomeDefaultSave] Scene {} loaded with no home blocks. "
                                    + "Check the HomeworldDefaultSave resource field mappings.",
                            sceneId);
        }

        return HomeSceneItem.of()
                .sceneId(sceneId)
                .blockItems(
                        homeBlocks.stream()
                                .map(HomeBlockItem::parseFrom)
                                .collect(
                                        Collectors.toMap(
                                                HomeBlockItem::getBlockId,
                                                block -> block,
                                                (first, ignored) -> first)))
                .bornPos(defaultItem.getBornPos() == null ? new Position() : defaultItem.getBornPos())
                .bornRot(defaultItem.getBornRot() == null ? new Position() : defaultItem.getBornRot())
                .djinnPos(defaultItem.getDjinPos() == null ? new Position() : defaultItem.getDjinPos())
                .mainHouse(
                        defaultItem.getMainhouse() == null
                                ? null
                                : HomeFurnitureItem.parseFrom(defaultItem.getMainhouse()))
                .doorList(parseFurnitureList(defaultItem.getDoorLists()))
                .stairList(parseFurnitureList(defaultItem.getStairLists()))
                .tmpVersion(defaultItem.getTmpVersion())
                .build();
    }

    private static List<HomeFurnitureItem> parseFurnitureList(
            List<HomeworldDefaultSaveData.HomeFurniture> furnitureList) {
        if (furnitureList == null || furnitureList.isEmpty()) {
            return List.of();
        }

        return furnitureList.stream().map(HomeFurnitureItem::parseFrom).toList();
    }

    public void reassignStructureListsIfNull() {
        if (this.doorList == null) {
            this.doorList = List.of();
        }

        if (this.stairList == null) {
            this.stairList = List.of();
        }
    }

    public void update(HomeSceneArrangementInfo arrangementInfo, Player owner) {
        for (var blockItem : arrangementInfo.getBlockArrangementInfoListList()) {
            var block = this.blockItems.get(blockItem.getBlockId());
            if (block == null) {
                Grasscutter.getLogger().warn("Could not find Home block {}", blockItem.getBlockId());
                continue;
            }
            block.update(blockItem, owner);
            this.blockItems.put(blockItem.getBlockId(), block);
        }

        this.bornPos = new Position(arrangementInfo.getBornPos());
        this.bornRot = new Position(arrangementInfo.getBornRot());
        this.djinnPos = new Position(arrangementInfo.getDjinnPos());
        this.homeBgmId = arrangementInfo.getBgmId();

        if (!this.isRoom() && arrangementInfo.hasMainHouse()) {
            this.mainHouse = HomeFurnitureItem.parseFrom(arrangementInfo.getMainHouse());
        }

        this.tmpVersion = arrangementInfo.getTmpVersion();
    }

    public int getRoomSceneId() {
        if (this.isRoom()) {
            return 0;
        }
        return mainHouse.getAsItem().getRoomSceneId();
    }

    public boolean isRoom() {
        return mainHouse == null || mainHouse.getAsItem() == null;
    }

    @Nullable public Position getTeleportPointPos(int guid) {
        return this.getBlockItems().values().stream()
                .map(HomeBlockItem::getDeployFurnitureList)
                .flatMap(Collection::stream)
                .filter(homeFurnitureItem -> homeFurnitureItem.getGuid() == guid)
                .map(HomeFurnitureItem::getSpawnPos)
                .findFirst()
                .orElse(null);
    }

    public List<EntityHomeAnimal> getAnimals(Scene scene) {
        return this.blockItems.values().stream()
                .map(HomeBlockItem::getDeployAnimalList)
                .flatMap(Collection::stream)
                .filter(
                        homeAnimalItem ->
                                GameData.getHomeWorldAnimalDataMap().containsKey(homeAnimalItem.getFurnitureId()))
                .map(
                        homeAnimalItem -> {
                            return new EntityHomeAnimal(
                                    scene,
                                    GameData.getHomeWorldAnimalDataMap().get(homeAnimalItem.getFurnitureId()),
                                    homeAnimalItem.getSpawnPos(),
                                    homeAnimalItem.getSpawnRot());
                        })
                .toList();
    }

    public int calComfort() {
        return this.blockItems.values().stream().mapToInt(HomeBlockItem::calComfort).sum();
    }

    public HomeSceneArrangementInfo toProto() {
        var proto = HomeSceneArrangementInfo.newBuilder();
        blockItems.values().forEach(b -> proto.addBlockArrangementInfoList(b.toProto()));

        proto
                .setComfortValue(calComfort())
                .setBornPos(bornPos.toProto())
                .setBornRot(bornRot.toProto())
                .setDjinnPos(djinnPos.toProto())
                .setIsSetBornPos(true)
                .setSceneId(sceneId)
                .setBgmId(homeBgmId)
                .setTmpVersion(tmpVersion);

        if (mainHouse != null) {
            proto.setMainHouse(mainHouse.toProto());
        }
        return proto.build();
    }
}
