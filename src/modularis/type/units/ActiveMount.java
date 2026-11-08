package modularis.type.units;

import arc.util.*;

import modularis.type.units.modules.*;

public class ActiveMount{
    public final ModulActive type;

    public int maxCharges = 1, charges = 1;
    public float progress;

    public ActiveMount(ModulActive type){
        this.type = type;
    }

    public void update(float powerRatio){
        if(charges >= maxCharges){
            progress = 0f;
            return;
        }

        progress += Time.delta * powerRatio / Math.max(type.cooldown, 1f);
        if(progress >= 1f){
            progress = 0f;
            charges++;
        }
    }
}
