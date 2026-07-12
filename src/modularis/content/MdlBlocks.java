package modularis.content;

import arc.graphics.*;
import arc.math.*;
import arc.struct.*;
import mindustry.*;
import mindustry.content.Items;
import mindustry.content.UnitTypes;
import mindustry.entities.*;
import mindustry.entities.abilities.*;
import mindustry.entities.bullet.*;
import mindustry.entities.effect.*;
import mindustry.entities.part.DrawPart.*;
import mindustry.entities.part.*;
import mindustry.entities.pattern.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.type.unit.*;
import mindustry.world.*;
import mindustry.world.blocks.*;
import mindustry.world.blocks.campaign.*;
import mindustry.world.blocks.defense.*;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.blocks.distribution.*;
import mindustry.world.blocks.environment.*;
import mindustry.world.blocks.heat.*;
import mindustry.world.blocks.legacy.*;
import mindustry.world.blocks.liquid.*;
import mindustry.world.blocks.logic.*;
import mindustry.world.blocks.payloads.*;
import mindustry.world.blocks.power.*;
import mindustry.world.blocks.production.*;
import mindustry.world.blocks.sandbox.*;
import mindustry.world.blocks.storage.*;
import mindustry.world.blocks.units.*;
import mindustry.world.consumers.*;
import mindustry.world.draw.*;
import mindustry.world.meta.*;

import modularis.type.units.*;

import static mindustry.Vars.*;
import static mindustry.type.ItemStack.*;
import static modularis.content.MdlItems.*;

public class MdlBlocks{
    public static Block

    // WALLS
    zinkWall,

    // UNITS
    modularConstructor

    ;

    public static void load(){
        // WALLS
        zinkWall = new Wall("zinc-wall"){{
            requirements(Category.defense, with(zinc, 6));
            localizedName = "Zinc Wall";
            description = "Wall made of zinc.";
            health = 100 * 4;
        }};

        // UNITS
        modularConstructor = new ModularConstructor("creative-constructor"){{
            requirements(Category.units, BuildVisibility.sandboxOnly, with());
            localizedName = "Modular Constructor";
            description = "Creative test bench: design a modular machine and spawn it into the world.";
            size = 3;
            health = 560;
        }};
    }
}
