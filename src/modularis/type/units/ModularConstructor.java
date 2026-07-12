package modularis.type.units;

import arc.*;
import arc.audio.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.*;
import mindustry.ai.*;
import mindustry.ctype.*;
import mindustry.entities.*;
import mindustry.entities.units.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.io.*;
import mindustry.logic.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.blocks.*;
import mindustry.world.blocks.payloads.*;
import mindustry.world.blocks.units.UnitBlock;
import mindustry.world.consumers.*;
import mindustry.world.meta.*;

import java.util.*;

import static mindustry.Vars.*;

public class ModularConstructor extends UnitBlock{

    public Sound createSound = Sounds.unitCreate;
    public float createSoundVolume = 1f;

    public ModularConstructor(String name){
        super(name);
        update = true;
        hasPower = true;
        hasItems = true;
        solid = false;
        configurable = true;
        outputsPayload = true;
        rotate = true;
        regionRotated1 = 1;
        ambientSound = Sounds.loopUnitBuilding;
        ambientSoundVolume = 0.09f;
    }

    @Override
    public TextureRegion[] icons(){
        return new TextureRegion[]{region, outRegion};
    }

    @Override
    public void drawPlanRegion(BuildPlan plan, Eachable<BuildPlan> list){
        Draw.rect(region, plan.drawx(), plan.drawy());
        Draw.rect(outRegion, plan.drawx(), plan.drawy(), plan.rotation * 90);
    }
    
    public class ModularConstructorBuild extends UnitBuild{

        @Override
        public void draw(){
            Draw.rect(region, x, y);
            Draw.rect(outRegion, x, y, rotdeg());

            Draw.z(Layer.blockOver);

            payRotation = rotdeg();
            drawPayload();
        }
    }
}