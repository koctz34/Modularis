package modularis.content;

import modularis.type.units.*;

public class MdlUnits{
    public static ModularUnitType modularUnit;

    public static void load(){
        modularUnit = new ModularUnitType("modular-unit");
    }
}
