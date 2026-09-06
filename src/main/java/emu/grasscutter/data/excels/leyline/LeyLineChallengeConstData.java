package emu.grasscutter.data.excels.leyline;

import emu.grasscutter.data.GameResource;
import emu.grasscutter.data.ResourceType;
import lombok.Getter;

/** Global constants for the Stygian Onslaught (Ley Line Challenge) mode. */
@ResourceType(name = "LeyLineChallengeConstExcelConfigData.json")
@Getter
public class LeyLineChallengeConstData extends GameResource {

    @Getter(onMethod_ = @Override)
    private int id; // == activityID (5269)

    private int activityID;
    /** Gadget id of the overworld Stygian Ley Line interaction point (73051004). */
    private int LAAJACLDMML;
    private int BDLJGJPACBA;
    private int BPEGCFCKEEC;
    private int CAHELFMOIDN;
    private int CNFMIGIMAHG;
    private int ECFICFMCJIN;
    private int EIBCKDNDGHN;
    private int ELFAGNMMBCI;
    private int FFBNJJNIECA;
    private int FIOMOIAPMCI;
    private int GHIIPABMJHO;
    private int HNLCBAEJPPP;
    private int IPJEHDCKCLJ;
    private int JPAMFOEGEMK;
    private int KCNNLLHDKIP;
    private int LOMAGDFKPGJ;
    private int OMJLPFOGDJA;
    private int OPMDFFJKDAP;

    @Override
    public void onLoad() {
        this.id = this.activityID;
    }

    public int getActivityId() {
        return this.activityID;
    }

    public int getOverworldGadgetId() {
        return this.LAAJACLDMML;
    }
}
