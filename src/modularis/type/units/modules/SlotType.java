package modularis.type.units.modules;

/**
 * The pool of slots a module draws from. Command cores ({@link ModulRoot}) provide the
 * slots; weapons, engines and abilities consume them. Modules with {@link #none} are
 * unrestricted (armour, drive parts, the core itself).
 */
public enum SlotType{
    none("None"),
    weapon("Weapon"),
    engine("Engine"),
    ability("Ability");

    public final String title;

    SlotType(String title){
        this.title = title;
    }
}
