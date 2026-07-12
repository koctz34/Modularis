package modularis.type.units.modules;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.game.*;
import mindustry.gen.*;

import modularis.type.units.*;

public class ModuleType{
    /** Internal/atlas name. Sprite is looked up as {@code modularis-<name>}. */
    public final String name;
    /** Human readable name shown in the editor. */
    public String localizedName;
    /** Short description shown in the editor tooltip. */
    public String description = "";
    /** Category used to group the module in the parts list. */
    public ModuleCategory category = ModuleCategory.base;

    /** Size in grid cells. */
    public int w = 1, h = 1;

    /** Mass of the module. Higher total mass slows the unit down. */
    public float weight = 1f;
    /** Structural health this module contributes to the unit. */
    public float health = 120f;
    /** Power produced each tick. */
    public float powerProduction = 0f;
    /** Power drained each tick while active. */
    public float powerUse = 0f;

    /** Max number of this module allowed on one machine. -1 = unlimited. */
    public int limit = -1;

    /** Which slot pool this module consumes. {@link SlotType#none} = needs no slots. */
    public SlotType slot = SlotType.none;
    /** How many free slots of {@link #slot} this module requires. */
    public int slotCost = 1;

    protected TextureRegion region, cellRegion;

    public ModuleType(String name){
        this.name = name;
        this.localizedName = name;
    }

    public TextureRegion region(){
        if(region == null) region = Core.atlas.find("modularis-" + name);
        return region;
    }

    public TextureRegion cellRegion(){
        if(cellRegion == null) cellRegion = Core.atlas.find("modularis-" + name + "-cell");
        return cellRegion;
    }

    public TextureRegion bodyRegion(){
        return region();
    }

    public int cells(){
        return w * h;
    }

    // ---- rendering ----
    public void drawBody(@Nullable Unit unit, PlacedModule placed, float x, float y, float w, float h, float rotation){
        Draw.color(Color.white);
        Draw.rect(bodyRegion(), x, y, w, h, rotation);
    }

    public void drawTop(@Nullable Unit unit, PlacedModule placed, float x, float y, float w, float h, float rotation){
    }

    protected Color teamColor(@Nullable Unit unit){
        return unit != null ? unit.team().color : Team.sharded.color;
    }

    public void display(Table table){
        table.add(localizedName).color(category.color).left().row();
        if(!description.isEmpty()){
            table.add(description).color(mindustry.graphics.Pal.lightishGray).left().wrap().width(220f).row();
        }
        table.image().color(mindustry.graphics.Pal.gray).height(2f).growX().pad(4f).row();

        stat(table, "Size", w + "x" + h);
        stat(table, "Weight", Strings.autoFixed(weight, 1));
        stat(table, "Health", Strings.autoFixed(health, 0));
        if(powerProduction > 0) stat(table, "Power +", Strings.autoFixed(powerProduction * 60f, 1) + "/s");
        if(powerUse > 0) stat(table, "Power -", Strings.autoFixed(powerUse * 60f, 1) + "/s");
        if(limit >= 0) stat(table, "Max per machine", "" + limit);
        if(slot != SlotType.none) stat(table, "Slots needed", slotCost + " " + slot.title.toLowerCase());
        buildStats(table);
    }

    public void buildStats(Table table){
    }

    protected void stat(Table table, String key, String value){
        table.table(t -> {
            t.left();
            t.add(key + ":").color(mindustry.graphics.Pal.lightishGray).left().padRight(6f);
            t.add(value).left().growX();
        }).growX().left().row();
    }

    @Override
    public String toString(){
        return "ModuleType{" + name + "}";
    }
}
