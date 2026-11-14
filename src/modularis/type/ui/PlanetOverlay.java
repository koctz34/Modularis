package modularis.type.ui;

import arc.*;
import arc.audio.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.graphics.g3d.*;
import arc.graphics.gl.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.g3d.*;
import mindustry.type.*;
import mindustry.ui.*;

import modularis.*;

import static mindustry.Vars.*;

public class PlanetOverlay extends Element{
    static final float fadeEnd = 3.5f, crackStart = 3.2f, turnStart = 5.2f, turnEnd = 9f;
    static final float blastAt = 9.3f, darkAt = blastAt + 3.3f, recoverAt = darkAt + 17f;
    public static final float length = recoverAt + 2f;

    static final Blending sum = new Blending(Gl.one, Gl.one), invert = new Blending(Gl.oneMinusDstColor, Gl.zero);
    private static final int vs = 9;

    private static final ObjectMap<String, Sound> sounds = new ObjectMap<>();
    private static @Nullable PlanetOverlay current;
    private static boolean hooked;

    private final Planet planet;
    private final PlanetParams params = new PlanetParams();
    private final Rand rand = new Rand(), steady = new Rand(7);
    private final Seq<Crack> cracks = new Seq<>();
    private final Vec3[] origins = new Vec3[6], originSide = new Vec3[6];
    private final float[] originBorn = new float[6];
    private final float[] debris, embers, lobes, rays;
    private final Vec3 v1 = new Vec3(), v2 = new Vec3();
    private final TextureRegion frame = new TextureRegion();
    private final Seq<String> lines = new Seq<>();
    private final Seq<Color> lineColors = new Seq<>();
    private @Nullable FrameBuffer buffer;
    private float time, nextLine, nextNoise, nextAlarm, progress;
    private int recovery = -1, opened;
    private boolean boomed, crackled, captured;

    static class Crack{
        float[] pts, scr;
        float born, pace, width, phase;
        int depth, count;
    }

    static Sound sound(String name){
        Sound out = sounds.get(name);
        if(out == null){
            try{
                out = new Sound(mods.getMod(MdlMod.class).root.child("data").child(name + ".wav"));
            }catch(Throwable t){
                out = Sounds.none;
            }
            sounds.put(name, out);
        }
        return out;
    }

    public static void play(String planetName){
        if(current != null) return;
        Planet p = content.planet(planetName);
        current = new PlanetOverlay(p == null ? Planets.serpulo : p);
        Core.scene.add(current);
        if(!hooked){
            hooked = true;
            Events.on(WorldLoadEvent.class, e -> {
                if(current != null && current.time > length - 1.5f) current.close();
            });
        }
    }

    PlanetOverlay(Planet planet){
        this.planet = planet;
        touchable = Touchable.enabled;
        params.planet = planet;
        params.drawUi = false;
        params.drawSkybox = true;
        params.alwaysDrawAtmosphere = true;
        params.uiAlpha = 0f;
        params.camPos.set(0f, 0f, 4f);

        fracture(steady);

        embers = new float[260 * 6];
        for(int i = 0; i < embers.length; i += 6){
            int ci = steady.random(cracks.size - 1);
            embers[i] = ci;
            embers[i + 1] = steady.random(0, Math.max(cracks.get(ci).pts.length / 3 - 3, 0));
            embers[i + 2] = steady.random(0.45f, 1.3f);
            embers[i + 3] = steady.random(1f);
            embers[i + 4] = steady.random(0.05f, 0.3f);
            embers[i + 5] = steady.range(1f);
        }

        debris = new float[620 * 6];
        for(int i = 0; i < debris.length; i += 6){
            debris[i] = steady.random(360f);
            debris[i + 1] = steady.random(0.4f, 3.2f) * (steady.chance(0.15) ? 1.8f : 1f);
            debris[i + 2] = steady.random(0.004f, 0.022f) * (steady.chance(0.08) ? 3f : 1f);
            debris[i + 3] = steady.range(400f);
            debris[i + 4] = steady.chance(0.3) ? 1f : 0f;
            debris[i + 5] = steady.random(0.1f, 0.95f);
        }

        lobes = new float[18 * 3];
        for(int i = 0; i < lobes.length; i += 3){
            lobes[i] = steady.random(360f);
            lobes[i + 1] = steady.random(0.15f, 1f);
            lobes[i + 2] = steady.random(0.25f, 0.7f);
        }

        rays = new float[32 * 4];
        for(int i = 0; i < rays.length; i += 4){
            rays[i] = steady.random(360f);
            rays[i + 1] = steady.random(0.5f, 1.7f);
            rays[i + 2] = steady.random(0.03f, 0.13f);
            rays[i + 3] = steady.range(7f);
        }
    }

    private static Vec3 view(float t, Vec3 out){
        float turn = Interp.smoother.apply(Mathf.clamp((t - turnStart) / (turnEnd - turnStart)));
        return out.set(0.25f, Mathf.lerp(0.35f, -0.12f, turn), 1f).nor().rotate(Vec3.Y, Mathf.lerp(-30f, 48f, turn));
    }

    private static void turn(Vec3 d, Vec3 p, float deg){
        float c = Mathf.cosDeg(deg), s = Mathf.sinDeg(deg);
        float cx = p.y * d.z - p.z * d.y, cy = p.z * d.x - p.x * d.z, cz = p.x * d.y - p.y * d.x;
        d.set(d.x * c + cx * s, d.y * c + cy * s, d.z * c + cz * s);
    }

    static void blend(Blending mode){
        Draw.flush();
        Draw.blend(mode);
    }

    private static float taper(int k, int n){
        return n < 2 ? 1f : Mathf.pow(1f - k / (float)(n - 1), 0.6f);
    }

    private void fracture(Rand r){
        origins[0] = view(crackStart + 0.4f, new Vec3()).add(0.28f, 0.2f, 0f).nor();
        origins[1] = view(turnEnd, new Vec3()).add(-0.3f, -0.25f, 0.1f).nor();
        for(int i = 2; i < origins.length; i++){
            Vec3 o = origins[i] = new Vec3();
            for(int tries = 0; tries < 40; tries++){
                o.setToRandomDirection(r);
                boolean apart = true;
                for(int j = 0; j < i; j++) apart &= o.dot(origins[j]) < 0.45f;
                if(apart) break;
            }
        }

        for(int i = 0; i < origins.length; i++){
            Vec3 o = origins[i];
            originBorn[i] = i == 0 ? 0f : i == 1 ? 2.6f : 0.5f + (i - 2) * 0.55f + r.random(0.3f);
            Vec3 side = originSide[i] = new Vec3().setToRandomDirection(r);
            side.sub(v1.set(o).scl(side.dot(o))).nor();
            int arms = r.random(3, 5);
            float spin = r.random(360f);
            for(int a = 0; a < arms; a++){
                Vec3 dir = new Vec3(side);
                turn(dir, o, spin + a * 360f / arms + r.range(24f));
                grow(r, o, dir, r.random(22, 34), originBorn[i], 1f, 0);
            }
        }
    }

    private void grow(Rand r, Vec3 from, Vec3 heading, int steps, float born, float width, int depth){
        Crack c = new Crack();
        c.pts = new float[steps * 3];
        c.scr = new float[steps * vs];
        c.born = born;
        c.pace = r.random(0.035f, 0.06f) * (1f + depth * 0.3f);
        c.width = width;
        c.depth = depth;
        c.phase = r.random(100f);
        cracks.add(c);

        Vec3 p = new Vec3(from), dir = new Vec3(heading), step = new Vec3();
        for(int i = 0; i < steps; i++){
            c.pts[i * 3] = p.x;
            c.pts[i * 3 + 1] = p.y;
            c.pts[i * 3 + 2] = p.z;

            turn(dir, p, r.range(10f));
            step.set(dir);
            turn(step, p, r.chance(0.22) ? r.range(70f) : r.range(30f));
            float a = r.random(1.6f, 4.2f) * (depth == 0 ? 1f : 0.8f);
            p.scl(Mathf.cosDeg(a)).add(step, Mathf.sinDeg(a)).nor();
            dir.sub(step.set(p).scl(dir.dot(p))).nor();

            if(depth < 2 && i > 2 && i < steps - 4 && r.chance(depth == 0 ? 0.2 : 0.14)){
                Vec3 off = new Vec3(dir);
                turn(off, p, r.random(28f, 70f) * (r.chance(0.5) ? 1f : -1f));
                grow(r, p, off, (int)((steps - i) * r.random(0.45f, 0.85f)) + 4, born + i * c.pace, Math.max(width * taper(i, steps) * 0.7f, 0.2f), depth + 1);
            }
        }
    }

    void close(){
        remove();
        if(buffer != null){
            buffer.dispose();
            buffer = null;
        }
        if(current == this) current = null;
    }

    @Override
    public void act(float delta){
        super.act(delta);
        time += Math.min(delta, 0.1f);
        setBounds(0f, 0f, Core.scene.getWidth(), Core.scene.getHeight());
        toFront();

        if(time >= crackStart && !crackled){
            crackled = true;
            sound("rumble").play(1f);
        }
        for(int i = 0; i < originBorn.length; i++){
            if((opened & 1 << i) == 0 && time - crackStart >= originBorn[i] && time < blastAt){
                opened |= 1 << i;
                Sounds.rockBreak.play(0.9f, rand.random(0.45f, 0.7f), 0f);
            }
        }
        if(time >= blastAt && !boomed){
            boomed = true;
            Sounds.explosionCore.play(2f);
            Sounds.explosionReactorNeoplasm.play(1.5f);
            sound("glitch2").play(0.9f);
        }
        if(time >= blastAt && time < darkAt){
            nextNoise -= delta;
            if(nextNoise <= 0f){
                nextNoise = rand.random(0.08f, 0.5f) * Mathf.lerp(1f, 0.35f, (time - blastAt) / (darkAt - blastAt));
                sound("glitch" + rand.random(1, 3)).play(rand.random(0.35f, 0.75f), rand.random(0.6f, 1.6f), rand.range(0.6f));
            }
        }
        if(time >= darkAt && time < recoverAt){
            nextAlarm -= delta;
            if(nextAlarm <= 0f){
                nextAlarm = 1.5f;
                sound("alarm").play(0.55f);
            }
        }
        if(time >= darkAt) updateConsole(delta);
        if(time >= length + 4f) close();
    }

    private void updateConsole(float delta){
        if(time >= recoverAt){
            if(recovery < 0){
                recovery = 0;
                nextLine = 0f;
            }
            progress = Mathf.clamp((time - recoverAt) / 1.7f);
            nextLine -= delta;
            if(nextLine <= 0f && recovery < restore.length){
                push(restore[recovery].replace("$", planet.localizedName), recovery == 0 ? Color.white : Color.valueOf("8ef0a1"));
                sound("beep").play(0.6f, 1f + recovery * 0.06f, 0f);
                recovery++;
                nextLine = 0.17f;
            }
            return;
        }

        nextLine -= delta;
        if(nextLine > 0f) return;
        float into = (time - darkAt) / (recoverAt - darkAt);
        nextLine = rand.random(0.08f, 0.55f) * Mathf.lerp(1.4f, 0.25f, into);
        int burst = rand.chance(0.12 + into * 0.4) ? rand.random(2, 5) : 1;
        for(int i = 0; i < burst; i++){
            String line = faults[rand.random(faults.length - 1)];
            line = line.replace("$", planet.localizedName)
                .replace("#", Integer.toHexString(rand.random(0x100000, 0xffffff)))
                .replace("%", String.valueOf(rand.random(0, 9999)));
            push(line, line.startsWith("[F") ? Color.valueOf("ff3b3b") : line.startsWith("[W") ? Color.valueOf("ffb347") : Color.valueOf("ff6b6b"));
        }
        sound("pip").play(0.45f, rand.random(0.7f, 1.4f), 0f);
    }

    private void push(String line, Color color){
        lines.add(String.format("%07.2f ", time - darkAt) + line);
        lineColors.add(color);
        if(lines.size > 200){
            lines.remove(0);
            lineColors.remove(0);
        }
    }

    @Override
    public void draw(){
        float w = width, h = height;
        Draw.color(Color.black);
        Fill.crect(x, y, w, h);

        if(time < blastAt){
            drawSpace(0f);

            float black = time < 1f ? 1f : 1f - Mathf.clamp((time - 1f) / (fadeEnd - 1f));
            if(black > 0f){
                Draw.color(0f, 0f, 0f, Interp.pow2In.apply(black));
                Fill.crect(x, y, w, h);
            }
        }else if(time < darkAt){
            drawBroken();
        }else{
            drawTerminal();
        }
        Draw.reset();
    }

    private void drawSpace(float g){
        float t = time - crackStart, heat = Mathf.clamp(t / (blastAt - crackStart));
        boolean gone = time >= blastAt;

        float shake = heat * heat * heat * 0.012f;
        for(float born : originBorn){
            float age = t - born;
            if(age > 0f && age < 0.4f) shake += (1f - age / 0.4f) * 0.018f;
        }
        if(gone) shake = 0.03f * Mathf.clamp(1f - (time - blastAt) / 1.2f) + g * 0.05f;

        params.camPos.set(view(time, v2).add(rand.range(shake), rand.range(shake), 0f).nor()).scl(4f);
        params.zoom = Mathf.lerp(1.55f, 0.5f, Interp.smooth.apply(Mathf.clamp(time / blastAt)));

        boolean was = planet.visible;
        if(gone) planet.visible = false;
        Draw.flush();
        renderer.planets.render(params);
        planet.visible = was;

        Camera3D cam = renderer.planets.cam;
        int sw = Core.graphics.getWidth(), sh = Core.graphics.getHeight();
        Vec3 c = cam.project(v1.set(planet.position), 0, 0, sw, sh);
        float cx = c.x, cy = c.y;
        Vec3 edge = cam.project(v1.set(cam.up).scl(planet.radius).add(planet.position), 0, 0, sw, sh);
        float r = Mathf.dst(cx, cy, edge.x, edge.y);

        if(!gone){
            if(t > 0f) drawCracks(cam, sw, sh, cx, cy, r, t, heat);
        }else{
            drawBlast(cx, cy, r);
        }
        blend(Blending.normal);

        if(gone){
            Draw.color(1f, 1f, 1f, 0.9f * Interp.pow2In.apply(Mathf.clamp(1f - (time - blastAt) / 0.7f)));
            Fill.crect(x, y, width, height);
        }
    }

    private void drawCracks(Camera3D cam, int sw, int sh, float cx, float cy, float r, float t, float heat){
        float surge = Interp.pow2In.apply(Mathf.clamp((time - blastAt + 0.8f) / 0.8f));
        float beat = 1f + (0.1f + 0.25f * heat) * Mathf.pow(Math.max(Mathf.sin(t * 3f + t * t * 1.3f), 0f), 3f);
        float open = (1f + heat * heat * 1.2f + surge * 1.6f) * beat;
        float shaft = Mathf.clamp((heat - 0.25f) / 0.75f);
        float rad = planet.radius, limit = r * 0.004f;

        Vec3 eye = v2.set(cam.position).sub(planet.position);
        float horizon = rad / eye.len();
        eye.nor();
        float ex = eye.x, ey = eye.y, ez = eye.z;

        for(Crack c : cracks){
            float head = (t - c.born) / c.pace;
            int n = c.pts.length / 3;
            c.count = head <= 0f ? 0 : Math.min(n, (int)head + 2);
            float[] p = c.pts, s = c.scr;

            for(int k = 0; k < c.count; k++){
                float nx = p[k * 3], ny = p[k * 3 + 1], nz = p[k * 3 + 2];
                int o = k * vs;
                float facing = nx * ex + ny * ey + nz * ez - horizon;
                if(facing < -0.2f){
                    s[o + 2] = 0f;
                    continue;
                }
                Vec3 at = cam.project(v1.set(nx, ny, nz).scl(rad * 1.003f).add(planet.position), 0, 0, sw, sh);
                float since = (head - k) * c.pace, slim = taper(k, n);
                float wave = Mathf.pow(Math.max(Mathf.sin(k * 0.45f - time * 6f + c.phase), 0f), 5f);
                s[o] = at.x;
                s[o + 1] = at.y;
                s[o + 2] = Mathf.clamp(facing * 7f);
                s[o + 5] = r * 0.0085f * c.width * slim * Mathf.clamp(since / 0.22f) * open * (1f + wave * 0.3f);
                s[o + 6] = Mathf.clamp(Math.max(Mathf.clamp(1f - since / 0.45f), surge) + wave * 0.45f * heat + heat * heat * 0.3f);

                if(c.depth < 2 && shaft > 0f){
                    float reach = (0.04f + 0.55f * shaft * shaft + surge * 0.8f) * (0.55f + 0.45f * Mathf.sin(k * 0.8f + time * 4.5f + c.phase)) * slim * c.width;
                    Vec3 tip = cam.project(v1.set(nx, ny, nz).scl(rad * (1f + reach)).add(planet.position), 0, 0, sw, sh);
                    s[o + 7] = tip.x;
                    s[o + 8] = tip.y;
                }
            }

            if(c.count >= 2 && c.count - 1 > head){
                int a = (c.count - 2) * vs, b = a + vs;
                float f = head - (c.count - 2);
                s[b] = Mathf.lerp(s[a], s[b], f);
                s[b + 1] = Mathf.lerp(s[a + 1], s[b + 1], f);
            }

            for(int k = 0; k < c.count; k++){
                int o = k * vs, a = Math.max(k - 1, 0) * vs, b = Math.min(k + 1, c.count - 1) * vs;
                float dx = s[b] - s[a], dy = s[b + 1] - s[a + 1], len = Mathf.len(dx, dy);
                s[o + 3] = len < 0.001f ? 0f : -dy / len;
                s[o + 4] = len < 0.001f ? 0f : dx / len;
            }
        }

        blend(Blending.normal);
        for(Crack c : cracks){
            ribbon(c, 3.2f, r * 0.006f, limit, Tmp.c1.set(0.03f, 0f, 0f, 0.8f), Tmp.c2.set(0f, 0f, 0f, 0f), 0f);
        }

        blend(Blending.additive);
        float rim = Math.min(0.2f * heat * heat * beat, 1f);
        Fill.light(cx, cy, 60, r, Tmp.c1.set(1f, 0.3f, 0.05f, 0.06f * heat), Tmp.c2.set(1f, 0.28f, 0.03f, rim));
        Fill.lightInner(cx, cy, 60, r, r * (1.2f + heat * 0.7f), 0f, Tmp.c1.set(1f, 0.28f, 0.03f, rim), Tmp.c2.set(1f, 0.15f, 0f, 0f));

        float sheet = Math.min(0.16f * shaft + 0.3f * surge, 1f);
        float clear = Color.toFloatBits(1f, 0.4f, 0.1f, 0f);
        for(Crack c : cracks){
            if(c.count < 2) continue;
            float[] s = c.scr;
            if(c.depth < 2 && shaft > 0f){
                for(int k = 1; k < c.count; k++){
                    int a = (k - 1) * vs, b = k * vs;
                    if(s[a + 2] <= 0f && s[b + 2] <= 0f) continue;
                    float ca = Color.toFloatBits(1f, 0.72f, 0.38f, sheet * s[a + 2] * Mathf.clamp(s[a + 5] / limit));
                    float cb = Color.toFloatBits(1f, 0.72f, 0.38f, sheet * s[b + 2] * Mathf.clamp(s[b + 5] / limit));
                    Fill.quad(s[a], s[a + 1], ca, s[b], s[b + 1], cb, s[b + 7], s[b + 8], clear, s[a + 7], s[a + 8], clear);
                }
            }
            ribbon(c, 6f, r * 0.012f, limit, Tmp.c1.set(1f, 0.26f, 0.03f, 0.3f + 0.2f * heat), Tmp.c2.set(1f, 0.1f, 0f, 0f), 0.25f);
            ribbon(c, 2.3f, 0f, limit, Tmp.c1.set(1f, 0.5f, 0.1f, 0.9f), Tmp.c2.set(1f, 0.25f, 0.02f, 0f), 0.6f);
            ribbon(c, 0.9f, 0f, limit, Tmp.c1.set(1f, 0.9f, 0.62f, 1f), Tmp.c2.set(1f, 0.7f, 0.3f, 0.35f), 1f);
        }

        for(int i = 0; i < origins.length; i++){
            float age = t - originBorn[i];
            if(age <= 0f) continue;
            Vec3 o = origins[i], u = originSide[i];
            float vis = Mathf.clamp((o.x * ex + o.y * ey + o.z * ez - horizon) * 7f);
            if(vis > 0f){
                Vec3 at = cam.project(v1.set(o).scl(rad * 1.003f).add(planet.position), 0, 0, sw, sh);
                float flash = Mathf.clamp(1f - age / 0.5f);
                Fill.light(at.x, at.y, 24, r * (0.07f + 0.05f * heat + flash * 0.3f) * beat, Tmp.c1.set(1f, 0.9f, 0.7f, vis * (0.55f + flash * 0.45f)), Tmp.c2.set(1f, 0.3f, 0.05f, 0f));
            }

            float ring = age / 1.5f;
            if(ring >= 1f) continue;
            float spread = 8f + 70f * Interp.pow2Out.apply(ring), sn = Mathf.sinDeg(spread), cs = Mathf.cosDeg(spread);
            float wx = o.y * u.z - o.z * u.y, wy = o.z * u.x - o.x * u.z, wz = o.x * u.y - o.y * u.x;
            float px = 0f, py = 0f, pv = 0f;
            Lines.stroke(r * 0.014f * (1f - ring));
            for(int j = 0; j <= 48; j++){
                float ca = Mathf.cosDeg(j * 7.5f) * sn, sa = Mathf.sinDeg(j * 7.5f) * sn;
                float nx = o.x * cs + u.x * ca + wx * sa, ny = o.y * cs + u.y * ca + wy * sa, nz = o.z * cs + u.z * ca + wz * sa;
                float v = Mathf.clamp((nx * ex + ny * ey + nz * ez - horizon) * 7f);
                Vec3 at = cam.project(v1.set(nx, ny, nz).scl(rad * 1.003f).add(planet.position), 0, 0, sw, sh);
                if(j > 0 && v > 0f && pv > 0f){
                    Draw.color(1f, 0.65f, 0.35f, 0.5f * (1f - ring) * (1f - ring) * Math.min(v, pv));
                    Lines.line(px, py, at.x, at.y, false);
                }
                px = at.x;
                py = at.y;
                pv = v;
            }
        }

        for(int i = 0; i < embers.length; i += 6){
            Crack c = cracks.get((int)embers[i]);
            int k = (int)embers[i + 1];
            if(k + 2 >= c.count) continue;
            float[] p = c.pts;
            float nx = p[k * 3], ny = p[k * 3 + 1], nz = p[k * 3 + 2];
            float facing = nx * ex + ny * ey + nz * ez - horizon;
            if(facing <= 0f) continue;
            float f = (time * embers[i + 2] + embers[i + 3]) % 1f;
            float bx = p[k * 3 + 3], by = p[k * 3 + 4], bz = p[k * 3 + 5];
            float lx = ny * bz - nz * by, ly = nz * bx - nx * bz, lz = nx * by - ny * bx;
            float drift = embers[i + 5] * f * 0.12f / Math.max(Mathf.len(Mathf.len(lx, ly), lz), 0.001f);
            float rise = embers[i + 4] * (1f - (1f - f) * (1f - f)) * (0.5f + heat);
            Vec3 at = cam.project(v1.set(nx + lx * drift, ny + ly * drift, nz + lz * drift).scl(rad * (1f + rise)).add(planet.position), 0, 0, sw, sh);
            Draw.color(1f, 0.85f - 0.5f * f, 0.35f - 0.3f * f, (1f - f) * Mathf.clamp(facing * 7f) * (0.4f + 0.6f * heat));
            Fill.square(at.x, at.y, r * 0.0045f * (1.3f - f), 45f);
        }

        if(surge > 0f){
            Fill.light(cx, cy, 60, r * (0.6f + surge * 1.1f), Tmp.c1.set(1f, 1f, 0.9f, surge), Tmp.c2.set(1f, 0.6f, 0.2f, 0f));
        }
    }

    private void ribbon(Crack c, float scl, float pad, float limit, Color in, Color out, float whiten){
        float[] s = c.scr;
        float px = 0f, py = 0f, pox = 0f, poy = 0f, pci = 0f, pco = 0f, pvis = 0f;
        for(int k = 0; k < c.count; k++){
            int o = k * vs;
            float vis = s[o + 2], w = s[o + 5], hot = s[o + 6] * whiten;
            float hw = w * scl + pad * Mathf.clamp(w / limit);
            float ci = Color.toFloatBits(in.r + (1f - in.r) * hot, in.g + (1f - in.g) * hot, in.b + (1f - in.b) * hot, in.a * vis);
            float co = Color.toFloatBits(out.r, out.g, out.b, out.a * vis);
            float x = s[o], y = s[o + 1], ox = s[o + 3] * hw, oy = s[o + 4] * hw;
            if(k > 0 && (vis > 0f || pvis > 0f)){
                Fill.quad(px, py, pci, x, y, ci, x + ox, y + oy, co, px + pox, py + poy, pco);
                Fill.quad(px, py, pci, x, y, ci, x - ox, y - oy, co, px - pox, py - poy, pco);
            }
            px = x;
            py = y;
            pox = ox;
            poy = oy;
            pci = ci;
            pco = co;
            pvis = vis;
        }
    }

    private void drawBlast(float cx, float cy, float r){
        float u = time - blastAt;
        float fade = Mathf.clamp(1f - u / 3.6f), grow = 1f - Mathf.pow(0.5f, u * 2.4f);
        float flash = Mathf.clamp(1f - u / 1.5f);
        flash *= flash;
        float clear = Color.toFloatBits(1f, 0.5f, 0.15f, 0f);

        blend(Blending.additive);
        Fill.light(cx, cy, 64, r * (1.6f + grow * 6f), Tmp.c1.set(1f, 0.7f, 0.4f, 0.5f * fade), Tmp.c2.set(1f, 0.2f, 0.02f, 0f));
        Fill.light(cx, cy, 64, r * (1f + grow * 2.6f), Tmp.c1.set(1f, 0.97f, 0.85f, fade), Tmp.c2.set(1f, 0.4f, 0.06f, 0f));
        Fill.light(cx, cy, 48, r * (0.6f + grow * 1.1f), Tmp.c1.set(1f, 1f, 1f, fade * fade), Tmp.c2.set(1f, 0.8f, 0.5f, 0f));

        for(int i = 0; i < lobes.length; i += 3){
            float life = Mathf.clamp(1f - u / (1.2f + lobes[i + 1] * 1.4f));
            if(life <= 0f) continue;
            float dist = r * lobes[i + 1] * grow * 3f;
            Fill.light(cx + Angles.trnsx(lobes[i], dist), cy + Angles.trnsy(lobes[i], dist), 20, r * lobes[i + 2] * (0.6f + grow * 1.6f),
                Tmp.c1.set(1f, 0.55f + 0.4f * life, 0.15f + 0.5f * life * life, 0.6f * life), Tmp.c2.set(1f, 0.2f, 0f, 0f));
        }

        for(int i = 0; i < rays.length; i += 4){
            float a = rays[i] + u * rays[i + 3], len = r * rays[i + 1] * (0.8f + grow * 7f), hw = r * rays[i + 2];
            float c = Color.toFloatBits(1f, 0.9f, 0.7f, 0.4f * fade * (0.6f + 0.4f * Mathf.sin(time * 17f + i)));
            float dx = Mathf.cosDeg(a), dy = Mathf.sinDeg(a);
            Fill.quad(cx - dy * hw, cy + dx * hw, c, cx + dy * hw, cy - dx * hw, c, cx + dx * len, cy + dy * len, clear, cx + dx * len, cy + dy * len, clear);
        }

        if(flash > 0f){
            float len = r * (4f + 10f * flash), hh = r * 0.07f * flash, c = Color.toFloatBits(1f, 0.95f, 0.9f, flash);
            Fill.quad(cx, cy + hh, c, cx, cy - hh, c, cx - len, cy, clear, cx - len, cy, clear);
            Fill.quad(cx, cy + hh, c, cx, cy - hh, c, cx + len, cy, clear, cx + len, cy, clear);
        }

        shock(cx, cy, r * (1f + u * 7.5f), r * (0.3f + u * 0.7f), 1f, 0.85f, 0.6f, 0.7f * fade);
        if(u > 0.45f) shock(cx, cy, r * (0.4f + (u - 0.45f) * 4.2f), r * 0.35f, 1f, 0.6f, 0.3f, 0.5f * Mathf.clamp(1f - (u - 0.45f) / 2f));
        ellipse(cx, cy, r * (1.2f + u * 4.2f), 0.22f, 18f, r * 0.11f * fade, Tmp.c3.set(1f, 0.55f, 0.2f, fade));
        ellipse(cx, cy, r * (1f + u * 2.6f), 0.3f, 18f, r * 0.05f * fade, Tmp.c3.set(1f, 0.9f, 0.7f, fade * 0.8f));

        float out = 1f - Mathf.pow(0.7f, u * 1.6f);
        for(int i = 0; i < debris.length; i += 6){
            if(debris[i + 4] > 0f) continue;
            float ang = debris[i], speed = debris[i + 1];
            float life = Mathf.clamp(1f - u / (3f + speed));
            if(life <= 0f) continue;
            float dist = r * (debris[i + 5] + speed * out * 3.4f);
            float dx = Mathf.cosDeg(ang), dy = Mathf.sinDeg(ang) * 0.85f;
            float px = cx + dx * dist, py = cy + dy * dist;
            float s = r * debris[i + 2] * (0.5f + life * 0.5f), tail = s * (2f + 5f * life * speed);
            float c = Tmp.c1.set(1f, 0.9f, 0.6f, life).lerp(0.9f, 0.2f, 0.05f, life, 1f - life).toFloatBits();
            Fill.quad(px - dy * s, py + dx * s, c, px + dy * s, py - dx * s, c, px - dx * tail, py - dy * tail, clear, px - dx * tail, py - dy * tail, clear);
        }

        blend(Blending.normal);
        for(int i = 0; i < debris.length; i += 6){
            if(debris[i + 4] <= 0f) continue;
            float speed = debris[i + 1], life = Mathf.clamp(1f - u / (3f + speed));
            if(life <= 0f) continue;
            float dist = r * (debris[i + 5] + speed * out * 3.4f);
            Draw.color(0.07f, 0.045f, 0.04f, Mathf.clamp(life * 2.5f));
            Fill.poly(cx + Angles.trnsx(debris[i], dist), cy + Angles.trnsy(debris[i], dist), 3 + i / 6 % 3, r * debris[i + 2] * 2.2f * (0.5f + life * 0.5f), debris[i + 3] * u + debris[i]);
        }

        blend(Blending.additive);
        for(int i = 0; i < debris.length; i += 6){
            if(debris[i + 4] <= 0f) continue;
            float ang = debris[i], speed = debris[i + 1], life = Mathf.clamp(1f - u / (3f + speed));
            if(life <= 0f) continue;
            float dist = r * (debris[i + 5] + speed * out * 3.4f);
            float dx = Mathf.cosDeg(ang), dy = Mathf.sinDeg(ang);
            float px = cx + dx * dist, py = cy + dy * dist;
            float s = r * debris[i + 2] * 2.2f * (0.5f + life * 0.5f), glow = Mathf.clamp(1f - u / (1.6f + speed * 0.4f));
            Draw.color(1f, 0.4f + 0.4f * glow, 0.1f + 0.3f * glow, life * (0.35f + 0.65f * glow));
            Fill.poly(px - dx * s * 0.22f, py - dy * s * 0.22f, 3 + i / 6 % 3, s * 0.72f, debris[i + 3] * u + ang);
            float tail = s * (2f + 5f * speed * glow), c = Color.toFloatBits(1f, 0.55f, 0.2f, 0.5f * life * glow);
            Fill.quad(px - dy * s * 0.6f, py + dx * s * 0.6f, c, px + dy * s * 0.6f, py - dx * s * 0.6f, c, px - dx * tail, py - dy * tail, clear, px - dx * tail, py - dy * tail, clear);
        }
    }

    private void shock(float cx, float cy, float rad, float thick, float cr, float cg, float cb, float alpha){
        if(alpha <= 0f) return;
        Tmp.c1.set(cr, cg, cb, 0f);
        Tmp.c2.set(cr, cg, cb, alpha);
        Fill.lightInner(cx, cy, 72, Math.max(rad - thick, 0f), rad, 0f, Tmp.c1, Tmp.c2);
        Fill.lightInner(cx, cy, 72, rad, rad + thick * 0.25f, 0f, Tmp.c2, Tmp.c1);
    }

    private void ellipse(float cx, float cy, float rad, float squash, float tilt, float stroke, Color color){
        if(stroke <= 0.01f) return;
        float mid = color.toFloatBits(), edge = Color.toFloatBits(color.r, color.g, color.b, 0f);
        float ct = Mathf.cosDeg(tilt), st = Mathf.sinDeg(tilt);
        float lx = 0f, ly = 0f;
        for(int i = 0; i <= 72; i++){
            float ca = Mathf.cosDeg(i * 5f), sa = Mathf.sinDeg(i * 5f) * squash;
            float dx = ca * ct - sa * st, dy = ca * st + sa * ct;
            if(i > 0){
                float in = rad - stroke, out = rad + stroke;
                Fill.quad(cx + lx * in, cy + ly * in, edge, cx + dx * in, cy + dy * in, edge, cx + dx * rad, cy + dy * rad, mid, cx + lx * rad, cy + ly * rad, mid);
                Fill.quad(cx + lx * rad, cy + ly * rad, mid, cx + dx * rad, cy + dy * rad, mid, cx + dx * out, cy + dy * out, edge, cx + lx * out, cy + ly * out, edge);
            }
            lx = dx;
            ly = dy;
        }
    }

    private void drawBroken(){
        float w = width, h = height;
        float u = time - blastAt, g = Mathf.clamp(u / (darkAt - blastAt));
        float kick = Mathf.clamp(1f - u / 0.5f);
        float burst = rand.chance(0.14) ? 1.9f : 1f;
        float amp = Mathf.clamp(Math.max(kick * 0.8f, 0.14f + g * g * 0.86f) * burst);

        int sw = Core.graphics.getWidth(), sh = Core.graphics.getHeight();
        if(buffer == null) buffer = new FrameBuffer(sw, sh);
        buffer.resize(sw, sh);
        if(!captured || !rand.chance(Math.max(kick * 0.4f, g * 0.6f))){
            captured = true;
            buffer.begin(Color.black);
            drawSpace(g);
            buffer.end();
        }
        frame.set(buffer.getTexture());

        if(!rand.chance(g * g * g * 0.6f)) tear(frame, rand, x, y, w, h, amp);
        flips(rand, x, y, w, h, kick > 0f ? rand.random(1, 3) : rand.chance(0.25f + amp * 0.5f) ? rand.random(1, 2) : 0, amp);
        noise(rand, x, y, w, h, Mathf.clamp(Math.max(kick * 0.3f, 0.06f + g * g * 0.94f) * burst));
    }

    static void tear(TextureRegion frame, Rand rand, float x, float y, float w, float h, float amp){
        blend(Blending.normal);
        Draw.color(Color.black);
        Fill.crect(x, y, w, h);

        float split = Scl.scl(1.5f + 24f * amp) * (rand.chance(0.5) ? 1f : -1f);
        blend(sum);
        strip(frame, x, y, w, h, 0f, 1f, rand.chance(amp * 0.5f) ? rand.range(w * 0.03f * amp) : 0f, split);

        int tears = 2 + (int)(amp * 16f);
        for(int i = 0; i < tears; i++){
            float f0 = rand.random(1f), f1 = Math.min(f0 + rand.random(0.008f, 0.03f + amp * 0.14f), 1f);
            blend(Blending.normal);
            Draw.color(Color.black);
            Fill.crect(x, y + f0 * h, w, (f1 - f0) * h);
            blend(sum);
            strip(frame, x, y, w, h, f0, f1, rand.range(w * (0.015f + 0.22f * amp)), split * rand.random(1f, 2.6f));
        }

        int blocks = (int)(amp * 12f);
        for(int i = 0; i < blocks; i++){
            float bw = rand.random(0.04f, 0.22f), bh = rand.random(0.02f, 0.1f);
            float sx = rand.random(1f - bw), sy = rand.random(1f - bh);
            float dx = Mathf.clamp(sx + rand.range(0.1f), 0f, 1f - bw), dy = Mathf.clamp(sy + rand.range(0.1f), 0f, 1f - bh);
            frame.set(sx, rand.chance(0.4) ? sy + 0.003f : sy + bh, sx + bw, sy);
            blend(Blending.normal);
            Draw.color(Color.black);
            Fill.crect(x + dx * w, y + dy * h, bw * w, bh * h);
            blend(sum);
            Draw.color(rand.chance(0.7) ? 1f : 0.15f, rand.chance(0.7) ? 1f : 0.15f, rand.chance(0.7) ? 1f : 0.15f);
            Draw.rect(frame, x + (dx + bw / 2f) * w, y + (dy + bh / 2f) * h, bw * w, bh * h);
        }
        blend(Blending.normal);
    }

    private static void strip(TextureRegion frame, float x, float y, float w, float h, float f0, float f1, float offset, float split){
        frame.set(0f, f1, 1f, f0);
        float cy = y + (f0 + f1) / 2f * h, sh = (f1 - f0) * h;
        for(int ch = 0; ch < 3; ch++){
            Draw.color(ch == 0 ? 1f : 0f, ch == 1 ? 1f : 0f, ch == 2 ? 1f : 0f);
            float ox = offset + (ch - 1) * split;
            Draw.rect(frame, x + w / 2f + ox, cy, w, sh);
            if(ox != 0f) Draw.rect(frame, x + w / 2f + ox - Math.signum(ox) * w, cy, w, sh);
        }
    }

    static void flips(Rand rand, float x, float y, float w, float h, int count, float amp){
        blend(invert);
        Draw.color(Color.white);
        for(int i = 0; i < count; i++){
            Fill.crect(x, y + rand.random(h), w, rand.random(3f, 14f + amp * 90f));
        }
        blend(Blending.normal);
    }

    static void noise(Rand rand, float x, float y, float w, float h, float g){
        int bands = 6 + (int)(g * 22f);
        for(int i = 0; i < bands; i++){
            float by = rand.random(h), bh = rand.random(2f, 26f + g * 60f);
            float shade = rand.random(1f);
            if(rand.chance(0.45)){
                Draw.color(0f, 0f, 0f, 0.6f + g * 0.4f);
            }else if(rand.chance(0.5)){
                Draw.color(shade, 0.1f, 0.1f, 0.25f + g * 0.4f);
            }else{
                Draw.color(0.1f, shade, shade, 0.2f + g * 0.3f);
            }
            Fill.crect(x + rand.range(40f * g), y + by, w, bh);
        }
        for(int i = 0; i < 40 * g; i++){
            Draw.color(rand.random(1f), rand.random(1f), rand.random(1f), rand.random(0.2f, 0.7f));
            Fill.crect(x + rand.random(w), y + rand.random(h), rand.random(4f, 40f), rand.random(2f, 10f));
        }
        Draw.color(0f, 0f, 0f, 0.18f + g * 0.3f);
        for(float sy = 0; sy < h; sy += 4f){
            Fill.crect(x, y + sy, w, 1.5f);
        }
    }

    private void drawTerminal(){
        float blink = time >= recoverAt ? 0.5f : (Mathf.sin(time * 9f) > -0.6f ? 1f : 0.25f);
        String tail = null;
        if(recovery > 0 && recovery >= restore.length){
            int bars = (int)(progress * 24);
            tail = " [" + "#".repeat(bars) + "-".repeat(24 - bars) + "] " + (int)(progress * 100) + "%";
        }
        terminal(x, y, width, height, time, "CRITICAL LOW POWER", Tmp.c1.set(1f, 0.12f, 0.12f, blink), Tmp.c2.set(0.08f, 0f, 0f, 0.85f), Tmp.c3.set(0.6f, 0.08f, 0.08f, 1f), lines, lineColors, tail);
    }

    static void terminal(float x, float y, float w, float h, float time, String head, Color headColor, Color back, Color edge, Seq<String> lines, Seq<Color> colors, @Nullable String tail){
        float margin = Scl.scl(28f);

        Font title = Fonts.outline;
        float oldScale = title.getData().scaleX;
        boolean markup = title.getData().markupEnabled;
        title.getData().markupEnabled = false;
        title.getData().setScale(Scl.scl(3.2f));
        title.setColor(headColor);
        title.draw(head, x + w / 2f, y + h - margin, 0f, Align.center, false);
        title.getData().setScale(oldScale);
        title.getData().markupEnabled = markup;
        title.setColor(Color.white);

        Font mono = Fonts.monospace;
        float monoScale = mono.getData().scaleX;
        boolean monoMarkup = mono.getData().markupEnabled;
        mono.getData().markupEnabled = false;
        mono.getData().setScale(Scl.scl(0.9f));
        float lineH = mono.getLineHeight();
        float top = y + h - margin - Scl.scl(110f), bottom = y + margin;
        float left = x + margin, boxW = w * 0.58f;

        Draw.color(back);
        Fill.crect(left - Scl.scl(10f), bottom - Scl.scl(10f), boxW + Scl.scl(20f), top - bottom + Scl.scl(20f));
        Draw.color(edge);
        Lines.stroke(Scl.scl(2f));
        Lines.rect(left - Scl.scl(10f), bottom - Scl.scl(10f), boxW + Scl.scl(20f), top - bottom + Scl.scl(20f));

        int fit = Math.max((int)((top - bottom) / lineH) - 1, 1);
        int from = Math.max(lines.size - fit, 0);
        float ly = top - lineH * 0.2f;
        for(int i = from; i < lines.size; i++){
            mono.setColor(colors.get(i));
            String text = lines.get(i);
            if(i == lines.size - 1 && tail != null) text += tail;
            mono.draw(text, left, ly, boxW, Align.left, false);
            ly -= lineH;
        }
        if(Mathf.sin(time * 12f) > 0f){
            mono.setColor(Color.lightGray);
            mono.draw("_", left, ly, boxW, Align.left, false);
        }
        mono.getData().setScale(monoScale);
        mono.getData().markupEnabled = monoMarkup;
        mono.setColor(Color.white);
    }

    private static final String[] faults = {
        "[E] power bus 0x#: undervoltage (% mV)",
        "[E] planetary anchor lost: $",
        "[F] gravity well solver diverged, residual %",
        "[E] sector % out of bounds",
        "[E] NullPointerException at world.tile(%, %)",
        "[E] save checksum mismatch: 0x#",
        "[W] battery cell % reports empty",
        "[E] content 'modularis-#' failed to resolve",
        "[E] no ground under unit %",
        "[F] thread 'logic' not responding",
        "[E] orbit of $ undefined",
        "[E] core link timed out after % ms",
        "[W] heat sink % saturated",
        "[E] reactor % offline",
        "[E] light source not found",
        "[F] segmentation fault at 0x#",
        "[E] memory page 0x# unreadable",
        "[E] unit % has no team",
        "[W] clock drift +% ticks",
        "[E] tech tree root missing: $",
        "[E] schematic % references deleted block",
        "[F] kernel panic: attempted to read past end of world",
        "[E] I/O error on drive 0x#",
        "[W] fan % stalled",
        "[E] cannot allocate % bytes for sky",
    };

    private static final String[] restore = {
        "== emergency recovery ==",
        "rerouting reserve power: 0.3% available",
        "core memory: scanning... ok",
        "world data for $: unrecoverable",
        "falling back to last stable snapshot",
        "loading ... DONE?",
    };
}
