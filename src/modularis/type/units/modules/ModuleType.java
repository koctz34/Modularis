package modularis.type.units.modules;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.entities.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.Layer;
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
    /** Structural armor this module contributes to the unit. */
    public float armor = 0f;
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

    /** Scales the machine's total health. */
    public float healthMultiplier = 1f;
    /** Scales the damage every weapon on the machine deals. */
    public float damageMultiplier = 1f;
    /** Scales weapon fire rate (>1 = reloads faster). */
    public float reloadMultiplier = 1f;
    /** Scales the machine's top speed. */
    public float speedMultiplier = 1f;
    /** Scales the machine's total weight (<1 = lighter, so faster). */
    public float weightMultiplier = 1f;
    /** Scales power produced by the engines. */
    public float powerProductionMultiplier = 1f;
    /** Scales power drawn by everything on the machine. */
    public float powerUseMultiplier = 1f;
    /** Scales the drivetrain's hauling capacity. */
    public float haulMultiplier = 1f;
    /** Cargo capacity of the machine. */
    public int cargoCapacity = 0;

    /**
     * Build speed this module grants the machine. 0 = grants none.
     * A machine with no build module cannot build at all; several stack.
     */
    public float buildSpeed = 0f;

    /** Effect emitted every so often while the module is running. Null = none. */
    public @Nullable Effect ambientEffect;
    /** Chance per tick to emit {@link #ambientEffect}. */
    public float ambientChance = 0.1f;
    /** Colour handed to the effect. */
    public Color ambientColor = Color.white;

    protected TextureRegion region, cellRegion, topRegion;
    /** Cumulative tone masks, darkest first. Resolved together, so null = not looked up yet. */
    protected TextureRegion[] paintRegions;

    public ModuleType(String name){
        this.name = name;
        this.localizedName = name;
    }

    public TextureRegion region(){
        if(region == null && Core.atlas != null) region = Core.atlas.find("modularis-" + name);
        return region;
    }

    public TextureRegion topRegion(){
        if(topRegion == null && Core.atlas != null) topRegion = Core.atlas.find("modularis-" + name + "-top");
        return topRegion;
    }

    public TextureRegion cellRegion(){
        if(cellRegion == null && Core.atlas != null) cellRegion = Core.atlas.find("modularis-" + name + "-cell");
        return cellRegion;
    }

    public TextureRegion bodyRegion(){
        return region();
    }

    public String bodySpriteName(){
        return name;
    }

    public TextureRegion paintRegion(int tone){
        if(paintRegions == null){
            if(Core.atlas == null) return null;
            String base = "modularis-" + bodySpriteName() + "-paint";
            paintRegions = new TextureRegion[]{
                Core.atlas.find(base + "0"), Core.atlas.find(base + "1"), Core.atlas.find(base + "2")
            };
        }
        return paintRegions[tone];
    }

    public boolean paintable(){
        TextureRegion r = paintRegion(0);
        return r != null && r.found();
    }

    public int cells(){
        return w * h;
    }

    // ---- rendering ----
    public void drawBody(@Nullable Unit unit, PlacedModule placed, float x, float y, float w, float h, float rotation){
        Draw.color(Color.white);
        Draw.rect(bodyRegion(), x, y, w, h, rotation);
    }

    public void drawPaint(@Nullable Unit unit, PlacedModule placed, float x, float y, float w, float h, float rotation, Color tint){
        if(tint.equals(Color.white) || !paintable()) return;

        PaintRamp ramp = PaintRamp.of(tint);
        for(int tone = 0; tone < 3; tone++){
            TextureRegion r = paintRegion(tone);
            if(r == null || !r.found()) continue;
            Draw.color(ramp.tone(tone));
            Draw.rect(r, x, y, w, h, rotation);
        }
        Draw.color();
    }

    public void drawTop(@Nullable Unit unit, PlacedModule placed, float x, float y, float w, float h, float rotation){
        if(topRegion != null) Draw.rect(topRegion(), x, y, w, h, rotation);
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
        if(armor > 0) stat(table, "Armor", Strings.autoFixed(armor, 0));
        if(powerProduction > 0) stat(table, "Power +", Strings.autoFixed(powerProduction * 60f, 1) + "/s");
        if(powerUse > 0) stat(table, "Power -", Strings.autoFixed(powerUse * 60f, 1) + "/s");
        if(limit >= 0) stat(table, "Max per machine", "" + limit);
        if(slot != SlotType.none) stat(table, "Slots needed", slotCost + " " + slot.title.toLowerCase());
        if(cargoCapacity > 0) stat(table, "Cargo capacity", Strings.autoFixed(cargoCapacity, 0));
        if(buildSpeed > 0) stat(table, "Build speed", "[lime]x" + Strings.autoFixed(buildSpeed, 2) + "[]");

        mult(table, "Health", healthMultiplier, false);
        mult(table, "Damage", damageMultiplier, false);
        mult(table, "Fire rate", reloadMultiplier, false);
        mult(table, "Speed", speedMultiplier, false);
        mult(table, "Weight", weightMultiplier, true);
        mult(table, "Power output", powerProductionMultiplier, false);
        mult(table, "Power draw", powerUseMultiplier, true);
        mult(table, "Haul capacity", haulMultiplier, false);

        buildStats(table);
    }

    public void buildStats(Table table){
    }

    protected void mult(Table table, String key, float value, boolean lowerIsBetter){
        if(Mathf.equal(value, 1f, 0.001f)) return;
        boolean good = lowerIsBetter ? value < 1f : value > 1f;
        stat(table, key, (good ? "[lime]" : "[scarlet]") + "x" + Strings.autoFixed(value, 2) + "[]");
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
