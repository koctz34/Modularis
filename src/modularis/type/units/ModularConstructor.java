package modularis.type.units;

import arc.audio.*;
import arc.graphics.g2d.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.entities.units.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;
import mindustry.world.blocks.units.*;

import modularis.content.*;
import modularis.type.ui.*;

import static mindustry.Vars.*;

public class ModularConstructor extends UnitBlock{
    public Sound createSound = Sounds.unitCreate;
    public float createSoundVolume = 1f;

    public ModularConstructor(String name){
        super(name);
        update = true;
        hasPower = true;
        hasItems = false;
        solid = true;
        configurable = true;
        outputsPayload = false;
        rotate = false;
        ambientSound = Sounds.loopUnitBuilding;
        ambientSoundVolume = 0.09f;
    }

    @Override
    public TextureRegion[] icons(){
        return new TextureRegion[]{region};
    }

    @Override
    public void drawPlanRegion(BuildPlan plan, Eachable<BuildPlan> list){
        Draw.rect(region, plan.drawx(), plan.drawy());
    }

    public class ModularConstructorBuild extends UnitBuild{
        public ModularDesign design = new ModularDesign();

        @Override
        public void buildConfiguration(Table table){
            table.button("Open Editor", Icon.pencil, () -> {
                deselect();
                ModularConstructorEditor.dialog.show(design, () -> configure(design.serialize()));
            }).size(220f, 50f).row();

            table.button("Create Machine", Icon.add, () -> configure(Boolean.TRUE))
                .size(220f, 50f).disabled(b -> design.isEmpty());
        }

        @Override
        public void configured(Unit builder, Object value){
            if(value instanceof String s){
                design = ModularDesign.read(s);
            }else if(value instanceof Boolean){
                if(!net.client()) createMachine();
            }
        }

        public void createMachine(){
            if(design.isEmpty()) return;

            //spawn clear of the (solid) block footprint so the tank isn't stuck inside it
            float sy = y - (size / 2f + 1f) * tilesize;
            Unit unit = MdlUnits.modularUnit.spawn(team, x, sy);
            if(unit != null){
                ModularUnitType.assign(unit, design);
                createSound.at(x, sy, 1f, createSoundVolume);
            }
        }

        @Override
        public void draw(){
            Draw.rect(region, x, y);
            Draw.z(Layer.blockOver);
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.str(design.serialize());
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            design = ModularDesign.read(read.str());
        }
    }
}
