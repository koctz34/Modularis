package modularis.type.units.modules;

import arc.scene.ui.layout.*;

import modularis.type.units.*;

public class CannonPart extends ModuleType{
    public enum Kind{
        barrel,
        breech,
        armor,
        autoloader,
        stabilizer,
        motor,
        ammoHE,
        ammoAP,
        sight
    }

    public final Kind kind;
    /** 1 light, 2 medium, 3 heavy. Barrels and breeches are this many cells wide. 0 for the rest. */
    public final int caliber;

    public CannonPart(String name, Kind kind, int caliber){
        super(name);
        this.kind = kind;
        this.caliber = caliber;
        category = switch(kind){
            case barrel -> ModuleCategory.barrel;
            case breech -> ModuleCategory.breech;
            case armor -> ModuleCategory.cannonArmor;
            default -> ModuleCategory.cannonGear;
        };
    }

    @Override
    public void buildStats(Table table){
        if(caliber > 0) stat(table, "Caliber", CannonCaliber.get(caliber).title);
        stat(table, "Role", switch(kind){
            case barrel -> "Stack in front of a breech of the same caliber. Longer hits harder, flies faster, spreads less";
            case breech -> "Fires the barrel column in front of it. Without one, barrels are dead weight";
            case armor -> "Makes the hinge harder to blow off";
            case autoloader -> "[lime]-20%[] reload each, down to half";
            case stabilizer -> "Tighter spread";
            case motor -> "[lime]+30%[] traverse each";
            case ammoHE -> "HE: weaker hit, big splash. Whichever of HE or AP has more racks wins";
            case ammoAP -> "AP: harder hit, punches through. Whichever of HE or AP has more racks wins";
            case sight -> "[lime]+" + Math.round(CannonSpec.sightRange * 100f) + "%[] range each, up to [lime]+"
                + Math.round((CannonSpec.maxRangeScale - 1f) * 100f) + "%[]";
        });
    }
}
