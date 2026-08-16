package emu.grasscutter.server.packet.recv;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import emu.grasscutter.net.proto.ItemParamOuterClass.ItemParam;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class WeaponUpgradeReqCompat {
    /*
     * 6.6 proto archaeology candidates:
     *
     * WeaponUpgradeReq candidate:
     *   CmdID: 8208
     *   target_weapon_guid: field 12
     *   food_weapon_guid_list: field 9
     *   item_param_list: field 10
     *
     * CalcWeaponUpgradeReturnItemsReq candidate:
     *   CmdID: 3748
     *   target_weapon_guid: field 1
     *   food_weapon_guid_list: field 5
     *   item_param_list: field 10
     */
    private static final int[] TARGET_GUID_FIELDS = {12, 1, 11};
    private static final int[] FOOD_GUID_FIELDS = {9, 5, 14};
    private static final int[] ITEM_PARAM_FIELDS = {10, 2, 15};

    private final long targetWeaponGuid;
    private final List<Long> foodWeaponGuidList;
    private final List<ItemParam> itemParamList;

    private WeaponUpgradeReqCompat(
            long targetWeaponGuid,
            List<Long> foodWeaponGuidList,
            List<ItemParam> itemParamList
    ) {
        this.targetWeaponGuid = targetWeaponGuid;
        this.foodWeaponGuidList = foodWeaponGuidList;
        this.itemParamList = itemParamList;
    }

    long getTargetWeaponGuid() {
        return targetWeaponGuid;
    }

    List<Long> getFoodWeaponGuidList() {
        return foodWeaponGuidList;
    }

    List<ItemParam> getItemParamList() {
        return itemParamList;
    }

    static WeaponUpgradeReqCompat parse(byte[] payload) throws IOException {
        CodedInputStream input = CodedInputStream.newInstance(payload);

        long targetWeaponGuid = 0L;
        List<Long> foodWeaponGuidList = new ArrayList<>();
        List<ItemParam> itemParamList = new ArrayList<>();

        while (!input.isAtEnd()) {
            int tag = input.readTag();

            if (tag == 0) {
                break;
            }

            int fieldNumber = WireFormat.getTagFieldNumber(tag);
            int wireType = WireFormat.getTagWireType(tag);

            if (contains(TARGET_GUID_FIELDS, fieldNumber)
                    && wireType == WireFormat.WIRETYPE_VARINT) {
                targetWeaponGuid = input.readUInt64();
                continue;
            }

            if (contains(FOOD_GUID_FIELDS, fieldNumber)) {
                if (wireType == WireFormat.WIRETYPE_VARINT) {
                    foodWeaponGuidList.add(input.readUInt64());
                    continue;
                }

                if (wireType == WireFormat.WIRETYPE_LENGTH_DELIMITED) {
                    int length = input.readRawVarint32();
                    int oldLimit = input.pushLimit(length);

                    while (input.getBytesUntilLimit() > 0) {
                        foodWeaponGuidList.add(input.readUInt64());
                    }

                    input.popLimit(oldLimit);
                    continue;
                }
            }

            if (contains(ITEM_PARAM_FIELDS, fieldNumber)
                    && wireType == WireFormat.WIRETYPE_LENGTH_DELIMITED) {
                int length = input.readRawVarint32();
                itemParamList.add(ItemParam.parseFrom(input.readRawBytes(length)));
                continue;
            }

            if (!input.skipField(tag)) {
                break;
            }
        }

        return new WeaponUpgradeReqCompat(targetWeaponGuid, foodWeaponGuidList, itemParamList);
    }

    private static boolean contains(int[] values, int value) {
        for (int v : values) {
            if (v == value) {
                return true;
            }
        }

        return false;
    }
}