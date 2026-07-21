package modularis.type.units.modules;

import arc.graphics.*;
import mindustry.graphics.*;

/**
 * How a drive part holds the machine up, and therefore which physics apply to it.
 */
public enum PropulsionMode{
    /** Wheels and tracks. Needs ground contact; provides the traction that accelerates and turns. */
    ground("Ground", Pal.accent),
    /** Hover pads. Floats just clear of the floor - ignores terrain, but grips almost nothing. */
    hover("Hover", Color.valueOf("64f1ff")),
    /** Future. */
    air("Rotor", Color.valueOf("ffd37f"));

    public final String title;
    public final Color color;

    PropulsionMode(String title, Color color){
        this.title = title;
        this.color = color;
    }
}
