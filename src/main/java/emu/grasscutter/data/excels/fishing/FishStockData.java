package emu.grasscutter.data.excels.fishing;

import emu.grasscutter.data.*;
import lombok.Getter;
import java.util.Map;

@ResourceType(name = "FishStockExcelConfigData.json")
@Getter
public class FishStockData extends GameResource {
    private int id;
    private String type; // FISH_STOCK_TYPE_DAY, FISH_STOCK_TYPE_NIGHT, FISH_STOCK_TYPE_ANY
    private Map<String, Integer> fishWeight;

    @Override
    public int getId() {
        return this.id;
    }
}