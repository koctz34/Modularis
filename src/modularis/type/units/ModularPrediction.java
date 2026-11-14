package modularis.type.units;

import arc.math.*;
import arc.util.*;

import static mindustry.Vars.*;

public class ModularPrediction{
    private static final float tickMillis = 1000f / 60f;

    private static final float posGain = 0.12f, contactPosGain = 0.015f;
    private static final float velGain = 0.2f, contactVelGain = 0.02f;
    private static final float rotGain = 0.25f, contactRotGain = 0.04f;
    private static final float snapDistance = 6f * 8f;
    private static final float maxLeadTicks = 30f;
    private static final float maxRotRate = 12f;
    private static final float contactMargin = 10f;
    private static final float maxContactWindow = 90f;

    private boolean has;
    private float sx, sy, svx, svy, srot, srotRate;
    private float at;
    private float spacing = 12f;
    private float contactUntil = -1f;

    private static float ping(){
        return netClient == null ? 0f : netClient.getPing();
    }

    public void reset(){
        has = false;
    }

    public boolean has(){
        return has;
    }

    public void snapshot(float x, float y, float rotation, float vx, float vy){
        float now = Time.time;
        if(has){
            float gap = Math.max(now - at, 1f);
            spacing = Mathf.lerp(spacing, Math.min(gap, 120f), 0.3f);
            float turned = ((rotation - srot) % 360f + 540f) % 360f - 180f;
            srotRate = Mathf.clamp(turned / gap, -maxRotRate, maxRotRate);
        }else{
            srotRate = 0f;
        }
        sx = x; sy = y; srot = rotation; svx = vx; svy = vy;
        at = now;
        has = true;
    }

    public void contact(){
        float window = ping() / tickMillis + spacing + contactMargin;
        contactUntil = Math.max(contactUntil, Time.time + Math.min(window, maxContactWindow));
    }

    public boolean inContact(){
        return Time.time < contactUntil;
    }

    public void step(ModularUnitEntity e){
        if(!has) return;

        float age = Time.time - at;
        float lead = Math.min(ping() / 2f / tickMillis, maxLeadTicks);
        float ahead = Math.min(age + lead, maxLeadTicks + spacing);
        float ex = sx + svx * ahead, ey = sy + svy * ahead;
        float er = srot + srotRate * Math.min(ahead, spacing * 2f);

        boolean contact = inContact();
        float kv = Math.min((contact ? contactVelGain : velGain) * Time.delta, 1f);
        e.vel.x += (svx - e.vel.x) * kv;
        e.vel.y += (svy - e.vel.y) * kv;
        e.move(e.vel.x * Time.delta, e.vel.y * Time.delta);

        float dx = ex - e.x, dy = ey - e.y;
        if(dx * dx + dy * dy > snapDistance * snapDistance){
            e.x = ex;
            e.y = ey;
            e.vel.set(svx, svy);
            e.rotation = er;
            return;
        }
        float kp = Math.min((contact ? contactPosGain : posGain) * Time.delta, 1f);
        e.x += dx * kp;
        e.y += dy * kp;
        e.rotation = Mathf.slerp(e.rotation, er, Math.min((contact ? contactRotGain : rotGain) * Time.delta, 1f));
    }
}
