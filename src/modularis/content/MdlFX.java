package modularis.content;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.graphics.*;

/** Mod effects. Call {@link #load()} once from content init. */
public class MdlFX{

    public static Effect bloodPuddle, workerBuild, wheelDust, menderPulse, turboSmoke;

    private static boolean loaded;

    public static void load(){
        if(loaded) return;
        loaded = true;

        //exhaust smoke puffed out by a turbo heater; e.color tints the fresh smoke
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

        //expanding heal ring; e.rotation carries the radius, e.color the tint
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

        //kicked-up dust from a driving wheel (tank-tread style)
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
