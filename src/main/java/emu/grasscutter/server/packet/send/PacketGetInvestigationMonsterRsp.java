package emu.grasscutter.server.packet.send;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.WorldDataSystem;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetInvestigationMonsterRspOuterClass;
import emu.grasscutter.net.proto.InvestigationMonsterOuterClass;
import java.util.ArrayList;
import java.util.List;

public class PacketGetInvestigationMonsterRsp extends BasePacket {

    public PacketGetInvestigationMonsterRsp(
            Player player, WorldDataSystem worldDataManager, List<Integer> cityIdListList) {
        this(player, worldDataManager, cityIdListList, false);
    }

    public PacketGetInvestigationMonsterRsp(
            Player player,
            WorldDataSystem worldDataManager,
            List<Integer> cityIdListList,
            boolean isForMark) {
        super(PacketOpcodes.GetInvestigationMonsterRsp);

        var resolvedCityIds = resolveCityIds(cityIdListList, worldDataManager);

        var resp =
                GetInvestigationMonsterRspOuterClass.GetInvestigationMonsterRsp.newBuilder()
                        .setRetcode(0)
                        .setIsForMark(isForMark);

        List<InvestigationMonsterOuterClass.InvestigationMonster> monsters = new ArrayList<>();

        for (int cityId : resolvedCityIds) {
            if (isForMark) {
                monsters.addAll(worldDataManager.getInvestigationMonsterMapMarkersByCityId(player, cityId));
            } else {
                monsters.addAll(worldDataManager.getInvestigationMonstersByCityId(player, cityId));
            }
        }

        Grasscutter.getLogger()
                .info(
                        "GetInvestigationMonsterRsp: isForMark={}, requestedCities={}, resolvedCities={}, monsters={}",
                        isForMark,
                        cityIdListList,
                        resolvedCityIds,
                        monsters.size());

        resp.addAllMonsterList(monsters);

        this.setData(resp.build());
    }

    private List<Integer> resolveCityIds(
            List<Integer> requestedCityIds, WorldDataSystem worldDataManager) {
        if (requestedCityIds != null && !requestedCityIds.isEmpty()) {
            var filtered =
                    requestedCityIds.stream()
                            .filter(id -> id != null && id > 0)
                            .distinct()
                            .toList();

            if (!filtered.isEmpty()) {
                return filtered;
            }
        }

        return worldDataManager.getInvestigationMonsterCityIds();
    }
}