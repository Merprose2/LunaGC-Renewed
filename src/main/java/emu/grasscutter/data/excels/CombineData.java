package emu.grasscutter.data.excels;

import emu.grasscutter.data.GameResource;
import emu.grasscutter.data.ResourceType;
import emu.grasscutter.data.common.ItemParamData;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@ResourceType(name = "CombineExcelConfigData.json")
public class CombineData extends GameResource {
    private int combineId;
    private int playerLevel;
    private boolean isDefaultShow;
    private int combineType;
    private int subCombineType;
    private int resultItemId;
    private int resultItemCount;
    private int scoinCost;
    private List<ItemParamData> randomItems;
    private List<ItemParamData> materialItems;
    private String recipeType;

    @Override
    public int getId() {
        return combineId;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        randomItems = cleanItems(randomItems);
        materialItems = cleanItems(materialItems);
    }

    private static List<ItemParamData> cleanItems(List<ItemParamData> items) {
        if (items == null) {
            return new ArrayList<>();
        }

        return items.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getId() > 0)
                .collect(Collectors.toList());
    }

    public int getCombineId() {
        return combineId;
    }

    public int getPlayerLevel() {
        return playerLevel;
    }

    public boolean isDefaultShow() {
        return isDefaultShow;
    }

    public int getCombineType() {
        return combineType;
    }

    public int getSubCombineType() {
        return subCombineType;
    }

    public int getResultItemId() {
        return resultItemId;
    }

    public int getResultItemCount() {
        return resultItemCount;
    }

    public int getScoinCost() {
        return scoinCost;
    }

    public List<ItemParamData> getRandomItems() {
        return randomItems;
    }

    public List<ItemParamData> getMaterialItems() {
        return materialItems;
    }

    public String getRecipeType() {
        return recipeType;
    }
}