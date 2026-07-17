package modularis.type.units.modules;

public class ModulTow extends ModuleType{
    public ModulTow(String name){
        super(name);
        localizedName = "Tow Hitch";
        description = "Connects this machine to another tow hitch with a flexible cable.";
        category = ModuleCategory.ability;
        w = h = 1;
        limit = 2;
        weight = 1.5f;
        health = 160f;
    }

    @Override
    public String bodySpriteName(){
        return "base1x1";
    }
}
