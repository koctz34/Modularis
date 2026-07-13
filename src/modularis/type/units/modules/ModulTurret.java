package modularis.type.units.modules;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.entities.units.*;
import mindustry.gen.*;
import mindustry.type.*;

import modularis.type.units.*;
    
public class ModulTurret extends ModuleType{
    /** The backing weapon. Set this in the content loader. */
    public Weapon weapon;
    /** Sprite name used for the turret's armour base. */
    public String baseSprite = "base1x1";

    private TextureRegion baseRegion;
    private boolean bulletReady, weaponReady;

    public ModulTurret(String name){
        super(name);
        category = ModuleCategory.weapon;
        slot = SlotType.weapon;
        weight = 2f;
        health = 70f;
        powerUse = 0.8f;
    }

    /**
     * Weapon/bullet logic setup. Needs NO atlas, so it can run as early as UnitType.init()
     * (which is where we work out the machine's firing range for the AI).
     */
    public void ensureBullet(){
        if(bulletReady || weapon == null) return;
        bulletReady = true;

        weapon.mirror = false;
        weapon.autoTarget = true;
        weapon.controllable = true;

        if(weapon.bullet != null) weapon.bullet.init();
        weapon.init();

        //normalisation that UnitType.init() would normally do for weapons in a unit's
        //weapon list - we bypass it, so replicate it here. Without this recoilTime stays
        //-1 and the recoil decays the wrong way, making the barrel slide off infinitely.
        if(weapon.recoilTime < 0f) weapon.recoilTime = weapon.reload;
    }

    /** Full setup, including sprite loading (needs a loaded atlas). */
    public void ensureWeapon(){
        ensureBullet();
        if(weaponReady || weapon == null) return;
        weaponReady = true;
        weapon.load();
    }

    /** Firing range of this turret, or 0 if it has no weapon. */
    public float range(){
        ensureBullet();
        return weapon == null || weapon.bullet == null ? 0f : weapon.bullet.range;
    }

    public WeaponMount createMount(float localX, float localY){
        ensureWeapon();
        if(weapon == null) return null;

        Weapon copy = weapon.copy();
        copy.x = localX;
        copy.y = localY;
        copy.mirror = false;
        copy.controllable = true;
        copy.autoTarget = true;
        return new WeaponMount(copy);
    }

    public TextureRegion baseRegion(){
        if(baseRegion == null) baseRegion = Core.atlas.find("modularis-" + baseSprite);
        return baseRegion;
    }

    @Override
    public TextureRegion bodyRegion(){
        return baseRegion();
    }

    @Override
    public void drawTop(Unit unit, PlacedModule placed, float x, float y, float w, float h, float rotation){
        //A turret with no free slot gets no WeaponMount, so the Weapon system never draws its
        //barrel. Draw a dead one here, locked to the hull and greyed out, so it reads as
        //"bolted on but not wired in" rather than as a missing sprite.
        if(unit instanceof ModularUnitEntity e && e.design != null && !e.design.isActive(placed)){
            Draw.color(Color.gray);
            Draw.rect(region(), x, y, w, h, rotation);
            Draw.color();
            return;
        }

        //in-world barrels are drawn by the Weapon system; only draw a static preview in the editor
        if(unit != null) return;
        Draw.color(Color.white);
        Draw.rect(region(), x, y, w, h, rotation);
        Draw.color();
    }

    @Override
    public void buildStats(Table table){
        if(weapon == null) return;
        stat(table, "Reload", Strings.autoFixed(60f / Math.max(1f, weapon.reload), 1) + "/s");
        if(weapon.bullet != null){
            stat(table, "Damage", Strings.autoFixed(weapon.bullet.damage, 0));
        }
    }
}
