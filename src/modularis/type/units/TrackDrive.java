package modularis.type.units;

import arc.math.*;

public class TrackDrive{
    public enum Mode{
        /** Thrust along the hull while turning towards the request, so moving hulls turn in an arc. */
        arc,
        /** No thrust, just turn. */
        pivot,
        /** Thrust out of the back of the hull. */
        reverse
    }

    public static final float reverseEngage = 135f;
    public static final float reverseRelease = 100f;
    public static final float pivotAngle = 25f;
    public static final float movingShare = 0.15f;
    public static final float reverseSpeed = 0.6f;

    public static Mode decide(float rotation, float target, float forward, float top, boolean reversing, boolean canReverse){
        float off = Angles.angleDist(rotation, target);

        if(canReverse && off >= (reversing ? reverseRelease : reverseEngage)) return Mode.reverse;

        return Math.abs(forward) < top * movingShare && off > pivotAngle ? Mode.pivot : Mode.arc;
    }
}
