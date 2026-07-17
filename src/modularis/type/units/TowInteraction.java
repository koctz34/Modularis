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
import modularis.type.units.modules.*;

import static mindustry.Vars.*;

public class TowInteraction{
    public static final String packetName = "modularis-tow-link";
    public static final float selectRadius = 6f;
    public static final float maxLinkLength = 24f;

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
        if(headless || !state.isGame()) return;
        installInput();
        restoreLinks();
        for(int i = links.size - 1; i >= 0; i--){
            TowLink link = links.get(i);
            if(!link.valid()) removeLink(link);
        }
        if(!net.client()){
            for(TowLink link : links) link.updatePhysics();
        }
        if(selectedUnit != null && (!selectedUnit.isAdded() || player == null || player.unit() != selectedUnit)) clearSelection();
    }

    public static void draw(){
        if(headless || !state.isGame()) return;
        Draw.z(Layer.overlayUI);

        for(TowLink link : links){
            link.positions(sourcePos, targetPos);
            drawConnected(sourcePos.x, sourcePos.y, targetPos.x, targetPos.y, link.tension());
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

    private static void drawConnected(float x1, float y1, float x2, float y2, float tension){
        float distance = Mathf.dst(x1, y1, x2, y2);
        int segments = Math.max(8, Mathf.ceil(distance / 2.5f));
        float sag = Mathf.clamp(distance * 0.22f, 1.5f, 5f) * (1f - Mathf.clamp(tension));
        Draw.color(Color.valueOf("242424"));
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
                float length = sourcePos.dst(targetPos);
                TowLink link = new TowLink(a, towA, b, towB, length);
                links.add(link);
                setTow(a, towA, b, towB, length, 0f);
                setTow(b, towB, a, towA, length, 0f);
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
        clearTow(link.a, link.towA);
        clearTow(link.b, link.towB);
    }

    private record TowEnd(ModularUnitEntity unit, PlacedModule tow){}

    private static class TowLink{
        final ModularUnitEntity a, b;
        final PlacedModule towA, towB;
        final float length;

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
            return a.isAdded() && b.isAdded() && a.team == b.team && a.design != null && b.design != null &&
                a.design.modules.contains(towA, true) && b.design.modules.contains(towB, true);
        }

        void positions(Vec2 outA, Vec2 outB){
            a.modulePos(towA, outA);
            b.modulePos(towB, outB);
        }

        float tension(){
            if(net.client()){
                for(int i = 0; i < 2; i++){
                    if(a.towUnitIds[i] == b.id && a.towXs[i] == towA.x && a.towYs[i] == towA.y){
                        return a.towTensions[i];
                    }
                }
            }
            positions(sourcePos, targetPos);
            return Mathf.clamp((sourcePos.dst(targetPos) - length + 1.5f) / 1.5f);
        }

        void updatePhysics(){
            positions(sourcePos, targetPos);
            float distance = sourcePos.dst(targetPos);
            if(distance < 0.001f) return;

            float nx = (targetPos.x - sourcePos.x) / distance;
            float ny = (targetPos.y - sourcePos.y) / distance;
            float massA = Math.max(a.design.totalWeight(), 1f);
            float massB = Math.max(b.design.totalWeight(), 1f);
            float stretch = Math.max(distance - length, 0f);
            float relative = (b.vel.x - a.vel.x) * nx + (b.vel.y - a.vel.y) * ny;
            float force = stretch * 0.16f + Math.max(relative, 0f) * 0.28f;
            float impulse = Mathf.clamp(force, 0f, 2.4f) * Time.delta;
            float syncedTension = Mathf.clamp(force / 2.4f);
            setTow(a, towA, b, towB, length, syncedTension);
            setTow(b, towB, a, towA, length, syncedTension);
            float total = massA + massB;

            a.vel.add(nx * impulse * massB / total, ny * impulse * massB / total);
            b.vel.add(-nx * impulse * massA / total, -ny * impulse * massA / total);

            float excess = stretch;
            if(excess > 0.5f){
                float correction = Math.min(excess * 0.18f * Time.delta, 1.5f);
                a.set(a.x + nx * correction * massB / total, a.y + ny * correction * massB / total);
                b.set(b.x - nx * correction * massA / total, b.y - ny * correction * massA / total);
            }

            if(stretch > 0.05f){
                rotateByTow(a, sourcePos, targetPos, massA, massB, stretch);
                rotateByTow(b, targetPos, sourcePos, massB, massA, stretch);
            }
        }

        void rotateByTow(ModularUnitEntity unit, Vec2 hitch, Vec2 target, float ownMass, float otherMass, float stretch){
            float hitchAngle = Angles.angle(unit.x, unit.y, hitch.x, hitch.y);
            float pullAngle = Angles.angle(unit.x, unit.y, target.x, target.y);
            float offset = Mathf.mod(pullAngle - hitchAngle + 180f, 360f) - 180f;
            float leverage = Mathf.dst(unit.x, unit.y, hitch.x, hitch.y) / Math.max(ModularUnitType.cellWorld(), 0.001f);
            float load = Mathf.clamp(stretch / 2f) * Mathf.clamp(leverage, 0.35f, 2.5f);
            float massFactor = Mathf.clamp(otherMass / Math.max(ownMass, 1f), 0.4f, 2.5f);
            unit.rotation += Mathf.clamp(offset, -1.8f, 1.8f) * Time.delta * load * massFactor;
        }
    }
}
