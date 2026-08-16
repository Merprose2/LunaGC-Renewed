package emu.grasscutter.game.home;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.EnterReason;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.World;
import emu.grasscutter.game.world.data.TeleportProperties;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.server.event.player.PlayerEnterHomeEvent;
import emu.grasscutter.server.event.player.PlayerLeaveHomeEvent;
import emu.grasscutter.server.event.player.PlayerTeleportEvent;
import emu.grasscutter.server.game.BaseGameSystem;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.server.packet.send.*;

public class HomeWorldMPSystem extends BaseGameSystem {
    public HomeWorldMPSystem(GameServer server) {
        super(server);
    }

    public void sendEnterHomeRequest(Player requester, int ownerUid) {
        var owner = getServer().getPlayerByUid(ownerUid);
        if (owner == null) {
            requester.sendPacket(
                    new PacketPlayerApplyEnterHomeResultNotify(
                            ownerUid,
                            "",
                            false,
                            PlayerApplyEnterHomeResultNotifyOuterClass.PlayerApplyEnterHomeResultNotify.Reason
                                    .OPEN_STATE_NOT_OPEN));
            requester.sendPacket(new PacketTryEnterHomeRsp());
            return;
        }

        if (owner.getRealmList() == null) {
            requester.sendPacket(
                    new PacketPlayerApplyEnterHomeResultNotify(
                            ownerUid,
                            "",
                            false,
                            PlayerApplyEnterHomeResultNotifyOuterClass.PlayerApplyEnterHomeResultNotify.Reason
                                    .OPEN_STATE_NOT_OPEN));
            requester.sendPacket(new PacketTryEnterHomeRsp());
            return;
        }

        var request = owner.getEnterHomeRequests().get(requester.getUid());

        if (request != null && !request.isExpired()) {
            return;
        }

        if (owner.isInEditMode()) {
            requester.sendPacket(
                    new PacketPlayerApplyEnterHomeResultNotify(
                            ownerUid,
                            owner.getNickname(),
                            false,
                            PlayerApplyEnterHomeResultNotifyOuterClass.PlayerApplyEnterHomeResultNotify.Reason
                                    .HOST_IN_EDIT_MODE));
            requester.sendPacket(new PacketTryEnterHomeRsp());
            return;
        }

        request = new EnterHomeRequest(requester);
        owner.getEnterHomeRequests().put(requester.getUid(), request);

        owner.sendPacket(new PacketPlayerApplyEnterHomeNotify(requester));
    }

    public void acceptEnterHomeRequest(Player owner, int requesterUid, boolean isAgreed) {
        var request = owner.getEnterHomeRequests().get(requesterUid);
        if (request == null || request.isExpired()) {
            return;
        }

        var requester = request.getRequester();
        owner.getEnterHomeRequests().remove(requesterUid);

        if (requester.getWorld().isMultiplayer()) {
            requester.sendPacket(
                    new PacketPlayerApplyEnterHomeResultNotify(
                            owner.getUid(),
                            owner.getNickname(),
                            false,
                            PlayerApplyEnterHomeResultNotifyOuterClass.PlayerApplyEnterHomeResultNotify.Reason
                                    .SYSTEM_JUDGE));
            requester.sendPacket(new PacketTryEnterHomeRsp());
            return;
        }

        requester.sendPacket(
                new PacketPlayerApplyEnterHomeResultNotify(
                        owner.getUid(),
                        owner.getNickname(),
                        isAgreed,
                        PlayerApplyEnterHomeResultNotifyOuterClass.PlayerApplyEnterHomeResultNotify.Reason
                                .PLAYER_JUDGE));

        if (!isAgreed) {
            requester.sendPacket(new PacketTryEnterHomeRsp());
            return;
        }

        this.enterHome(requester, owner, 0, false);
    }

    public void enterHome(Player requester, Player owner, int teleportPoint, boolean toSafe) {
        if (requester.getWorld().isMultiplayer()) {
            return;
        }

        if (owner.getRealmList() == null) {

            requester.sendPacket(
                    new PacketTryEnterHomeRsp(
                            RetcodeOuterClass.Retcode.RET_HOME_NOT_FOUND_IN_MEM_VALUE, owner.getUid()));
            return;
        }

        var world = this.server.getHomeWorldOrCreate(owner);
        var targetHome = world.getHome();

        var event = new PlayerEnterHomeEvent(requester, owner, targetHome);
        event.call();
        if (event.isCanceled()) {
            requester.sendPacket(
                    new PacketTryEnterHomeRsp(
                            RetcodeOuterClass.Retcode.RET_HOME_OWNER_REFUSE_TO_ENTER_HOME_VALUE, owner.getUid()));
            return;
        }

        if (owner.isInEditMode()) {
            requester.sendPacket(
                    new PacketTryEnterHomeRsp(
                            RetcodeOuterClass.Retcode.RET_HOME_CANT_ENTER_BY_IN_EDIT_MODE_VALUE, owner.getUid()));
            return;
        }

        int realmId = 2000 + owner.getCurrentRealmId();
        var item = targetHome.getHomeSceneItem(realmId);
        var scene = world.getSceneById(realmId);
        targetHome.save();

        Position pos;
        if (scene != null) {
            pos = toSafe ? scene.getScriptManager().getConfig().born_pos : item.getBornPos();
        } else {
            pos = item.getBornPos();
        }

        if (teleportPoint != 0) {
            var target = item.getTeleportPointPos(teleportPoint);
            if (target != null) {
                pos = target;
            }
        }

        requester.getPrevPosForHome().set(requester.getPosition());
        requester.setCurHomeWorld(world);
        requester.setPrevScene(requester.getSceneId());
        world.addPlayer(requester, realmId);
        requester.setSceneId(realmId);
        requester.getPosition().set(pos);

        requester.sendPacket(
                new PacketPlayerEnterSceneNotify(
                        requester,
                        owner.getUid(),
                        TeleportProperties.builder()
                                .sceneId(realmId)
                                .enterReason(EnterReason.EnterHome)
                                .teleportTo(pos)
                                .teleportType(PlayerTeleportEvent.TeleportType.INTERNAL)
                                .build(),
                        !requester.equals(owner)));
        requester.sendPacket(new PacketEnterScenePeerNotify(requester));
        requester.sendPacket(new PacketTryEnterHomeRsp(owner.getUid()));

        requester.setHasSentInitPacketInHome(false);
        world.getPlayers().stream()
                .filter(player -> !player.equals(requester))
                .forEach(player -> player.sendPacket(new PacketPlayerPreEnterMpNotify(requester)));
    }

    public boolean leaveCoop(Player player, int prevScene) {
        return this.leaveCoop(player, prevScene, player.getPrevPosForHome());
    }

    public boolean leaveCoop(Player player, int prevScene, Position pos) {
        if (player == null) {
            Grasscutter.getLogger().warn("[HomeExit] leaveCoop called with a null player");
            return false;
        }

        var sourceWorld = player.getWorld();

        if (!(sourceWorld instanceof HomeWorld sourceHomeWorld)) {
            Grasscutter.getLogger()
                    .warn(
                            "[HomeExit] Rejected Home exit because the active world is not a HomeWorld: "
                                    + "uid={}, currentSceneId={}, worldClass={}",
                            player.getUid(),
                            player.getSceneId(),
                            sourceWorld != null
                                    ? sourceWorld.getClass().getSimpleName()
                                    : "null");
            return false;
        }

        Position destination =
                pos != null
                        ? pos.clone()
                        : player.getPrevPosForHome() != null
                                ? player.getPrevPosForHome().clone()
                                : new Position();

        Grasscutter.getLogger()
                .debug(
                        "[HomeExit] Begin leaveCoop: uid={}, requestedSceneId={}, "
                                + "currentSceneId={}, previousSceneId={}, sceneLoadState={}, "
                                + "peerId={}, sourceWorldClass={}, sourceHostUid={}, "
                                + "destination={}, players={}",
                        player.getUid(),
                        prevScene,
                        player.getSceneId(),
                        player.getPrevScene(),
                        player.getSceneLoadState(),
                        player.getPeerId(),
                        sourceHomeWorld.getClass().getSimpleName(),
                        sourceHomeWorld.getHost() != null
                                ? sourceHomeWorld.getHost().getUid()
                                : 0,
                        destination,
                        describeWorldPlayers(sourceHomeWorld));

        for (var worldPlayer : sourceHomeWorld.getPlayers()) {
            if (worldPlayer.getSceneLoadState() != Player.SceneLoadState.LOADED) {
                Grasscutter.getLogger()
                        .warn(
                                "[HomeExit] Blocked Home exit because a player is not LOADED: "
                                        + "requesterUid={}, blockedUid={}, blockedSceneId={}, "
                                        + "blockedState={}, players={}",
                                player.getUid(),
                                worldPlayer.getUid(),
                                worldPlayer.getSceneId(),
                                worldPlayer.getSceneLoadState(),
                                describeWorldPlayers(sourceHomeWorld));
                return false;
            }
        }

        var event =
                new PlayerLeaveHomeEvent(
                        player,
                        sourceHomeWorld.getHost(),
                        sourceHomeWorld.getHome(),
                        PlayerLeaveHomeEvent.Reason.PLAYER_LEAVE);
        event.call();

        player.getPosition().set(destination);

        Grasscutter.getLogger()
                .debug(
                        "[HomeExit] Creating single-player world: uid={}, oldWorldPlayers={}, "
                                + "oldSceneId={}, targetSceneId={}, targetPos={}",
                        player.getUid(),
                        describeWorldPlayers(sourceHomeWorld),
                        player.getSceneId(),
                        prevScene,
                        destination);

        var world = new World(player);
        world.addPlayer(player, prevScene);

        Grasscutter.getLogger()
                .debug(
                        "[HomeExit] World swap complete: uid={}, worldClass={}, isMultiplayer={}, "
                                + "sceneId={}, peerId={}, sceneLoadState={}, worldPlayers={}",
                        player.getUid(),
                        player.getWorld() != null
                                ? player.getWorld().getClass().getSimpleName()
                                : "null",
                        player.getWorld() != null && player.getWorld().isMultiplayer(),
                        player.getSceneId(),
                        player.getPeerId(),
                        player.getSceneLoadState(),
                        describeWorldPlayers(player.getWorld()));

        sourceHomeWorld.sendPacketToHostIfOnline(
                new PacketOtherPlayerEnterOrLeaveHomeNotify(
                        player,
                        OtherPlayerEnterHomeNotifyOuterClass.OtherPlayerEnterHomeNotify.Reason.LEAVE));

        var myHome = this.server.getHomeWorldOrCreate(player);
        player.setCurHomeWorld(myHome);
        myHome.getHome().onOwnerLogin(player);

        Grasscutter.getLogger()
                .debug(
                        "[HomeExit] Sending Home exit packets: uid={}, "
                                + "PlayerQuitFromHomeNotifyOpcode={}, PlayerEnterSceneNotifyOpcode={}, "
                                + "EnterScenePeerNotifyOpcode={}, sceneId={}, peerId={}, targetPos={}",
                        player.getUid(),
                        PacketOpcodes.PlayerQuitFromHomeNotify,
                        PacketOpcodes.PlayerEnterSceneNotify,
                        PacketOpcodes.EnterScenePeerNotify,
                        prevScene,
                        player.getPeerId(),
                        destination);

        player.sendPacket(
                new PacketPlayerQuitFromHomeNotify(
                        PlayerQuitFromHomeNotifyOuterClass.PlayerQuitFromHomeNotify.QuitReason
                                .BACK_TO_MY_WORLD));
        player.sendPacket(
                new PacketPlayerEnterSceneNotify(
                        player,
                        EnterTypeOuterClass.EnterType.EnterType_ENTER_BACK,
                        EnterReason.TeamBack,
                        prevScene,
                        destination));

        /*
         * Normal scene transfers wait for EnterSceneReadyReq before sending
         * EnterScenePeerNotify. Keep Home exit on that same sequence.
         */
        Grasscutter.getLogger()
                .debug(
                        "[HomeExit] Waiting for EnterSceneReadyReq before sending "
                                + "EnterScenePeerNotify: uid={}, enterSceneToken={}, sceneId={}",
                        player.getUid(),
                        player.getEnterSceneToken(),
                        player.getSceneId());

        Grasscutter.getLogger()
                .debug(
                        "[HomeExit] leaveCoop completed: uid={}, worldClass={}, "
                                + "isMultiplayer={}, sceneId={}, peerId={}, sceneLoadState={}, "
                                + "enterSceneToken={}",
                        player.getUid(),
                        player.getWorld() != null
                                ? player.getWorld().getClass().getSimpleName()
                                : "null",
                        player.getWorld() != null && player.getWorld().isMultiplayer(),
                        player.getSceneId(),
                        player.getPeerId(),
                        player.getSceneLoadState(),
                        player.getEnterSceneToken());

        return true;
    }

    private static String describeWorldPlayers(World world) {
        if (world == null) {
            return "[]";
        }

        return world.getPlayers().stream()
                .map(
                        player ->
                                "{uid="
                                        + player.getUid()
                                        + ",sceneId="
                                        + player.getSceneId()
                                        + ",state="
                                        + player.getSceneLoadState()
                                        + ",peerId="
                                        + player.getPeerId()
                                        + "}")
                .toList()
                .toString();
    }

    public boolean kickPlayerFromHome(Player owner, int targetUid) {

        if (!owner.getCurHomeWorld().getHost().equals(owner)) {
            return false;
        }

        var victim = owner.getServer().getPlayerByUid(targetUid);
        if (victim == null || owner.equals(victim)) {
            return false;
        }

        if (victim.getSceneLoadState() != Player.SceneLoadState.LOADED) {
            return false;
        }

        var event =
                new PlayerLeaveHomeEvent(
                        victim, owner, victim.getCurHomeWorld().getHome(), PlayerLeaveHomeEvent.Reason.KICKED);
        event.call();

        victim.getPosition().set(victim.getPrevPosForHome());
        var world = new World(victim);
        world.addPlayer(victim, 3);
        victim
                .getCurHomeWorld()
                .sendPacketToHostIfOnline(
                        new PacketOtherPlayerEnterOrLeaveHomeNotify(
                                victim,
                                OtherPlayerEnterHomeNotifyOuterClass.OtherPlayerEnterHomeNotify.Reason.LEAVE));
        var myHome = this.server.getHomeWorldOrCreate(victim);
        victim.setCurHomeWorld(myHome);
        myHome.getHome().onOwnerLogin(victim);

        victim.sendPacket(
                new PacketPlayerQuitFromHomeNotify(
                        PlayerQuitFromHomeNotifyOuterClass.PlayerQuitFromHomeNotify.QuitReason.KICK_BY_HOST));
        victim.sendPacket(
                new PacketPlayerEnterSceneNotify(
                        victim,
                        EnterTypeOuterClass.EnterType.EnterType_ENTER_BACK,
                        EnterReason.TeamKick,
                        victim.getScene().getId(),
                        victim.getPrevPosForHome()));
        victim.sendPacket(new PacketEnterScenePeerNotify(victim));
        return true;
    }
}
