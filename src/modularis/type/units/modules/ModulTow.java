package modularis.type.units.modules;

public class ModulTow extends ModuleType{
    public ModulTow(String name){
        super(name);
        localizedName = "Tow Hitch";
        description = "Connects this machine to another tow hitch with a flexible cable. Click on the module, then select the nearest other module to connect them.";
        category = ModuleCategory.ability;
        w = h = 1;
        limit = 2;
    }
}
