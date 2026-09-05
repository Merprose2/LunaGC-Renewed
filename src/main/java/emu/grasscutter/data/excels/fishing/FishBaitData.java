package emu.grasscutter.data.excels.fishing;

import emu.grasscutter.data.*;
import lombok.Getter;

@ResourceType(name = "FishBaitExcelConfigData.json")
@Getter
public class FishBaitData extends GameResource {
    private int id;

    @Override
    public int getId() {
        return this.id;
    }
}