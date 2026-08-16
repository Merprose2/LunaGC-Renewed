package emu.grasscutter.game.entity.gadget;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.codex.CodexViewpointData;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq;
import emu.grasscutter.net.proto.InteractTypeOuterClass.InteractType;
import emu.grasscutter.net.proto.SceneGadgetInfoOuterClass.SceneGadgetInfo;
import emu.grasscutter.server.packet.send.PacketGadgetInteractRsp;

public final class GadgetViewPoint extends GadgetContent {
    public GadgetViewPoint(EntityGadget gadget) {
        super(gadget);
    }

    @Override
    public boolean onInteract(Player player, GadgetInteractReq req) {
        int groupId = this.getGadget().getGroupId();
        int configId = this.getGadget().getConfigId();

        CodexViewpointData viewpoint =
                GameData.getViewCodexByGroupConfig(groupId, configId);

        if (viewpoint == null) {
            return false;
        }

        player.getCodex().checkUnlockedViewPoint(viewpoint);

        player.sendPacket(
                new PacketGadgetInteractRsp(
                        getGadget(),
                        InteractType.InteractType_INTERACT_VIEW));

        return false;
    }

    @Override
    public void onBuildProto(SceneGadgetInfo.Builder gadgetInfo) {}
}
