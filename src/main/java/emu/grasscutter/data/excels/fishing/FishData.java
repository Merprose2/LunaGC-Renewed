package emu.grasscutter.data.excels.fishing;

import emu.grasscutter.data.*;
import lombok.Getter;

@ResourceType(name = "FishExcelConfigData.json")
@Getter
public class FishData extends GameResource {
    private int id;
    private int monsterId;
    private int itemId;
    private int rewardId;
    private float attractRange;
    private float fleeRange;
    private int hp;
    private int initPose;
    private float biteTimeout;
    private float[] feelerTimes;

    @Override
    public int getId() {
        return this.id;
    }
}