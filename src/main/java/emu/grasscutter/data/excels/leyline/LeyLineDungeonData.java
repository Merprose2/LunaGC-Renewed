package emu.grasscutter.data.excels.leyline;

import emu.grasscutter.data.GameResource;
import emu.grasscutter.data.ResourceType;
import lombok.Getter;

/**
 * Maps a Ley Line dungeon id (20000-20005) to its prepare/battle gallery pair.
 *
 * <p>id 1: dungeon 20000 -> galleries 83301/83302, id 2: dungeon 20001 -> 83303/83304, id 3:
 * dungeon 20002 -> 83305/83306.
 */
@ResourceType(name = "LeyLineDungeonExcelConfigData.json")
@Getter
public class LeyLineDungeonData extends GameResource {

    @Getter(onMethod_ = @Override)
    private int id;

    private int dungeonId;
    /** Prepare (difficulty selection) gallery id, e.g. 83303. */
    private int HLKOKPADLEH;
    /** Battle gallery id, e.g. 83304. */
    private int LGGKCOFNJAK;

    public int getPrepareGalleryId() {
        return this.HLKOKPADLEH;
    }

    public int getBattleGalleryId() {
        return this.LGGKCOFNJAK;
    }
}
