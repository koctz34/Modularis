package modularis.type.units;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.input.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import modularis.content.*;
import modularis.type.units.modules.*;

import static mindustry.Vars.*;

public class TowInteraction{
    public static final String packetName = "modularis-tow-link";
    public static final float selectRadius = 6f;
    public static final float maxLinkLength = 48f;

    // ---- rope physics; numbers tuned against scratch sims of typical machine masses ----
    public static final float ropeDamping = 0.15f;
    public static final float breakStretch = 16f;
    public static final float snapEffectStrain = 0.5f;

    private static final Color ropeColor = Color.valueOf("242424"), strainedColor = Color.valueOf("e8502f");

    private static final Seq<TowLink> links = new Seq<>();
    private static final Vec2 sourcePos = new Vec2(), targetPos = new Vec2();
    private static InputProcessor processor;
    private static ModularUnitEntity selectedUnit;
    private static PlacedModule selectedTow;

    public static void installInput(){
        if(headless || Core.input == null) return;
        if(processor != null && Core.input.getInputProcessors().contains(processor)) return;

        processor = new InputProcessor(){
            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, KeyCode button){
                if(button != KeyCode.mouseLeft || player == null || player.dead()) return false;
                Vec2 mouse = Core.input.mouseWorld(screenX, screenY);

                if(selectedUnit == null){
                    if(!(player.unit() instanceof ModularUnitEntity unit)) return false;
                    PlacedModule tow = towUnderCursor(unit, mouse.x, mouse.y);
                    if(tow == null) return false;
                    selectedUnit = unit;
                    selectedTow = tow;
                }else{
                    TowEnd end = findTow(mouse.x, mouse.y, selectedUnit);
                    if(end != null && selectedUnit.team == end.unit.team){
                        toggleLink(selectedUnit, selectedTow, end.unit, end.tow);
                    }
                    clearSelection();
                }

                player.shooting = false;
                return true;
            }
        };
        Core.input.getInputProcessors().insert(0, processor);
    }

    public static void update(){
        if(!state.isGame()) return;
        installInput();
        restoreLinks();
        for(int i = links.size - 1; i >= 0; i--){
            TowLink link = links.get(i);
            if(!link.valid()){
                //a client learns about a snap only from the slots emptying out
                if(net.client() && link.strain >= snapEffectStrain) snapEffect(link);
                removeLink(link);
            }else if(net.client()){
                link.readStrain();
            }
        }
        if(!net.client() && !state.isPaused()){
            for(int i = links.size - 1; i >= 0; i--){
                TowLink link = links.get(i);
                link.updatePhysics();
                if(link.strain >= 1f){
                    snapEffect(link);
                    removeLink(link);
                }
            }
        }
        if(selectedUnit != null && (!selectedUnit.isAdded() || player == null || player.unit() != selectedUnit)) clearSelection();
    }

    public static void draw(){
        if(headless || !state.isGame()) return;
        Draw.z(Layer.overlayUI);

        for(TowLink link : links){
            link.positions(sourcePos, targetPos);
            drawConnected(sourcePos.x, sourcePos.y, targetPos.x, targetPos.y, link.length, link.strain);
        }

        if(selectedUnit != null && selectedTow != null){
            selectedUnit.modulePos(selectedTow, sourcePos);
            Drawf.select(sourcePos.x, sourcePos.y, selectRadius, Pal.accent);
            Groups.unit.each(unit -> {
                if(!(unit instanceof ModularUnitEntity other) || other == selectedUnit || other.team != selectedUnit.team || other.design == null) return;
                for(PlacedModule tow : other.design.modules){
                    if(!(tow.type instanceof ModulTow) || !other.design.isActive(tow)) continue;
                    other.modulePos(tow, targetPos);
                    if(sourcePos.dst(targetPos) > maxLinkLength) continue;
                    boolean linked = findLink(selectedUnit, other) != null;
                    Color color = linked ? Pal.accent : Pal.breakInvalid;
                    Drawf.select(targetPos.x, targetPos.y,
                        selectRadius + (linked ? 0f : Mathf.absin(Time.time, 4f, 0.5f)), color);
                }
            });
        }
        Draw.reset();
    }

    static void twist(ModularUnitEntity unit, Vec2 hitch, float impX, float impY){
        ModularPhysics.Stats s = unit.stats();
        if(s == null) return;

        unit.comOffset(Tmp.v3);
        float leverX = hitch.x - (unit.x + Tmp.v3.x), leverY = hitch.y - (unit.y + Tmp.v3.y);
        unit.spin = Mathf.clamp(unit.spin + ModularImpact.spinFrom(leverX, leverY, impX, impY, s.inertia),
            -ModularImpact.maxSpin, ModularImpact.maxSpin);
    }

    static void drawConnected(float x1, float y1, float x2, float y2, float length, float strain){
        float distance = Mathf.dst(x1, y1, x2, y2);
        int segments = Math.max(8, Mathf.ceil(distance / 2.5f));
        float sag = Mathf.clamp(distance * 0.22f, 1.5f, 8f) * (1f - Mathf.clamp(distance / Math.max(length, 0.001f)));
        Draw.color(Tmp.c1.set(ropeColor).lerp(strainedColor, Mathf.clamp(strain)));
        Draw.alpha(1f);
        Lines.stroke(2.2f);
        float previousX = x1, previousY = y1;
        Fill.circle(previousX, previousY, 1.1f);
        for(int i = 1; i <= segments; i++){
            float t = i / (float)segments;
            float currentX = Mathf.lerp(x1, x2, t);
            float currentY = Mathf.lerp(y1, y2, t) - Mathf.sin(t * Mathf.pi) * sag;
            Lines.line(previousX, previousY, currentX, currentY, false);
            Fill.circle(currentX, currentY, 1.1f);
            previousX = currentX;
            previousY = currentY;
        }
    }

    private static void clearSelection(){
        selectedUnit = null;
        selectedTow = null;
    }

    private static PlacedModule towUnderCursor(ModularUnitEntity unit, float x, float y){
        if(unit.design == null) return null;
        PlacedModule best = null;
        float distance = Float.MAX_VALUE;
        for(PlacedModule module : unit.design.modules){
            if(!(module.type instanceof ModulTow) || !unit.design.isActive(module)) continue;
            unit.modulePos(module, sourcePos);
            float current = sourcePos.dst(x, y);
            if(current <= selectRadius && current < distance){
                best = module;
                distance = current;
            }
        }
        return best;
    }

    private static TowEnd findTow(float x, float y, ModularUnitEntity exclude){
        TowEnd[] result = {null};
        float[] nearest = {Float.MAX_VALUE};
        Groups.unit.each(unit -> {
            if(!(unit instanceof ModularUnitEntity modular) || modular == exclude || modular.design == null || modular.team != exclude.team) return;
            PlacedModule tow = towUnderCursor(modular, x, y);
            if(tow == null) return;
            modular.modulePos(tow, targetPos);
            float distance = targetPos.dst(x, y);
            exclude.modulePos(selectedTow, sourcePos);
            if(distance < nearest[0] && sourcePos.dst(targetPos) <= maxLinkLength){
                nearest[0] = distance;
                result[0] = new TowEnd(modular, tow);
            }
        });
        return result[0];
    }

    private static TowLink findLink(ModularUnitEntity a, ModularUnitEntity b){
        return links.find(link -> link.connects(a, b));
    }

    private static void toggleLink(ModularUnitEntity a, PlacedModule towA, ModularUnitEntity b, PlacedModule towB){
        if(net.client()){
            Call.serverPacketReliable(packetName, a.id + "," + towA.x + "," + towA.y + "," + b.id + "," + towB.x + "," + towB.y);
            return;
        }
        toggleAuthoritative(a, towA, b, towB);
    }

    private static void toggleAuthoritative(ModularUnitEntity a, PlacedModule towA, ModularUnitEntity b, PlacedModule towB){
        TowLink existing = links.find(link -> link.uses(a, towA) || link.uses(b, towB));
        if(existing != null){
            removeLink(existing);
        }else{
            if(linkCount(a) >= 2 || linkCount(b) >= 2) return;
            a.modulePos(towA, sourcePos);
            b.modulePos(towB, targetPos);
            if(sourcePos.dst(targetPos) <= maxLinkLength){
                TowLink link = new TowLink(a, towA, b, towB, maxLinkLength);
                links.add(link);
                setTow(a, towA, b, towB, maxLinkLength, 0f);
                setTow(b, towB, a, towA, maxLinkLength, 0f);
            }
        }
    }

    public static void handle(Player sender, String data){
        try{
            String[] p = data.split(",");
            if(p.length != 6 || sender == null) return;
            Unit ua = Groups.unit.getByID(Integer.parseInt(p[0])), ub = Groups.unit.getByID(Integer.parseInt(p[3]));
            if(!(ua instanceof ModularUnitEntity a) || !(ub instanceof ModularUnitEntity b) || sender.unit() != a || a.team != b.team) return;
            PlacedModule towA = moduleAt(a, Integer.parseInt(p[1]), Integer.parseInt(p[2]));
            PlacedModule towB = moduleAt(b, Integer.parseInt(p[4]), Integer.parseInt(p[5]));
            if(towA != null && towB != null && towA.type instanceof ModulTow && towB.type instanceof ModulTow) toggleAuthoritative(a, towA, b, towB);
        }catch(Exception ignored){}
    }

    private static void restoreLinks(){
        Groups.unit.each(unit -> {
            if(!(unit instanceof ModularUnitEntity a)) return;
            for(int slot = 0; slot < 2; slot++){
                if(a.towUnitIds[slot] < 0) continue;
                Unit found = Groups.unit.getByID(a.towUnitIds[slot]);
                if(!(found instanceof ModularUnitEntity b)) continue;
                int towX = a.towXs[slot], towY = a.towYs[slot];
                if(links.contains(l -> l.connectsModules(a, towX, towY, b))) continue;
                PlacedModule ta = moduleAt(a, towX, towY);
                PlacedModule tb = moduleAt(b, a.towOtherXs[slot], a.towOtherYs[slot]);
                if(ta != null && tb != null && ta.type instanceof ModulTow && tb.type instanceof ModulTow){
                    links.add(new TowLink(a, ta, b, tb, Math.max(a.towLengths[slot], 4f)));
                }
            }
        });
    }

    private static int linkCount(ModularUnitEntity unit){
        return links.count(link -> link.a == unit || link.b == unit);
    }

    private static PlacedModule moduleAt(ModularUnitEntity unit, int x, int y){
        if(unit == null || unit.design == null) return null;
        return unit.design.modules.find(module -> module.x == x && module.y == y);
    }

    private static void setTow(ModularUnitEntity unit, PlacedModule own, ModularUnitEntity other, PlacedModule remote, float length, float tension){
        int slot = slotFor(unit, own);
        if(slot < 0) return;
        unit.towUnitIds[slot] = other.id;
        unit.towXs[slot] = own.x;
        unit.towYs[slot] = own.y;
        unit.towOtherXs[slot] = remote.x;
        unit.towOtherYs[slot] = remote.y;
        unit.towLengths[slot] = length;
        unit.towTensions[slot] = tension;
    }

    private static int slotFor(ModularUnitEntity unit, PlacedModule tow){
        for(int i = 0; i < 2; i++) if(unit.towUnitIds[i] >= 0 && unit.towXs[i] == tow.x && unit.towYs[i] == tow.y) return i;
        for(int i = 0; i < 2; i++) if(unit.towUnitIds[i] < 0) return i;
        return -1;
    }

    private static void clearTow(ModularUnitEntity unit, PlacedModule tow){
        for(int i = 0; i < 2; i++){
            if(unit.towXs[i] == tow.x && unit.towYs[i] == tow.y){
                unit.towUnitIds[i] = -1;
                unit.towLengths[i] = unit.towTensions[i] = 0f;
            }
        }
    }

    private static void removeLink(TowLink link){
        links.remove(link);
        if(net.client()) return;
        clearTow(link.a, link.towA);
        clearTow(link.b, link.towB);
    }

    private static void snapEffect(TowLink link){
        link.positions(sourcePos, targetPos);
        float mx = (sourcePos.x + targetPos.x) / 2f, my = (sourcePos.y + targetPos.y) / 2f;
        MdlFX.towSnap.at(mx, my, Angles.angle(sourcePos.x, sourcePos.y, targetPos.x, targetPos.y));
        Sounds.rockBreak.at(mx, my, Mathf.random(1.3f, 1.7f));
    }

    private static boolean hasSlot(ModularUnitEntity unit, PlacedModule tow, ModularUnitEntity other){
        return slotOf(unit, tow, other) >= 0;
    }

    private static int slotOf(ModularUnitEntity unit, PlacedModule tow, ModularUnitEntity other){
        for(int i = 0; i < 2; i++){
            if(unit.towUnitIds[i] == other.id && unit.towXs[i] == tow.x && unit.towYs[i] == tow.y) return i;
        }
        return -1;
    }

    //NOT a record: D8 (--min-api 14) fails to desugar java.lang.Record on some Android SDK/
    //build-tools combinations ("Cannot ensure java.lang.Record as a synthetic program class"),
    //which silently produced a jar with no working Android dex - the exact cause of the
    //Android-only ClassNotFoundException on this mod. A plain holder class sidesteps it entirely.
    private static final class TowEnd{
        final ModularUnitEntity unit;
        final PlacedModule tow;

        TowEnd(ModularUnitEntity unit, PlacedModule tow){
            this.unit = unit;
            this.tow = tow;
        }
    }

    private static class TowLink{
        final ModularUnitEntity a, b;
        final PlacedModule towA, towB;
        final float length;
        /** Stretch as a share of {@link #breakStretch}. At 1 the rope parts. */
        float strain;

        TowLink(ModularUnitEntity a, PlacedModule towA, ModularUnitEntity b, PlacedModule towB, float length){
            this.a = a;
            this.towA = towA;
            this.b = b;
            this.towB = towB;
            this.length = Math.max(length, 4f);
        }

        boolean connects(ModularUnitEntity first, ModularUnitEntity second){
            return a == first && b == second || a == second && b == first;
        }

        boolean uses(ModularUnitEntity unit, PlacedModule tow){
            return a == unit && towA == tow || b == unit && towB == tow;
        }

        boolean connectsModules(ModularUnitEntity first, int x, int y, ModularUnitEntity second){
            return a == first && b == second && towA.x == x && towA.y == y || b == first && a == second && towB.x == x && towB.y == y;
        }

        boolean valid(){
            if(!(a.isAdded() && b.isAdded() && a.team == b.team && a.design != null && b.design != null &&
                a.design.modules.contains(towA, true) && b.design.modules.contains(towB, true))) return false;

            //the server decides when a rope is gone; a client has to notice the slots emptying
            return !net.client() || (hasSlot(a, towA, b) && hasSlot(b, towB, a));
        }

        void readStrain(){
            int slot = slotOf(a, towA, b);
            if(slot >= 0) strain = a.towTensions[slot];
        }

        void positions(Vec2 outA, Vec2 outB){
            a.modulePos(towA, outA);
            b.modulePos(towB, outB);
        }

        float breakForce(){
            return Math.max(Math.min(((ModulTow)towA.type).breakForce, ((ModulTow)towB.type).breakForce), 1f);
        }

        void updatePhysics(){
            positions(sourcePos, targetPos);
            float distance = sourcePos.dst(targetPos);
            if(distance < 0.001f) return;

            float nx = (targetPos.x - sourcePos.x) / distance;
            float ny = (targetPos.y - sourcePos.y) / distance;
            float stretch = Math.max(distance - length, 0f);

            if(stretch > 0f){
                float invA = 1f / Math.max(a.mass(), 1f), invB = 1f / Math.max(b.mass(), 1f);
                float relative = (b.vel.x - a.vel.x) * nx + (b.vel.y - a.vel.y) * ny;
                float spring = breakForce() / breakStretch * stretch * Time.delta;
                float damper = relative * Math.min(ropeDamping * Time.delta, 1f) / (invA + invB);
                float impulse = Math.max(spring + damper, 0f);

                a.vel.add(nx * impulse * invA, ny * impulse * invA);
                b.vel.add(-nx * impulse * invB, -ny * impulse * invB);
                twist(a, sourcePos, nx * impulse, ny * impulse);
                twist(b, targetPos, -nx * impulse, -ny * impulse);
            }

            strain = stretch / breakStretch;

            setTow(a, towA, b, towB, length, strain);
            setTow(b, towB, a, towA, length, strain);
        }

    }
}
