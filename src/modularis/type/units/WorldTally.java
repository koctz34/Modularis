package modularis.type.units;

import arc.*;
import mindustry.gen.*;

import static mindustry.Vars.*;

public class WorldTally{
    static final String wrecks = "mdl.t0", blasts = "mdl.t1", tuned = "mdl.t2", kept = "mdl.k";

    public static int get(String key){
        return state.rules.tags.getInt(key, 0);
    }

    static void add(String key){
        if(net.client()) return;
        state.rules.tags.put(key, String.valueOf(get(key) + 1));
        if(net.server()) Call.setRules(state.rules);
    }

    public static void wreck(){
        add(wrecks);
    }

    public static void blast(){
        add(blasts);
    }

    public static boolean settled(){
        return get(wrecks) >= 10 && get(blasts) >= 3;
    }

    public static boolean tuned(){
        return get(tuned) > 0;
    }

    public static boolean whole(){
        return Core.settings.getBool(kept, false);
    }

    public static void keep(){
        Core.settings.put(kept, true);
        Core.settings.forceSave();
    }

    public static void tune(){
        state.rules.tags.put(tuned, "1");
    }
}
