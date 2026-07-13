package modularis.type.units;

import modularis.type.units.modules.*;

public class PulsarMount{
    public final PlacedModule placed;
    public final ModulPulsar type;

    public float charge;

    public PulsarMount(PlacedModule placed, ModulPulsar type){
        this.placed = placed;
        this.type = type;
    }
}
