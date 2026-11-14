package modularis.type.units.modules;

import arc.math.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.graphics.*;

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
        ammoHeal,
        sight,
        focus
    }

    public final Kind kind;
    /** 1 light, 2 medium, 3 heavy. Barrels and breeches are this many cells wide. 0 for the rest. */
    public final int caliber;

    public int hingeSlots = 0;
    public boolean unstable;

    private static final String noise = "#$%&@!?/\\|<>~^*=+0123456789ABCDEFЖЯЮЭЪЫШЩФ";

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

    /* ... */
    static String smear(String text, float keep, int salt){
        long frame = (long)(Time.globalTime / 4f);
        Rand r = new Rand(frame * 31 + salt);
        StringBuilder out = new StringBuilder(text.length());
        for(int i = 0; i < text.length(); i++){
            char c = text.charAt(i);
            out.append(c == ' ' && r.chance(0.7) || r.chance(keep) ? c : noise.charAt(r.random(noise.length() - 1)));
        }
        return out.toString();
    }

    @Override
    public void display(Table table){
        if(!unstable){
            super.display(table);
            return;
        }
        table.label(() -> smear("            ", 0f, 1)).color(Pal.remove).left().row();
        table.label(() -> smear(description, 0.18f, 2)).color(Pal.lightishGray).left().wrap().width(220f).row();
        table.image().color(Pal.gray).height(2f).growX().pad(4f).row();
        stat(table, "Size", w + "x" + h);
        stat(table, "Weight", Strings.autoFixed(weight, 1));
        stat(table, "Power -", Strings.autoFixed(powerUse * 60f, 1) + "/s");
        table.label(() -> smear("Role: ????????????????????????", 0.3f, 3)).color(Pal.lightishGray).left().row();
    }

    @Override
    public void buildStats(Table table){
        if(caliber > 0) stat(table, "Caliber", CannonCaliber.get(caliber).title);
        if(hingeSlots > 0) stat(table, "Hinge slots +", hingeSlots + " weapon");
        stat(table, "Role", switch(kind){
            case barrel -> "Stack in front of a breech of the same caliber. Longer hits harder, flies faster, spreads less";
            case breech -> "Fires the barrel column in front of it. Without one, barrels are dead weight";
            case armor -> "Makes the hinge harder to blow off";
            case autoloader -> "[lime]-20%[] reload each, down to half";
            case stabilizer -> "Tighter spread";
            case motor -> "[lime]+30%[] traverse each";
            case ammoHE -> "HE: weaker hit, big splash. Whichever ammo has the most racks wins";
            case ammoAP -> "AP: harder hit, punches through. Whichever ammo has the most racks wins";
            case ammoHeal -> "Heal: half the damage to enemies, and mends damaged allied units and blocks it hits. Whichever ammo has the most racks wins";
            case sight -> "[lime]+" + Math.round(CannonSpec.sightRange * 100f) + "%[] range each, up to [lime]+"
                + Math.round((CannonSpec.maxRangeScale - 1f) * 100f) + "%[]";
            case focus -> "";
        });
    }
}
