package modularis.content;

import javax.sound.sampled.Line;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.graphics.*;

import modularis.type.units.*;
import modularis.type.units.modules.*;

public class MdlFX{

    public static Effect bloodPuddle, workerBuild, wheelDust, menderPulse, turboSmoke,
        moduleDebrisFly, moduleDebrisRest, neoplasmLaserChargeSmall, shootFlame, drillSmoke, icbmFlame, nukeBlast;

    public static final float debrisLife = 60f * 120f;
    public static final float debrisFlyTime = 55f;

    public static class Debris{
        public final ModuleType type;
        public final float dist, spin;

        public Debris(ModuleType type, float dist, float spin){
            this.type = type;
            this.dist = dist;
            this.spin = spin;
        }
    }

    private static boolean loaded;

    public static void load(){
        if(loaded) return;
        loaded = true;

        shootFlame = new Effect(32f, 90f, e -> {
            Draw.color(Pal.lightFlame, Pal.darkFlame, Color.valueOf("55555500"), e.fin());
    
            Angles.randLenVectors(e.id, 12, e.finpow() * 60f, e.rotation, 14f, (x, y) -> {
                Fill.circle(e.x + x, e.y + y, 0.65f + e.fout() * 1.5f);
            });
        }).followParent(false);

        icbmFlame = new Effect(23f, 90f, e -> {
            Draw.color(Pal.lightFlame, Pal.darkFlame, Color.valueOf("55555500"), e.fin());

            Fill.circle(e.x, e.y, 0.3f + e.fout() * 1.5f);
        }).followParent(false);

        nukeBlast = new Effect(100f, e -> {
            float radius = 300;

            float flash = Mathf.clamp(1f - e.fin() * 5f);
            if(flash > 0f){
                Draw.color(Color.white);
                Draw.alpha(flash);
                Fill.circle(e.x, e.y, radius * 0.55f * (0.3f + flash * 0.7f));
            }

            for(int i = 0; i < 2; i++){
                float p = Mathf.clamp(e.fin() * 1.15f - i * 0.12f);
                if(p <= 0f) continue;
                float eased = 1f - (1f - p) * (1f - p);

                Draw.color(Color.white, Pal.lightOrange, p);
                Draw.alpha((1f - p) * (i == 0 ? 1f : 0.6f));
                Lines.stroke((1f - p) * 5f + 0.6f);
                Lines.circle(e.x, e.y, radius * eased);
            }

            float dustP = Mathf.clamp(e.fin() * 0.9f);
            Draw.color(Pal.darkFlame, Color.valueOf("2b2624"), dustP);
            Draw.alpha((1f - dustP * 0.6f) * e.fout() * 0.5f);
            Lines.stroke(radius * 0.05f * dustP + 1f);
            Lines.circle(e.x, e.y, radius * (0.15f + dustP * 0.8f));

            Draw.reset();
        }).layer(Layer.flyingUnit + 2f);
        nukeBlast.clip = 900f;

        neoplasmLaserChargeSmall = new Effect(40f, 100f, e -> {
            Draw.color(Pal.neoplasm1);
            Lines.stroke(e.fin() * 2f);
            Lines.circle(e.x, e.y, e.fout() * 50f);
        }).followParent(true).rotWithParent(true);

        //phase 1: tumbling through the air, drawn above the units
        moduleDebrisFly = new Effect(debrisFlyTime, e -> {
            if(!(e.data instanceof Debris d)) return;
            TextureRegion reg = d.type.region();
            if(reg == null || !reg.found()) return;

            float ft = e.fin();
            float p = 1f - (1f - ft) * (1f - ft); //ease-out slide

            float cell = ModularUnitType.cellWorld();
            float dw = d.type.w * cell, dh = d.type.h * cell;

            float px = e.x + Angles.trnsx(e.rotation, d.dist * p);
            float py = e.y + Angles.trnsy(e.rotation, d.dist * p);

            float hop = Mathf.slope(ft);   //0 -> 1 -> 0, the arc through the air
            float scl = 1f + hop * 0.35f;
            float rot = d.spin * p;

            //shadow drops away below as the piece rises
            Draw.color(0f, 0f, 0f, 0.28f);
            Draw.rect(reg, px + hop * 4f, py - hop * 4f, dw, dh, rot);

            Draw.color(Color.white, Color.valueOf("6b6560"), 0.25f);
            Draw.rect(reg, px, py, dw * scl, dh * scl, rot);

            //sparks as it tears free
            Fx.rand.setSeed(e.id);
            Draw.color(Pal.lightOrange, Color.gray, ft);
            Draw.alpha(1f - ft);
            for(int i = 0; i < 4; i++){
                float a = Fx.rand.random(360f);
                float dd = Fx.rand.random(2f, 10f) * ft;
                Fill.circle(px + Angles.trnsx(a, dd), py + Angles.trnsy(a, dd),
                    (1.5f + Fx.rand.random(1.4f)) * (1f - ft));
            }
            Draw.reset();
        }).layer(Layer.flyingUnit + 1f);
        moduleDebrisFly.clip = 260f;

        //phase 2: settled on the ground, drawn under the units, fades out at the end
        moduleDebrisRest = new Effect(debrisLife, e -> {
            if(!(e.data instanceof ModuleType type)) return;
            TextureRegion reg = type.region();
            if(reg == null || !reg.found()) return;

            float cell = ModularUnitType.cellWorld();
            float dw = type.w * cell, dh = type.h * cell;
            float fade = Mathf.clamp((e.lifetime - e.time) / 90f);

            Draw.color(0f, 0f, 0f, 0.28f * fade);
            Draw.rect(reg, e.x + 1.5f, e.y - 1.5f, dw, dh, e.rotation);

            Draw.color(Color.white, Color.valueOf("6b6560"), 0.25f);
            Draw.alpha(fade);
            Draw.rect(reg, e.x, e.y, dw, dh, e.rotation);
            Draw.reset();
        }).layer(Layer.debris);
        moduleDebrisRest.clip = 260f;

        turboSmoke = new Effect(54f, e -> {
            Fx.rand.setSeed(e.id);
            Draw.color(e.color, Color.valueOf("2b2624"), e.fin());
            Draw.alpha(0.5f * e.fout());
            for(int i = 0; i < 3; i++){
                float ang = Fx.rand.random(360f);
                float dst = Fx.rand.random(1f, 6f) * e.fin();
                float rx = e.x + Angles.trnsx(ang, dst);
                //smoke drifts upward as it ages
                float ry = e.y + Angles.trnsy(ang, dst) + e.fin() * 5f;
                Fill.circle(rx, ry, (1.7f + Fx.rand.random(2.2f)) * e.fout());
            }
            Draw.reset();
        });

        menderPulse = new Effect(42f, e -> {
            Draw.color(e.color);
            Lines.stroke(2.4f * e.fout());
            Lines.circle(e.x, e.y, e.rotation * e.fin());
            Draw.alpha(0.5f * e.fout());
            Lines.stroke(1f * e.fout());
            Lines.circle(e.x, e.y, e.rotation * e.fin() * 0.7f);
            Draw.reset();
        });
        menderPulse.clip = 600f;

        //grinding dust thrown up by a drill chewing into ore; e.color is the ore's colour
        drillSmoke = new Effect(40f, e -> {
            Fx.rand.setSeed(e.id);
            Draw.color(e.color, Color.valueOf("4a423d"), e.fin());
            Draw.alpha(0.55f * e.fout());
            for(int i = 0; i < 3; i++){
                float ang = Fx.rand.random(360f);
                float dst = Fx.rand.random(1f, 7f) * e.fin();
                float rx = e.x + Angles.trnsx(ang, dst);
                //the dust drifts up as it settles
                float ry = e.y + Angles.trnsy(ang, dst) + e.fin() * 3f;
                Fill.circle(rx, ry, (1.3f + Fx.rand.random(1.8f)) * e.fout());
            }
            Draw.reset();
        }).layer(Layer.flyingUnit - 1f);

        wheelDust = new Effect(34f, e -> {
            Fx.rand.setSeed(e.id);
            Draw.color(Color.valueOf("8a7b63"), Color.valueOf("5a4c40"), e.fin());
            Draw.alpha(0.6f * e.fout());
            for(int i = 0; i < 2; i++){
                float ang = e.rotation + 180f + Fx.rand.range(45f);
                float dst = Fx.rand.random(1f, 5f) * e.fin();
                float rx = e.x + Angles.trnsx(ang, dst);
                float ry = e.y + Angles.trnsy(ang, dst);
                Fill.circle(rx, ry, (1.4f + Fx.rand.random(1.6f)) * e.fout());
            }
            Draw.reset();
        }).layer(Layer.debris);

        workerBuild = new Effect(46f, e -> {
            Draw.z(Layer.effect);
            Fx.rand.setSeed(e.id);

            float fin = e.fin();
            float fout = e.fout();

            for(int i = 0; i < 2; i++){
                float ang = Fx.rand.random(360f);
                float dst = Fx.rand.random(3f, 10f) * fin;
                float x = e.x + Angles.trnsx(ang, dst);
                float y = e.y + Angles.trnsy(ang, dst);

                Draw.color(Color.valueOf("6b5a4e"), fout * 0.55f);
                Fill.circle(x, y, (1.6f + Fx.rand.random(1.2f)) * fout);
            }

            for(int i = 0; i < 2; i++){
                float ang = Fx.rand.random(360f);
                float dst = Fx.rand.random(2f, 7f) * fin;
                float x = e.x + Angles.trnsx(ang, dst);
                float y = e.y + Angles.trnsy(ang, dst) + fin * 3f;

                Draw.color(Color.valueOf("2b2624"), fout * 0.35f);
                Fill.circle(x, y, (2.1f + Fx.rand.random(1.6f)) * fout);
            }

            if(fin < 0.65f){
                int sparks = 1 + Fx.rand.random(2);
                Draw.color(Pal.accent, Color.valueOf("ffb45c"), fin);
                Lines.stroke(1.1f * fout);
                for(int i = 0; i < sparks; i++){
                    float ang = Fx.rand.random(360f);
                    float len = Fx.rand.random(2f, 5.5f) * fout;
                    float x = e.x + Fx.rand.range(2f);
                    float y = e.y + Fx.rand.range(2f);
                    Lines.lineAngle(x, y, ang, len);
                }
            }

            Draw.reset();
        });
    }
}
