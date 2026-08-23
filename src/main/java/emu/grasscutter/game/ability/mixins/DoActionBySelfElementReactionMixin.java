package emu.grasscutter.game.ability.mixins;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.GameEntity;

@AbilityMixin(value = AbilityMixinData.Type.DoActionBySelfElementReactionMixin)
public class DoActionBySelfElementReactionMixin extends DoActionByElementReactionMixin {

    @Override
    public boolean execute(
            Ability ability, AbilityMixinData mixinData, ByteString abilityData, GameEntity target) {
        return runActions(ability, mixinData, abilityData, target);
    }
}
