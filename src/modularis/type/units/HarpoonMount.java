package modularis.type.units;

import arc.util.*;
import mindustry.entities.units.*;
import mindustry.gen.*;
import mindustry.type.*;

public class HarpoonMount extends WeaponMount{
    public enum State{
        loaded,
        flying,
        hooked,
        returning
    }

    public State state = State.loaded;
    public @Nullable Bullet shot;
    public @Nullable Unit target;
    public float anchorX, anchorY;
    public float rope, strain, hookTime;
    public float headX, headY;

    public HarpoonMount(Weapon weapon){
        super(weapon);
    }

    public void takeFrom(HarpoonMount other){
        state = other.state;
        shot = other.shot;
        target = other.target;
        anchorX = other.anchorX;
        anchorY = other.anchorY;
        rope = other.rope;
        strain = other.strain;
        hookTime = other.hookTime;
        headX = other.headX;
        headY = other.headY;
        reload = other.reload;
        if(shot != null && shot.data == other) shot.data = this;
    }
}
