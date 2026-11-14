package modularis.type.units;

import arc.graphics.*;
import arc.math.*;
import mindustry.entities.units.*;
import mindustry.gen.*;
import mindustry.type.*;

import modularis.type.*;
import modularis.type.units.modules.*;

/** Modular cannon. Aircraft in the next update. **/
public class CannonWeapon extends Weapon{
    public final PlacedModule placed;
    public final ModulHinge hinge;
    public boolean drawsCannon;
    public float damageScale = 1f, speedScale = 1f, rangeScale = 1f;

    public CannonWeapon(PlacedModule placed, ModulHinge hinge){
        super("modularis-cannon");
        this.placed = placed;
        this.hinge = hinge;
        rotate = true;
        mirror = false;
        autoTarget = true;
        shootY = 0f;
    }

    @Override
    protected void handleBullet(Unit unit, WeaponMount mount, Bullet bullet){
        super.handleBullet(unit, mount, bullet);
        if(bullet == null) return;
        if(focused(unit)){
            bullet.remove();
            MapFlow.begin();
            return;
        }

        bullet.damage *= damageScale;
        bullet.vel.scl(speedScale);
        bullet.lifetime *= rangeScale / speedScale;
    }

    private boolean focused(Unit unit){
        if(placed.cannon == null || !placed.cannon.modules.contains(m -> m.type instanceof CannonPart c && c.kind == CannonPart.Kind.focus)) return false;
        if(!(unit instanceof ModularUnitEntity e) || e.stats() == null || e.stats().powerRatio < 0.999f) return false;
        CannonSpec spec = CannonSpec.compute(placed.cannon, hinge);
        return spec.ammo() == CannonCaliber.heal && spec.lines.contains(l -> l.caliber == 3 && l.length >= 1);
    }

    @Override
    public void drawOutline(Unit unit, WeaponMount mount){
    }

    @Override
    public void draw(Unit unit, WeaponMount mount){
        if(!drawsCannon || placed.cannon == null) return;

        float rotation = unit.rotation - 90f;
        float weaponRotation = rotation + mount.rotation;
        float recoilOffset = Mathf.pow(mount.recoil, recoilPow) * recoil;
        float wx = unit.x + Angles.trnsx(rotation, x, y) + Angles.trnsx(weaponRotation, 0f, -recoilOffset);
        float wy = unit.y + Angles.trnsy(rotation, x, y) + Angles.trnsy(weaponRotation, 0f, -recoilOffset);

        Color tint = unit instanceof ModularUnitEntity e && e.design != null ? e.design.colorOf(placed) : Color.white;
        ModulHinge.drawCannon(placed.cannon, wx, wy, weaponRotation, ModularUnitType.cellWorld(), tint, true, false);
    }
}
