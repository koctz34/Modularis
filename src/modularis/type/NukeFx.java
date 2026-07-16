package modularis.type;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import mindustry.entities.*;
import mindustry.entities.effect.*;
import mindustry.graphics.*;
 
public class NukeFx{
    private static final Rand rand = new Rand();
    private static final Vec2 v = new Vec2();
 
    private static final Color
        cWhite     = Color.valueOf("fffdf0"),
        cYellow    = Color.valueOf("ffe25f"),
        cOrange    = Color.valueOf("ff8130"),
        cRed       = Color.valueOf("c92e17"),
        cDust      = Color.valueOf("9c8a72"),
        cSmoke     = Color.valueOf("5a4d46"),
        cSmokeDark = Color.valueOf("241f1d");
 
    public static Effect
 
    nukeFlash = new Effect(70f, 1_000_000f, e -> {
        float radius = e.rotation;

        float intensity = Mathf.pow(e.fout(), 2.6f);

        float dst = Mathf.dst(e.x, e.y, Core.camera.position.x, Core.camera.position.y);
        float falloff = Mathf.clamp(1f - dst / Math.max(radius * 9f, 1f));
 
        float a = intensity * falloff;
        if(a <= 0.002f) return;
 
        Draw.blend(Blending.additive);
        Draw.color(Tmp.c1.set(cWhite).lerp(cOrange, Mathf.clamp(e.fin() * 1.6f)));
        Draw.alpha(a);
        Fill.rect(Core.camera.position.x, Core.camera.position.y,
                  Core.camera.width + 8f, Core.camera.height + 8f);
        Draw.blend();
        Draw.reset();
    }).layer(Layer.overlayUI + 1f),
 
    nukeBlast = new Effect(260f, 6000f, e -> {
        float radius = e.rotation;
        float fin = e.fin(), fout = e.fout();
 
        float flash = Mathf.clamp(1f - fin * 5.5f);
        if(flash > 0f){
            Draw.blend(Blending.additive);
            Fill.light(e.x, e.y, 60, radius * (0.55f + flash * 1.05f),
                Tmp.c1.set(cWhite).a(flash),
                Tmp.c2.set(cYellow).a(0f));
            Draw.color(cWhite);
            Draw.alpha(flash);
            Fill.circle(e.x, e.y, radius * 0.34f * (0.4f + flash * 0.85f));
            Draw.blend();
        }
 
        float ballP = Interp.pow5Out.apply(Mathf.clamp(fin * 1.25f));
        float ballR = radius * (0.10f + ballP * 0.62f);
        float heat  = Mathf.clamp(fin * 2.3f);
        float cool  = Mathf.clamp((fin - 0.28f) / 0.42f);
 
        rand.setSeed(e.id);
        Draw.blend(Blending.additive);
        for(int i = 0; i < 32; i++){
            float ang = rand.random(360f);
            float d   = rand.random(0.15f, 1f);
            float sz  = ballR * rand.random(0.22f, 0.52f);
            v.trns(ang, ballR * d * (0.4f + ballP * 0.6f));
 
            Draw.color(Tmp.c1.set(cYellow).lerp(cRed, Mathf.clamp(heat + rand.range(0.18f))));
            Draw.alpha(Mathf.clamp(1f - cool * 1.4f) * 0.55f);
            Fill.circle(e.x + v.x, e.y + v.y, sz);
        }
        Draw.blend();
 
        if(cool > 0f){
            rand.setSeed(e.id + 7);
            for(int i = 0; i < 36; i++){
                float ang = rand.random(360f);
                float d   = rand.random(0.1f, 1.05f);
                v.trns(ang, ballR * d);
 
                Draw.color(Tmp.c1.set(cSmoke).lerp(cSmokeDark, rand.nextFloat()));
                Draw.alpha(Mathf.clamp(cool * 1.2f) * fout * 0.7f);
                Fill.circle(e.x + v.x, e.y + v.y,
                    ballR * rand.random(0.25f, 0.55f) * (0.6f + cool * 0.6f));
            }
        }
 
        float wil = Mathf.clamp((fin - 0.02f) / 0.38f);
        if(wil > 0f && wil < 1f){
            float wr = radius * 1.3f * Interp.pow3Out.apply(wil);
            float wa = Mathf.slope(wil);
 
            Draw.color(Color.white);
            Draw.alpha(wa * 0.26f);
            Fill.circle(e.x, e.y, wr);
 
            Draw.alpha(wa * 0.55f);
            Lines.stroke(radius * 0.03f);
            Lines.circle(e.x, e.y, wr);
        }
 
        for(int i = 0; i < 4; i++){
            float p = Mathf.clamp(fin * (1.35f - i * 0.13f) - i * 0.07f);
            if(p <= 0f || p >= 1f) continue;
 
            float eased = Interp.circleOut.apply(p);
            float r = radius * (0.85f + i * 0.62f) * eased;
 
            Draw.color(Tmp.c1.set(i == 0 ? cWhite : cOrange).lerp(cDust, p));
            Draw.alpha((1f - p) * (1f - p) * (i == 0 ? 1f : 0.45f));
            Lines.stroke(radius * 0.045f * (1f - p) + 0.7f);
            Lines.circle(e.x, e.y, r);
        }
 
        float shock = Mathf.clamp(fin * 1.1f);
        if(shock < 1f){
            Draw.color(cWhite);
            Draw.alpha((1f - shock) * 0.11f);
            Lines.stroke(radius * 0.35f * (1f - shock) + 2f);
            Lines.circle(e.x, e.y, radius * 2.2f * Interp.circleOut.apply(shock));
        }
 
        float dp = Interp.pow3Out.apply(Mathf.clamp(fin * 1.05f));
        rand.setSeed(e.id + 3);
        int dustn = 90;
        for(int i = 0; i < dustn; i++){
            float ang = i * (360f / dustn) + rand.range(4f);
            float rr  = radius * (0.25f + dp * (1.15f + rand.range(0.18f)));
            v.trns(ang, rr);
 
            Draw.color(Tmp.c1.set(cDust).lerp(cSmokeDark, Mathf.clamp(dp * 0.9f + rand.range(0.2f))));
            Draw.alpha(Mathf.clamp(fin * 5f) * fout * 0.85f);
            Fill.circle(e.x + v.x, e.y + v.y,
                radius * 0.075f * rand.random(0.5f, 1.4f) * (0.4f + dp));
        }
 
        Draw.blend(Blending.additive);
        rand.setSeed(e.id + 11);
        for(int i = 0; i < 55; i++){
            float ang   = rand.random(360f);
            float speed = rand.random(0.6f, 2.2f);
            float len   = radius * speed * Interp.pow5Out.apply(Mathf.clamp(fin * 1.3f));
            v.trns(ang, len);
 
            float px = e.x + v.x, py = e.y + v.y;
            float sz = radius * 0.022f * rand.random(0.5f, 1.6f) * Mathf.clamp(fout * 1.6f);
            if(sz <= 0.01f) continue;
 
            Draw.color(Tmp.c1.set(cWhite).lerp(cOrange, rand.nextFloat()));
            Draw.alpha(Mathf.pow(fout, 1.5f));
            Drawf.tri(px, py, sz * 2f, radius * 0.16f * speed * fout, ang + 180f);
            Fill.circle(px, py, sz);
        }
        Draw.blend();
 
        rand.setSeed(e.id + 23);
        for(int i = 0; i < 40; i++){
            float ang = rand.random(360f);
            float len = radius * rand.random(0.4f, 1.5f) * Interp.pow3Out.apply(Mathf.clamp(fin * 0.85f));
            v.trns(ang, len);
 
            Draw.color(Tmp.c1.set(cSmoke).lerp(cSmokeDark, rand.nextFloat()));
            Draw.alpha(fout * 0.5f * Mathf.clamp(fin * 3f));
            Fill.circle(e.x + v.x, e.y + v.y,
                radius * rand.random(0.05f, 0.16f) * (0.3f + Mathf.clamp(fin * 1.6f)));
        }
 
        float arcp = Mathf.clamp(fin * 4f);
        if(arcp < 1f){
            Draw.blend(Blending.additive);
            rand.setSeed(e.id + 31);
            Draw.color(cWhite, cYellow, arcp);
            Draw.alpha(1f - arcp);
            Lines.stroke(radius * 0.012f + 0.5f);
 
            for(int i = 0; i < 9; i++){
                float ang = rand.random(360f);
                float len = radius * rand.random(0.7f, 1.6f);
                int pts = 9;
 
                Lines.beginLine();
                for(int j = 0; j <= pts; j++){
                    float f = j / (float)pts;
                    v.trns(ang + Mathf.sin(f * 8f + i, 1f, 24f) * f,
                           len * f * (0.4f + arcp * 0.7f));
                    Lines.linePoint(e.x + v.x, e.y + v.y);
                }
                Lines.endLine();
            }
            Draw.blend();
        }
 
        Drawf.light(e.x, e.y, radius * (1.2f + fin * 1.6f),
            Tmp.c1.set(cOrange).lerp(cRed, fin), Mathf.pow(fout, 0.6f) * 0.9f);
 
        Draw.reset();
    }).layer(Layer.flyingUnit + 4f),
 
    nukeCloud = new Effect(900f, 6000f, e -> {
        float radius = e.rotation;
        float fin = e.fin();
 
        float grow = Interp.pow2Out.apply(Mathf.clamp(fin * 2.4f));
        float alpha = Mathf.clamp((1f - fin) * 2.2f) * Mathf.clamp(fin * 12f);
        if(alpha <= 0.002f) return;
 
        rand.setSeed(e.id + 77);
        for(int i = 0; i < 80; i++){
            float base = rand.random(360f);
            float spin = fin * 55f * rand.random(0.4f, 1f);
            float d = radius * (0.3f + rand.random(0.85f)) * grow;
            v.trns(base + spin, d);
 
            Draw.color(Tmp.c1.set(cSmoke).lerp(cSmokeDark, rand.nextFloat()));
            Draw.alpha(alpha * rand.random(0.25f, 0.55f));
            Fill.circle(e.x + v.x, e.y + v.y,
                radius * rand.random(0.12f, 0.3f) * (0.5f + grow * 0.7f));
        }
 
        float ember = Mathf.clamp(1f - fin * 2.5f);
        if(ember > 0f){
            Draw.blend(Blending.additive);
            rand.setSeed(e.id + 91);
            for(int i = 0; i < 14; i++){
                v.trns(rand.random(360f), radius * rand.random(0f, 0.32f));
                Draw.color(Tmp.c1.set(cRed).lerp(cOrange, Mathf.absin(Time.time + i * 9f, 7f, 1f)));
                Draw.alpha(ember * 0.4f);
                Fill.circle(e.x + v.x, e.y + v.y, radius * rand.random(0.06f, 0.15f));
            }
            Draw.blend();
            Drawf.light(e.x, e.y, radius * 0.9f, cRed, ember * 0.5f);
        }
 
        Draw.reset();
    }).layer(Layer.flyingUnit + 7f),
 
    nuke = new MultiEffect(nukeFlash, nukeBlast, nukeCloud);
}
