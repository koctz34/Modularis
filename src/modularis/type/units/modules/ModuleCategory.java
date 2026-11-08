package modularis.type.units.modules;

import arc.graphics.*;

/** Groups modules in the editor's part list. */
public enum ModuleCategory{
    base("Base", Color.valueOf("b0b2ba")),
    root("Root", Color.valueOf("ffd37f")),
    engine("Engine", Color.valueOf("7fd0ff")),
    wheel("Movement", Color.valueOf("d0a06a")),
    weapon("Weapon", Color.valueOf("ff7f7f")),
    ability("Ability", Color.valueOf("84f491")),
    active("Active", Color.valueOf("6cb8ff")),

    //cannon parts
    barrel("Barrels", Color.valueOf("ff9f6b")),
    breech("Breeches", Color.valueOf("ffd37f")),
    cannonArmor("Armor", Color.valueOf("b0b2ba")),
    cannonGear("Equipment", Color.valueOf("84f491"));

    public final String title;
    public final Color color;

    ModuleCategory(String title, Color color){
        this.title = title;
        this.color = color;
    }
}
