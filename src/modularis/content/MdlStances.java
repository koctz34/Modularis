package modularis.content;

import mindustry.ai.*;

public class MdlStances{
    /** RTS toggle: while on, the AI leaves this machine's active modules alone. */
    public static UnitStance holdActives;

    public static void load(){
        holdActives = new UnitStance("hold-actives", "lock", null){
            @Override
            public String localized(){
                return "Hold Abilities";
            }
        };
    }
}
