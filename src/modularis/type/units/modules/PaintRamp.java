package modularis.type.units.modules;

import arc.graphics.*;
import arc.math.*;

/**
 * The three-tone ramp a single paint colour expands into.
 */
public class PaintRamp{
    /** Shadows: hue back, more saturated, darker. */
    public static float darkHue = -11f, darkSat = 0.19f, darkVal = -0.30f;
    /** Highlights: hue forward, washed out, brighter. */
    public static float lightHue = 18.6f, lightSat = -0.195f, lightVal = 0.30f;

    public final Color dark = new Color(), mid = new Color(), light = new Color();

    public Color tone(int index){
        return index == 0 ? dark : index == 1 ? mid : light;
    }

    private static final PaintRamp shared = new PaintRamp();
    private static final float[] hsv = new float[3];

    private int cached;
    private boolean has;

    public static PaintRamp of(Color tint){
        shared.set(tint);
        return shared;
    }

    private void set(Color tint){
        if(has && tint.rgba() == cached) return;
        cached = tint.rgba();
        has = true;

        mid.set(tint);
        shade(tint, darkHue, darkSat, darkVal, dark);
        shade(tint, lightHue, lightSat, lightVal, light);
    }

    private static void shade(Color base, float dh, float ds, float dv, Color out){
        base.toHsv(hsv);
        out.fromHsv(Mathf.mod(hsv[0] + dh, 360f), Mathf.clamp(hsv[1] + ds), Mathf.clamp(hsv[2] + dv));
        out.a = base.a;
    }
}
