package emu.grasscutter.game.investigation;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.net.proto.InvestigationOuterClass.Investigation;
import emu.grasscutter.net.proto.InvestigationTargetOuterClass.InvestigationTarget;
import emu.grasscutter.net.proto.PlayerInvestigationTargetNotifyOuterClass.PlayerInvestigationTargetNotify;
import emu.grasscutter.server.packet.send.*;
import java.util.*;

public class InvestigationManager extends BasePlayerManager {

    public InvestigationManager(Player player) {
        super(player);
    }

    public void onPlayerLogin() {
        initInvestigations();

        var targetsProto =
                this.getPlayer().getInvestigationTargets().values().stream()
                        .map(PlayerInvestigationTarget::toProto)
                        .toList();

        var targetNotify =
                PlayerInvestigationTargetNotify.newBuilder()
                        .addAllInvestigationTargetList(targetsProto)
                        .build();

        var investigationsProto =
                this.getPlayer().getInvestigations().values().stream()
                        .map(PlayerInvestigation::toProto)
                        .toList();

        this.getPlayer().sendPacket(
                new PacketPlayerInvestigationAllInfoNotify(investigationsProto, targetNotify));
        this.getPlayer().sendPacket(new PacketPlayerInvestigationNotify(investigationsProto));
        this.getPlayer().sendPacket(new PacketPlayerInvestigationTargetNotify(targetsProto));

        Grasscutter.getLogger()
                .debug("Loaded {} investigations and {} targets for player {}",
                        investigationsProto.size(),
                        targetsProto.size(),
                        this.getPlayer().getUid());
    }

    private void initInvestigations() {
        var investigations = this.getPlayer().getInvestigations();
        var targets = this.getPlayer().getInvestigationTargets();

        // 1. Group targets by investigation ID for total progress calculation
        Map<Integer, Integer> targetCountPerInvestigation = new HashMap<>();
        for (InvestigationTargetData targetData : GameData.getInvestigationTargetDataMap().values()) {
            if (targetData.isDisuse()) continue;
            targetCountPerInvestigation.merge(targetData.getInvestigationId(), 1, Integer::sum);
        }

        // 2. Initialize / update Investigation Targets
        for (InvestigationTargetData targetData : GameData.getInvestigationTargetDataMap().values()) {
            if (targetData.isDisuse()) continue;
            var target = targets.get(targetData.getId());
            if (target == null) {
                int progress = targetData.getProgress() > 0 ? targetData.getProgress() : 1;
                target =
                        PlayerInvestigationTarget.builder()
                                .targetId(targetData.getId())
                                .investigationId(targetData.getInvestigationId())
                                .questId(targetData.getQuestId())
                                .progress(progress)
                                .totalProgress(progress)
                                .state(InvestigationTarget.State.State_COMPLETE_VALUE)
                                .build();
                targets.put(targetData.getId(), target);
            } else {
                target.setTargetId(targetData.getId());
                target.setInvestigationId(targetData.getInvestigationId());
                if (target.getTotalProgress() == 0) {
                    int progress = targetData.getProgress() > 0 ? targetData.getProgress() : 1;
                    target.setProgress(progress);
                    target.setTotalProgress(progress);
                }
            }
        }

        // 3. Initialize / update Investigations
        for (InvestigationData data : GameData.getInvestigationDataMap().values()) {
            int totalProgress = targetCountPerInvestigation.getOrDefault(data.getId(), 1);
            var inv = investigations.get(data.getId());
            if (inv == null) {
                inv =
                        PlayerInvestigation.builder()
                                .id(data.getId())
                                .progress(totalProgress)
                                .totalProgress(totalProgress)
                                .state(Investigation.State.State_COMPLETE_VALUE)
                                .build();
                investigations.put(data.getId(), inv);
            } else {
                inv.setTotalProgress(totalProgress);
                if (inv.getProgress() == 0 && inv.getState() == Investigation.State.State_COMPLETE_VALUE) {
                    inv.setProgress(totalProgress);
                }
            }
        }
    }

    public boolean takeInvestigationReward(int investigationId) {
        var investigation = this.getPlayer().getInvestigations().get(investigationId);
        if (investigation == null) {
            Grasscutter.getLogger().warn("Player {} tried to take unknown investigation reward {}",
                    this.getPlayer().getUid(), investigationId);
            return false;
        }

        if (investigation.getState() == Investigation.State.State_REWARD_TAKEN_VALUE) {
            Grasscutter.getLogger().debug("Player {} already took investigation reward {}",
                    this.getPlayer().getUid(), investigationId);
            return false;
        }

        investigation.setState(Investigation.State.State_REWARD_TAKEN_VALUE);

        var data = GameData.getInvestigationDataMap().get(investigationId);
        if (data != null && data.getRewardId() > 0) {
            grantReward(data.getRewardId(), ActionReason.InvestigationReward);
        }

        this.getPlayer().save();
        this.getPlayer().sendPacket(new PacketPlayerInvestigationNotify(List.of(investigation.toProto())));
        return true;
    }

    public boolean takeInvestigationTargetReward(int targetId) {
        var target = this.getPlayer().getInvestigationTargets().get(targetId);
        if (target == null) {
            target = this.getPlayer().getInvestigationTargets().values().stream()
                    .filter(t -> t.getQuestId() == targetId || t.getTargetId() == targetId)
                    .findFirst()
                    .orElse(null);
        }

        if (target == null) {
            Grasscutter.getLogger().warn("Player {} tried to take unknown investigation target reward {}",
                    this.getPlayer().getUid(), targetId);
            return false;
        }

        if (target.getState() == InvestigationTarget.State.State_REWARD_TAKEN_VALUE) {
            Grasscutter.getLogger().debug("Player {} already took investigation target reward {}",
                    this.getPlayer().getUid(), targetId);
            return false;
        }

        target.setState(InvestigationTarget.State.State_REWARD_TAKEN_VALUE);

        var targetData = GameData.getInvestigationTargetDataMap().get(target.getTargetId());
        if (targetData != null && targetData.getRewardId() > 0) {
            grantReward(targetData.getRewardId(), ActionReason.InvestigationTargetReward);
        }

        this.getPlayer().save();
        this.getPlayer().sendPacket(new PacketPlayerInvestigationTargetNotify(List.of(target.toProto())));
        return true;
    }

    private void grantReward(int rewardId, ActionReason reason) {
        var rewardData = GameData.getRewardDataMap().get(rewardId);
        if (rewardData != null && rewardData.getRewardItemList() != null) {
            List<GameItem> items = new ArrayList<>();
            for (var param : rewardData.getRewardItemList()) {
                var itemData = GameData.getItemDataMap().get(param.getId());
                if (itemData != null && param.getCount() > 0) {
                    items.add(new GameItem(itemData, param.getCount()));
                }
            }
            if (!items.isEmpty()) {
                this.getPlayer().getInventory().addItems(items, reason);
            }
        }
    }
}
