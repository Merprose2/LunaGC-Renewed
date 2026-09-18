package emu.grasscutter.data.excels;

import emu.grasscutter.data.*;
import lombok.Getter;

/**
 * The order in which the sections of a city take turns to hold ley line outcrops.
 *
 * <p>Only one section of a nation has outcrops on a given day, and the game moves on to the next
 * section the following day - the {@code order} column is that rotation. Nations that are not listed
 * here (Fontaine, Natlan and the other later regions) do not take part and keep all of their camps.
 */
@ResourceType(name = "BlossomSectionOrderExcelConfigData.json")
@Getter
public class BlossomSectionOrderExcelConfigData extends GameResource {
    @Getter(onMethod_ = @Override)
    private int id;

    private int cityId;
    private int sectionId;

    /** Position of this section in its city's rotation, starting at 1. */
    private int order;
}