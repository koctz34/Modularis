package modularis.type.units.modules;

/**
 * Base module - simple armour panel. It is the structural foundation other
 * modules attach to and mostly just contributes health and weight.
 */
public class ModulBase extends ModuleType{
    public ModulBase(String name){
        super(name);
        category = ModuleCategory.base;
        weight = 1f;
        health = 160f;
    }
}
