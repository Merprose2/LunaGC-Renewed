package emu.grasscutter.game.entity.gadget;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq;
import emu.grasscutter.net.proto.GatherGadgetInfoOuterClass.GatherGadgetInfo;
import emu.grasscutter.net.proto.InteractTypeOuterClass.InteractType;
import emu.grasscutter.net.proto.SceneGadgetInfoOuterClass.SceneGadgetInfo;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.ScriptArgs;
import emu.grasscutter.server.packet.send.PacketGadgetInteractRsp;
import emu.grasscutter.utils.Utils;

public final class GadgetGatherObject extends GadgetContent {
    private int itemId;
    private boolean isForbidGuest;

    public GadgetGatherObject(EntityGadget gadget) {
		super(gadget);

		int staticGatherItemId = 0;

		if (gadget.getSpawnEntry() != null) {
			staticGatherItemId = gadget.getSpawnEntry().getGatherItemId();
		}

		GatherData gatherData = resolveGatherData(gadget, staticGatherItemId);

		if (gatherData != null) {
			this.itemId = staticGatherItemId > 0 ? staticGatherItemId : gatherData.getItemId();
			this.isForbidGuest = gatherData.isForbidGuest();

			if (gadget.getPointType() == 0 && gatherData.getId() > 0) {
				gadget.setPointType(gatherData.getId());
			}

			return;
		}

		if (staticGatherItemId > 0) {
			this.itemId = staticGatherItemId;
			return;
		}

		Grasscutter.getLogger()
				.debug(
						"Could not resolve gather object item. configId={}, gadgetId={}, pointType={}, spawnEntry={}",
						gadget.getConfigId(),
						gadget.getGadgetId(),
						gadget.getPointType(),
						gadget.getSpawnEntry() != null);
	}

	private static GatherData resolveGatherData(EntityGadget gadget, int staticGatherItemId) {
		if (gadget.getPointType() > 0) {
			GatherData byPointType = GameData.getGatherDataMap().get(gadget.getPointType());

			if (byPointType != null) {
				return byPointType;
			}
		}

		int gadgetId = gadget.getGadgetId();

		if (gadgetId > 0) {
			for (GatherData gatherData : GameData.getGatherDataMap().values()) {
				if (gatherData.getGadgetId() == gadgetId) {
					return gatherData;
				}
			}
		}

		if (staticGatherItemId > 0) {
			for (GatherData gatherData : GameData.getGatherDataMap().values()) {
				if (gatherData.getItemId() == staticGatherItemId) {
					return gatherData;
				}
			}
		}
		return null;
	}

    public int getItemId() {
        return this.itemId;
    }

    public boolean isForbidGuest() {
        return isForbidGuest;
    }

    public boolean onInteract(Player player, GadgetInteractReq req) {
        // Sanity check
        ItemData itemData = GameData.getItemDataMap().get(getItemId());
        if (itemData == null) {
            return false;
        }

        GameItem item = new GameItem(itemData, 1);
        player.getInventory().addItem(item, ActionReason.Gather);

        var ScriptArgs =
                new ScriptArgs(getGadget().getGroupId(), EventType.EVENT_GATHER, getGadget().getConfigId());
        if (getGadget().getMetaGadget() != null) {
            ScriptArgs.setEventSource(getGadget().getMetaGadget().config_id);
        }
        getGadget().getScene().getScriptManager().callEvent(ScriptArgs);

        getGadget()
                .getScene()
                .broadcastPacket(
                        new PacketGadgetInteractRsp(getGadget(), InteractType.InteractType_INTERACT_GATHER));

        return true;
    }

    public void onBuildProto(SceneGadgetInfo.Builder gadgetInfo) {
        GatherGadgetInfo gatherGadgetInfo =
                GatherGadgetInfo.newBuilder()
                        .setItemId(this.getItemId())
                        .setIsForbidGuest(this.isForbidGuest())
                        .build();

        gadgetInfo.setGatherGadget(gatherGadgetInfo);
    }

    public void dropItems(Player player) {
		if (this.itemId <= 0 || GameData.getItemDataMap().get(this.itemId) == null) {
			Grasscutter.getLogger()
					.trace(
							"Skipping gather drop with invalid itemId. configId={}, gadgetId={}, pointType={}, spawnEntry={}",
							getGadget().getConfigId(),
							getGadget().getGadgetId(),
							getGadget().getPointType(),
							getGadget().getSpawnEntry() != null);
			return;
		}
		Scene scene = getGadget().getScene();
		int times = Utils.randomRange(1, 2);

        for (int i = 0; i < times; i++) {
            EntityItem item =
                    new EntityItem(
                            scene,
                            player,
                            GameData.getItemDataMap().get(itemId),
                            getGadget().getPosition().nearby2d(1f).addY(2f),
                            1,
                            true);

            scene.addEntity(item);
        }

        scene.killEntity(this.getGadget(), player.getTeamManager().getCurrentAvatarEntity().getId());
        // Todo: add record
    }
	
	public boolean requiresBreaking() {
		return this.getGadget().getSpawnEntry() != null
				&& this.getGadget().getSpawnEntry().getGadgetState() == 1;
	}
}
