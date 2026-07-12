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
    private boolean weaponReady;

    public ModulTurret(String name){
        super(name);
        category = ModuleCategory.weapon;
        slot = SlotType.weapon;
        weight = 2f;
        health = 70f;
        powerUse = 0.8f;
    }

    public void ensureWeapon(){
        if(weaponReady || weapon == null) return;
        weaponReady = true;

        weapon.mirror = false;
        weapon.autoTarget = true;
        weapon.controllable = false;

        if(weapon.bullet != null) weapon.bullet.init();
        weapon.init();
        weapon.load();

        //normalisation that UnitType.init() would normally do for weapons in a unit's
        //weapon list - we bypass it, so replicate it here. Without this recoilTime stays
        //-1 and the recoil decays the wrong way, making the barrel slide off infinitely.
        if(weapon.recoilTime < 0f) weapon.recoilTime = weapon.reload;
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
