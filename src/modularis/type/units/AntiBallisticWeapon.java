package modularis.type.units;

import mindustry.gen.*;
import mindustry.type.weapons.*;

/**
 * Dedicated ABM weapon: sees ONLY ballistic threats, not ordinary bullets.
 */
public class AntiBallisticWeapon extends PointDefenseWeapon{
    public AntiBallisticWeapon(String name){
        super(name);
    }

    public AntiBallisticWeapon(){
        super();
    }

    @Override
    protected Teamc findTarget(Unit unit, float x, float y, float range, boolean air, boolean ground){
        return Groups.bullet.intersect(x - range, y - range, range * 2, range * 2)
            .min(b -> b.team != unit.team && b.type() instanceof ICBM, b -> b.dst2(x, y));
    }

    @Override
    protected boolean checkTarget(Unit unit, Teamc target, float x, float y, float range){
        return !(target.within(unit, range) && target.team() != unit.team
            && target instanceof Bullet bullet && bullet.type instanceof ICBM);
    }
}
