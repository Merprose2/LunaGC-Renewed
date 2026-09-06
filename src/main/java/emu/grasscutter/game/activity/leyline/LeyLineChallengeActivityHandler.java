package emu.grasscutter.game.activity.leyline;

import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.activity.condition.ActivityConditionExecutor;
import emu.grasscutter.game.props.ActivityType;
import emu.grasscutter.net.proto.ActivityInfoOuterClass.ActivityInfo;

/**
 * Stygian Onslaught / Ley Line Challenge (activity 5269, 7.0).
 *
 * <p>The client must receive a full {@link ActivityInfo} (carrying the
 * ley_line_challenge_detail_info payload) at login, otherwise the event UI shows the mode as
 * closed. The payload shape is owned by the player's {@link
 * emu.grasscutter.game.stygian.StygianOnslaughtManager}, which also pushes the same proto inside
 * the challenge flow - so this handler simply delegates to it.
 */
@GameActivity(ActivityType.NEW_ACTIVITY_LEY_LINE_CHALLENGE)
public class LeyLineChallengeActivityHandler extends ActivityHandler {

    @Override
    public void onProtoBuild(
            PlayerActivityData playerActivityData, ActivityInfo.Builder activityInfo) {}

    @Override
    public void onInitPlayerActivityData(PlayerActivityData playerActivityData) {}

    @Override
    public ActivityInfo toProto(
            PlayerActivityData playerActivityData, ActivityConditionExecutor conditionExecutor) {
        if (playerActivityData != null && playerActivityData.getPlayer() != null) {
            var manager = playerActivityData.getPlayer().getStygianOnslaughtManager();
            if (manager != null) {
                return manager.buildActivityInfo();
            }
        }
        return super.toProto(playerActivityData, conditionExecutor);
    }
}
