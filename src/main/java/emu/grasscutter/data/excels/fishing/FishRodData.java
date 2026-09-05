package emu.grasscutter.data.excels.fishing;

import emu.grasscutter.data.*;
import lombok.Getter;

@ResourceType(name = "FishRodExcelConfigData.json")
@Getter
public class FishRodData extends GameResource {
    private int id;
    private int cityId;
    private float attackMag;
    private float baseAttack;

    @Override
    public int getId() {
        return this.id;
    }
}