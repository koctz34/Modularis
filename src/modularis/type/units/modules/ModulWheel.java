package modularis.type.units.modules;

/**
 * Wheel or track - a drive part that needs ground contact.
 */
public class ModulWheel extends ModulPropulsor{
    public ModulWheel(String name){
        super(name);
        mode = PropulsionMode.ground;
    }
}
