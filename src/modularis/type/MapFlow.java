package modularis.type;

import arc.*;
import arc.files.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.io.*;
import mindustry.maps.*;
import mindustry.net.*;
import mindustry.world.*;

import modularis.*;
import modularis.type.ui.*;

import static mindustry.Vars.*;

public class MapFlow{
    public static final String packet = "mdl.flow";
    private static @Nullable Tiles running;

    public static void begin(){
        if(running != null || net.client() || !state.isGame()) return;
        running = world.tiles;

        String planet = (state.rules.planet == Planets.erekir ? Planets.erekir : Planets.serpulo).name;
        if(net.server()) Call.clientPacketReliable(packet, planet);
        if(!headless) PlanetOverlay.play(planet);
        Timer.schedule(() -> Core.app.post(MapFlow::arrive), PlanetOverlay.length);
    }

    public static void received(String planet){
        if(!headless) PlanetOverlay.play(planet);
    }

    private static void arrive(){
        Tiles was = running;
        running = null;
        if(was != world.tiles || !state.isGame()) return;

        Map map = maps.defaultMaps().random();
        if(map == null) return;
        Rules rules = map.rules();
        rules.tags.put(VeilOverlay.mark, "1");

        if(net.server()){
            WorldReloader reloader = new WorldReloader();
            reloader.begin();
            world.loadMap(map, rules);
            state.rules = rules;
            logic.play();
            reloader.end();
        }else{
            control.playMap(map, rules);
        }
    }

    public static void watch(){
        if(!headless && state.isGame() && state.rules.tags.containsKey(VeilOverlay.mark)) VeilOverlay.keep();
    }
}
