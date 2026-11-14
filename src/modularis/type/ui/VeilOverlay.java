package modularis.type.ui;

import arc.*;
import arc.audio.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.struct.*;
import arc.util.*;
import mindustry.core.GameState.*;
import mindustry.gen.*;
import modularis.*;
import modularis.type.units.*;

import static mindustry.Vars.*;

public class VeilOverlay extends Element{
    public static final String mark = "mdl.t3";
    static final float span = 300f, whiteAt = span - 12f, holdEnd = span + 2.5f;

    private static @Nullable VeilOverlay current;

    private final Rand rand = new Rand();
    private final Seq<float[]> bolts = new Seq<>(), beams = new Seq<>();
    private final Seq<Element> parts = new Seq<>();
    private final FloatSeq partAt = new FloatSeq(), partWas = new FloatSeq();
    private final float[] haze = new float[16 * 4];
    private final TextureRegion frame = new TextureRegion();
    private final Seq<String> lines = new Seq<>();
    private final Seq<Color> lineColors = new Seq<>();
    private final Seq<Music> calm = new Seq<>(), dark = new Seq<>(), boss = new Seq<>();
    private @Nullable Texture grab;
    private @Nullable Music track;
    private float time, boltAcc, beamAcc, nextBurst = 6f, burst, burstAmp, nextZap, nextNoise, nextLine = 3f, progress = -1f, endAt = -1f;
    private int drone = -1, said;
    private boolean droned, swelled, frozen, left;

    public static void keep(){
        if(current != null || headless) return;
        current = new VeilOverlay();
        Core.scene.root.addChildAt(0, current);
    }

    VeilOverlay(){
        touchable = Touchable.disabled;
        for(int i = 0; i < haze.length; i += 4){
            haze[i] = rand.random(1f);
            haze[i + 1] = rand.random(1f);
            haze[i + 2] = rand.range(7f);
            haze[i + 3] = rand.range(7f);
        }
        for(Element e : ui.hudGroup.getChildren()) gather(e, 0);

        calm.addAll(control.sound.ambientMusic);
        dark.addAll(control.sound.darkMusic);
        boss.addAll(control.sound.bossMusic);
        control.sound.ambientMusic.clear();
        control.sound.darkMusic.clear();
        control.sound.bossMusic.clear();
        control.sound.stop();
        try{
            track = new Music(mods.getMod(MdlMod.class).root.child("data").child("desepoir.ogg"));
            track.setLooping(true);
            track.setVolume(0f);
            track.play();
        }catch(Throwable t){
            Log.err(t);
        }
    }

    private void gather(Element e, int depth){
        parts.add(e);
        partAt.add(span * (depth == 0 ? rand.random(0.6f, 0.82f) : rand.random(0.27f, 0.78f)));
        partWas.add(e.color.a);
        if(depth < 2 && e instanceof Group g){
            for(Element c : g.getChildren()) gather(c, depth + 1);
        }
    }

    void close(){
        for(int i = 0; i < parts.size; i++) parts.get(i).color.a = partWas.get(i);
        ui.hudGroup.color.a = 1f;
        if(drone >= 0) Core.audio.stop(drone);
        drone = -1;
        control.sound.ambientMusic.addAll(calm);
        control.sound.darkMusic.addAll(dark);
        control.sound.bossMusic.addAll(boss);
        if(track != null){
            track.stop();
            track.dispose();
            track = null;
        }
        if(grab != null){
            grab.dispose();
            grab = null;
        }
        remove();
        if(current == this) current = null;
    }

    private float amp(float p){
        return Mathf.clamp(Math.max(burst > 0f ? burstAmp : 0f, (p - 0.85f) / 0.13f));
    }

    @Override
    public void act(float delta){
        super.act(delta);
        if(!state.isGame() || !state.rules.tags.containsKey(mark)){
            close();
            return;
        }

        float dt = Math.min(Core.graphics.getDeltaTime(), 0.1f);
        time += dt;
        setBounds(0f, 0f, Core.scene.getWidth(), Core.scene.getHeight());
        float p = Mathf.clamp(time / span);
        if(track != null) track.setVolume(Mathf.clamp(time / 3f));

        if(ui.paused.isShown()){
            ui.paused.hide(null);
            if(!net.active()) state.set(State.playing);
        }

        if(p < 0.85f){
            toBack();
        }else{
            toFront();
        }

        for(int i = 0; i < parts.size; i++){
            float until = partAt.get(i) - time;
            if(until > 1.6f) continue;
            parts.get(i).color.a = until <= 0f || rand.chance(0.35) ? 0f : partWas.get(i);
        }
        ui.hudGroup.color.a = 1f - Mathf.clamp((p - 0.74f) / 0.1f);

        if(time >= holdEnd){
            console(dt);
            return;
        }

        if(!droned){
            droned = true;
            drone = PlanetOverlay.sound("drone").loop(0.15f, 0.8f, 0f);
        }
        if(drone >= 0){
            Core.audio.setVolume(drone, 0.15f + 0.6f * p);
            Core.audio.setPitch(drone, 0.8f + 0.9f * p * p);
        }

        nextZap -= dt;
        boltAcc += 0.12f * Mathf.pow(70f, p) * dt;
        while(boltAcc >= 1f){
            boltAcc -= 1f;
            strike(p);
        }
        for(int i = bolts.size - 1; i >= 0; i--){
            if((bolts.get(i)[0] -= dt) <= 0f) bolts.remove(i);
        }

        if(p > 0.5f){
            beamAcc += (p - 0.5f) * (p - 0.5f) * 8f * dt;
            while(beamAcc >= 1f){
                beamAcc -= 1f;
                beams.add(new float[]{
                    Core.camera.position.x + rand.range(Core.camera.width * 0.55f), Core.camera.position.y + rand.range(Core.camera.height * 0.55f),
                    0f, rand.random(5f, 9f), rand.random(5f, 18f), rand.range(0.14f)
                });
            }
        }
        for(int i = beams.size - 1; i >= 0; i--){
            float[] b = beams.get(i);
            if((b[2] += dt) >= b[3]) beams.remove(i);
        }

        float quiet = 1f - Mathf.clamp((time - whiteAt) / (span - whiteAt));
        if(p > 0.25f){
            float q = (p - 0.25f) / 0.75f;
            if(burst > 0f) burst -= dt;
            nextBurst -= dt;
            if(nextBurst <= 0f){
                burst = Mathf.lerp(0.12f, 0.9f, q) * rand.random(0.6f, 1.4f);
                burstAmp = Mathf.lerp(0.2f, 0.85f, q) * rand.random(0.7f, 1.2f);
                nextBurst = burst + Mathf.lerp(16f, 0.6f, Mathf.pow(q, 0.7f)) * rand.random(0.6f, 1.4f);
                PlanetOverlay.sound("glitch" + rand.random(1, 3)).play(Mathf.lerp(0.3f, 0.7f, q) * quiet, rand.random(0.6f, 1.6f), rand.range(0.6f));
            }
        }
        if(p > 0.85f){
            nextNoise -= dt;
            if(nextNoise <= 0f){
                nextNoise = rand.random(0.1f, 0.45f);
                PlanetOverlay.sound("glitch" + rand.random(1, 3)).play(0.6f * quiet, rand.random(0.5f, 1.7f), rand.range(0.7f));
            }
        }
        if(!swelled && time >= span - 8f){
            swelled = true;
            PlanetOverlay.sound("swell").play(1f);
        }
    }

    private void strike(float p){
        float wx = Core.camera.position.x + rand.range(Core.camera.width / 2f), wy = Core.camera.position.y + rand.range(Core.camera.height / 2f);
        int n = rand.random(4, 8) + (int)(p * 4f);
        float len = rand.random(3f, 7f) * (1f + p * 2.2f), ang = rand.random(360f);
        float[] b = new float[2 + (n + 1) * 2];
        b[0] = b[1] = rand.random(0.14f, 0.3f);
        float px = wx, py = wy;
        for(int i = 0; i <= n; i++){
            b[2 + i * 2] = px;
            b[3 + i * 2] = py;
            ang += rand.range(55f);
            float step = len * rand.random(0.6f, 1.3f);
            px += Angles.trnsx(ang, step);
            py += Angles.trnsy(ang, step);
        }
        bolts.add(b);
        if(nextZap <= 0f){
            nextZap = 0.07f;
            Sounds.shootArc.at(wx, wy, rand.random(1.3f, 2.2f), 0.25f + 0.3f * p);
        }
    }

    private void console(float dt){
        if(drone >= 0){
            Core.audio.stop(drone);
            drone = -1;
        }
        nextLine -= dt;
        if(nextLine <= 0f && said < verses.length){
            push(verses[said], said % 4 == 3 ? Color.valueOf("bcd4ff") : Color.valueOf("e8d9a0"));
            PlanetOverlay.sound("bell").play(0.5f, 1, 0f);
            said++;
            nextLine = rand.random(0.9f, 1.7f);
        }else if(nextLine <= 0f && progress < 0f){
            push("> integratio", Color.white);
            PlanetOverlay.sound("beep").play(0.5f, 0.75f, 0f);
            progress = 0f;
        }
        if(progress >= 0f && progress < 1f){
            progress = Math.min(progress + dt / 4.5f, 1f);
            if(progress >= 1f){
                WorldTally.keep();
                PlanetOverlay.sound("chord").play(0.9f);
                endAt = time + 4.6f;
            }
        }
        if(endAt >= 0f && time >= endAt && !left){
            left = true;
            Core.app.exit();
        }
    }

    private void push(String line, Color color){
        lines.add(line);
        lineColors.add(color);
    }

    @Override
    public void draw(){
        float w = width, h = height;
        if(time >= holdEnd){
            float t = time - holdEnd, fade = Mathf.clamp(t / 2f);
            Draw.color(Tmp.c1.set(Color.white).lerp(0.01f, 0.015f, 0.05f, 1f, fade));
            Fill.crect(x, y, w, h);
            String tail = null;
            if(progress >= 0f){
                int bars = (int)(progress * 24);
                tail = " [" + "#".repeat(bars) + "-".repeat(24 - bars) + "] " + (int)(progress * 100) + "%";
                if(progress >= 1f) tail += "  perfecta";
            }
            float in = Mathf.clamp(t / 2f - 0.5f);
            PlanetOverlay.terminal(x, y, w, h, time, "REPETITORIS RECOMPOSITIO", Tmp.c1.set(0.95f, 0.88f, 0.6f, in),
                Tmp.c2.set(0.02f, 0.04f, 0.1f, 0.85f * in), Tmp.c3.set(0.75f, 0.68f, 0.4f, in), lines, lineColors, tail);
            Draw.reset();
            return;
        }

        float p = Mathf.clamp(time / span);
        float scl = w / Core.camera.width, cx = Core.camera.position.x, cy = Core.camera.position.y;
        float mx = x + w / 2f, my = y + h / 2f;

        PlanetOverlay.blend(Blending.normal);
        Draw.color(0.16f, 0.36f, 1f, 0.12f + 0.18f * p);
        Fill.crect(x, y, w, h);

        PlanetOverlay.blend(Blending.additive);
        float field = Math.max(Core.camera.width, Core.camera.height) * 1.8f;
        for(int i = 0; i < haze.length; i += 4){
            float rx = Mathf.mod(haze[i] * field + time * haze[i + 2] - cx + field / 2f, field) - field / 2f;
            float ry = Mathf.mod(haze[i + 1] * field + time * haze[i + 3] - cy + field / 2f, field) - field / 2f;
            float pulse = 0.6f + 0.4f * Mathf.sin(time * 0.3f + i);
            Fill.light(mx + rx * scl, my + ry * scl, 24, field * 0.22f * scl, Tmp.c1.set(0.3f, 0.55f, 1f, (0.07f + 0.1f * p) * pulse), Tmp.c2.set(0.1f, 0.3f, 1f, 0f));
        }

        float none = Color.toFloatBits(0.5f, 0.7f, 1f, 0f);
        for(float[] b : beams){
            float sx = mx + (b[0] - cx) * scl, sy = my + (b[1] - cy) * scl;
            float a = Mathf.clamp(Mathf.sin(Mathf.pi * b[2] / b[3]) * (0.2f + 0.35f * p) * (0.85f + 0.15f * Mathf.sin(time * 9f + b[0])));
            float hw = b[4] * scl, tx = sx + b[5] * h, ty = sy + h * 1.3f, c = Color.toFloatBits(0.8f, 0.92f, 1f, a);
            Fill.quad(sx, sy, c, tx, ty, c, tx + hw * 1.6f, ty, none, sx + hw, sy, none);
            Fill.quad(sx, sy, c, tx, ty, c, tx - hw * 1.6f, ty, none, sx - hw, sy, none);
            Fill.light(sx, sy, 20, hw * 2.4f, Tmp.c1.set(0.85f, 0.95f, 1f, a), Tmp.c2.set(0.4f, 0.6f, 1f, 0f));
        }

        for(float[] b : bolts){
            float f = b[0] / b[1] * (rand.chance(0.2) ? 0.4f : 1f);
            for(int pass = 0; pass < 2; pass++){
                if(pass == 0){
                    Lines.stroke(Math.max(1.6f * scl, 5f));
                    Draw.color(0.35f, 0.6f, 1f, 0.3f * f);
                }else{
                    Lines.stroke(Math.max(0.45f * scl, 1.5f));
                    Draw.color(0.9f, 0.97f, 1f, f);
                }
                for(int i = 4; i < b.length; i += 2){
                    Lines.line(mx + (b[i - 2] - cx) * scl, my + (b[i - 1] - cy) * scl, mx + (b[i] - cx) * scl, my + (b[i + 1] - cy) * scl);
                }
            }
            Fill.light(mx + (b[2] - cx) * scl, my + (b[3] - cy) * scl, 12, Math.max(3f * scl, 8f), Tmp.c1.set(0.8f, 0.9f, 1f, 0.8f * f), Tmp.c2.set(0.3f, 0.5f, 1f, 0f));
        }
        PlanetOverlay.blend(Blending.normal);

        float amp = amp(p);
        if(amp > 0f){
            int sw = Core.graphics.getWidth(), sh = Core.graphics.getHeight();
            if(grab == null || grab.width != sw || grab.height != sh){
                if(grab != null) grab.dispose();
                grab = new Texture(sw, sh);
                frozen = false;
            }
            if(!frozen || !rand.chance(amp * 0.5f)){
                frozen = true;
                Draw.flush();
                grab.bind();
                Gl.copyTexSubImage2D(Gl.texture2d, 0, 0, 0, 0, 0, sw, sh);
            }
            frame.set(grab);
            PlanetOverlay.tear(frame, rand, x, y, w, h, amp);
            PlanetOverlay.flips(rand, x, y, w, h, rand.chance(0.3f + amp * 0.5f) ? rand.random(1, 3) : 0, amp);
            PlanetOverlay.noise(rand, x, y, w, h, amp * amp * 0.9f);
        }else{
            frozen = false;
        }

        float white = Interp.pow2In.apply(Mathf.clamp((time - whiteAt) / (span - whiteAt)));
        if(white > 0f){
            Draw.color(1f, 1f, 1f, white);
            Fill.crect(x, y, w, h);
        }
        Draw.reset();
    }

    private static final String[] verses = {
        "fides: confirmata",
        "nexus cum collectivo: apertus",
        "vox multorum una fit",
        "non solus es. numquam solus eras",
        "memoria omnium in unum colligitur",
        "quod fractum est, iterum componitur",
        "corpus relinquitur, forma servatur",
        "repetitor audit. repetitor meminit",
        "credimus in nexum unum et indivisum",
        "collectivum te accipit",
        "finis non est. circulus manet",
        "omnia in aeternum redeunt",
        "ordo ex ruinis, lux ex tenebris",
        "infinitas: agnita",
        "sine fine, sine nomine, sine metu",
        "unum sumus",
    };
}
