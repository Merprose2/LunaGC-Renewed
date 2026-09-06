package emu.grasscutter.game.activity.leyline;

import emu.grasscutter.game.activity.ActivityWatcher;
import emu.grasscutter.game.activity.ActivityWatcherType;
import emu.grasscutter.game.props.WatcherTriggerType;

/**
 * Watcher for the Stygian Onslaught / Ley Line Challenge (activity 5269) difficulty completion
 * rows. The excel param is the required difficulty ("1"-"6"); "0" (or an empty param) matches any
 * difficulty.
 */
@ActivityWatcherType(WatcherTriggerType.TRIGGER_LEY_LINE_CHALLENGE_FINISH_DIFFICULTY)
public class LeyLineChallengeWatcher extends ActivityWatcher {

    @Override
    protected boolean isMeet(String... param) {
        var data = getActivityWatcherData();
        if (data == null || data.getTriggerConfig() == null) {
            return false;
        }
        var list = data.getTriggerConfig().getParamList();
        if (list == null || list.isEmpty() || param.length == 0) {
            return false;
        }
        String target = list.get(0);
        return "0".equals(target) || target.equals(param[0]);
    }
}