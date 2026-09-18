package emu.grasscutter.server.packet.recv;

import static emu.grasscutter.config.Configuration.*;

import emu.grasscutter.*;
import emu.grasscutter.command.commands.SendMailCommand.MailBuilder;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.mail.Mail;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.SetPlayerBornDataReqOuterClass.SetPlayerBornDataReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.*;
import java.util.Arrays;

@Opcodes(PacketOpcodes.SetPlayerBornDataReq)
public class HandlerSetPlayerBornDataReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        SetPlayerBornDataReq req = SetPlayerBornDataReq.parseFrom(payload);

        // Sanity checks
        int avatarId = req.getAvatarId();
        int startingSkillDepot;
        if (avatarId == GameConstants.MAIN_CHARACTER_MALE) {
            startingSkillDepot = 504;
        } else if (avatarId == GameConstants.MAIN_CHARACTER_FEMALE) {
            startingSkillDepot = 704;
        } else {
            return;
        }

        // Make sure resources folder is set
        if (!GameData.getAvatarDataMap().containsKey(avatarId)) {
            Grasscutter.getLogger()
                    .error("No avatar data found! Please check your ExcelBinOutput folder.");
            session.close();
            return;
        }

        // Get player object
        Player player = session.getPlayer();

        // Only the very first avatar of the account is created through the born flow.
        if (player.getAvatars().getAvatarCount() != 0) {
            return;
        }

        // Keep the name the player entered on the born screen.
        player.setNickname(req.getNickName());

        // Create avatar
        Avatar mainCharacter = new Avatar(avatarId);

        // Check if the default Anemo skill should be given.
        if (!GAME_OPTIONS.questing.enabled) {
            mainCharacter.setSkillDepotData(
                    GameData.getAvatarSkillDepotDataMap().get(startingSkillDepot));
        }

        // Manually handle adding to team
        player.addAvatar(mainCharacter, false);
        player.setMainCharacterId(avatarId);
        player.setHeadImage(avatarId);
        player
                .getTeamManager()
                .getCurrentSinglePlayerTeamInfo()
                .getAvatars()
                .add(mainCharacter.getAvatarId());
        player.save(); // TODO save player team in different object

        // Login done
        player.onLogin();

        // Born resp packet - tells the client its main character now exists, so it can leave the
        // born screen and enter the world.
        session.send(new PacketSetPlayerBornDataRsp());

        // A normal login pushes the activity list right after PlayerLoginRsp; the born flow returns
        // before that point, so push it here to keep both login paths identical.
        session.send(new PacketGetActivityInfoRsp(player.getActivityManager()));

        // Default mail
        var welcomeMail = GAME_INFO.joinOptions.welcomeMail;
        MailBuilder mailBuilder = new MailBuilder(player.getUid(), new Mail());
        mailBuilder.mail.mailContent.title = welcomeMail.title;
        mailBuilder.mail.mailContent.sender = welcomeMail.sender;
        // Please credit Grasscutter if changing something here. We don't condone commercial use of the
        // project.
        mailBuilder.mail.mailContent.content =
                welcomeMail.content
                        + "\n<type=\"browser\" text=\"GitHub\" href=\"https://github.com/Grasscutters/Grasscutter\"/>";
        mailBuilder.mail.itemList.addAll(Arrays.asList(welcomeMail.items));
        mailBuilder.mail.importance = 1;
        player.sendMail(mailBuilder.mail);
    }
}
