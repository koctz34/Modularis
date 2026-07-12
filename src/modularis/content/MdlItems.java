package modularis.content;

import arc.graphics.*;
import arc.struct.*;
import mindustry.type.*;

public class MdlItems {
    public static Item
    zinc;

    public static void load(){
        zinc = new Item("zinc", Color.valueOf("63a42e")) {{
            localizedName = "Zincum";
            description = "Fundamentum futurae tuae amplificationis.";
            cost = 1;
        }};
    }
}

