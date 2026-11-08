package modularis.type.units.modules;

import arc.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.gen.*;

import modularis.content.*;
import modularis.type.units.*;

public class ModulHarpoon extends ModulTurret{
    public ModulHarpoon(String name){
        super(name);
    }

    @Override
    public void drawTop(Unit unit, PlacedModule placed, float x, float y, float w, float h, float rotation){
        super.drawTop(unit, placed, x, y, w, h, rotation);

        if(unit != null || !(weapon instanceof HarpoonWeapon gun)) return;

        TextureRegion head = Core.atlas.find("modularis-harpoon");
        float cell = w / this.w;
        float size = cell * head.width / ModularUnitType.spritePx;
        float along = gun.loadedY / ModularUnitType.cellWorld() * cell;
        float hx = x + Angles.trnsx(rotation, 0f, along), hy = y + Angles.trnsy(rotation, 0f, along);
        float off = Math.max(cell * 0.19f, 1f);

        Draw.color(MdlUnits.modularUnit.outlineColor);
        for(int i = 0; i < 4; i++){
            Draw.rect(head, hx + Angles.trnsx(i * 90f, off), hy + Angles.trnsy(i * 90f, off), size, size, rotation);
        }
        Draw.color();
        Draw.rect(head, hx, hy, size, size, rotation);
    }

    @Override
    public void buildStats(Table table){
        super.buildStats(table);
        if(weapon instanceof HarpoonWeapon gun && gun.bullet != null){
            stat(table, "Range", Strings.autoFixed(gun.bullet.speed * gun.bullet.lifetime / 8f, 1) + " tiles");
            stat(table, "Hook holds", Strings.autoFixed(gun.holdTime / 60f, 0) + "s");
        }
    }
}
