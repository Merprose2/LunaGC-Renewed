package emu.grasscutter.data.binout;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.game.world.Position;
import java.util.List;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class HomeworldDefaultSaveData {

    @SerializedName(
            value = "homeBlockLists",
            alternate = {
                "blockArrangementInfoList",
                "PKACPHDGGEI",
                "AKOLOBLHDFK",
                "KFHBFNPDJBE"
            })
    List<HomeBlock> homeBlockLists;

    @SerializedName(
            value = "bornPos",
            alternate = {"MINCKHBNING", "MBICDPDEKDM", "IJNPADKGNKE"})
    Position bornPos;

    @SerializedName(
            value = "bornRot",
            alternate = {"EJJIOJKFKCO", "IPIIGEMFLHK"})
    Position bornRot;

    @SerializedName(
            value = "djinPos",
            alternate = {"djinnPos", "CJAKHCIFHNP", "HHOLBNPIHEM"})
    Position djinPos;

    @SerializedName(
            value = "mainhouse",
            alternate = {"mainHouse", "AMDNOHPGKMI", "KNHCJKHCOAN"})
    HomeFurniture mainhouse;

    @SerializedName(
            value = "doorLists",
            alternate = {"doorList", "BHCPEAOPIDC", "NIHOJFEKFPG"})
    List<HomeFurniture> doorLists;

    @SerializedName(
            value = "stairLists",
            alternate = {"stairList", "AABEPENIFLN", "EPGELGEFJFK"})
    List<HomeFurniture> stairLists;

    @SerializedName(value = "tmpVersion", alternate = {"version"})
    int tmpVersion;

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class HomeBlock {

        @SerializedName(
                value = "blockId",
                alternate = {"PGDPDIDJEEL", "ANICBLBOBKD", "FGIJCELCGFI"})
        int blockId;

        @SerializedName(
                value = "furnitures",
                alternate = {
                    "deployFurniureList",
                    "deployFurnitureList",
                    "NCIMIKKFLOH",
                    "BEAPOFELABD"
                })
        List<HomeFurniture> furnitures;

        @SerializedName(
                value = "persistentFurnitures",
                alternate = {
                    "persistentFurnitureList",
                    "GJGNLIINBGB",
                    "MLIODLGDFHJ"
                })
        List<HomeFurniture> persistentFurnitures;
    }

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class HomeFurniture {

        @SerializedName(
                value = "id",
                alternate = {
                    "furnitureId",
                    "KMAAJJHPNBA",
                    "FFLCGFGGGND",
                    "ENHNGKJBJAB"
                })
        int id;

        @SerializedName(
                value = "pos",
                alternate = {
                    "spawnPos",
                    "JFKAHNCPDME",
                    "BPCGGBKIAMG",
                    "NGIEEIOLPPO"
                })
        Position pos;

        @SerializedName(
                value = "rot",
                alternate = {"spawnRot", "LKCKOOGFDBM", "HEOCEHKEBFM"})
        Position rot;

        @SerializedName(value = "parentFurnitureIndex", alternate = {"parentIndex"})
        int parentFurnitureIndex;

        @SerializedName(value = "version", alternate = {"tmpVersion"})
        int version;
    }
}
