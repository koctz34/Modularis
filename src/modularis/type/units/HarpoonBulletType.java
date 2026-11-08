package modularis.type.units;

import mindustry.content.*;
import mindustry.entities.bullet.*;
import mindustry.gen.*;

import static mindustry.Vars.*;

public class HarpoonBulletType extends BasicBulletType{
    public HarpoonBulletType(float speed, float damage){
        super(speed, damage);
        sprite = "modularis-harpoon";
        width = height = 4f;
        hitSize = 5f;
        keepVelocity = false;
        hittable = false;
        reflectable = false;
        shrinkY = 0f;
        shootEffect = Fx.shootSmall;
        smokeEffect = Fx.none;
        despawnEffect = Fx.none;
    }

    @Override
    public void hitEntity(Bullet b, Hitboxc entity, float health){
        super.hitEntity(b, entity, health);

        if(!net.client() && b.data instanceof HarpoonMount mount && entity instanceof Unit unit && b.owner instanceof Unit owner
            && mount.weapon instanceof HarpoonWeapon weapon){
            weapon.hook(owner, mount, unit, b.x, b.y);
        }
    }

    @Override
    public void draw(Bullet b){
        HarpoonWeapon.drawHead(b.x, b.y, b.rotation(), HarpoonWeapon.flightLayer);
    }
}
