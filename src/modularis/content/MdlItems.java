package modularis.content;

import arc.graphics.*;
import arc.struct.*;
import mindustry.type.*;

public class MdlItems {
    public static Item
    zink;

    public static void load(){
        zink = new Item("zink", Color.valueOf("b0b2ba")) {{
            localizedName = "Zincum";
            description = "Fundamentum futurae tuae amplificationis.";
            cost = 1;
        }};
    }
}
