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

    public static Effect bloodPuddle, workerBuild;

    private static boolean loaded;

    public static void load(){
        if(loaded) return;
        loaded = true;

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
