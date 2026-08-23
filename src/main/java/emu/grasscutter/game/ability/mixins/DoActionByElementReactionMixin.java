package emu.grasscutter.game.ability.mixins;

import com.google.protobuf.ByteString;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.GameEntity;

@AbilityMixin(value = AbilityMixinData.Type.DoActionByElementReactionMixin)
public class DoActionByElementReactionMixin extends AbilityMixinHandler {

    @Override
    public boolean execute(
            Ability ability, AbilityMixinData mixinData, ByteString abilityData, GameEntity target) {
        return runActions(ability, mixinData, abilityData, target);
    }

    protected boolean runActions(
            Ability ability, AbilityMixinData mixinData, ByteString abilityData, GameEntity target) {
        var actions = mixinData.actions;
        if (actions == null || actions.length == 0) actions = mixinData.actionQueue;
        if (actions == null || actions.length == 0) return false;

        var manager = ability.getManager();
        if (manager == null || target == null) return false;

        for (var action : actions) {
            if (action == null) continue;
            manager.executeAction(ability, action, abilityData, target);
        }

        Grasscutter.getLogger()
                .trace(
                        "Element reaction mixin {} ran {} action(s) for {} (reactions: {})",
                        mixinData.type,
                        actions.length,
                        ability.getData() != null ? ability.getData().abilityName : "?",
                        mixinData.reactionTypes);

        return true;
    }
}
