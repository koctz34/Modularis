package modularis.type.units;

import arc.math.geom.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.entities.bullet.*;
import mindustry.entities.*;
import mindustry.gen.*;
import mindustry.world.blocks.*;

import static mindustry.Vars.*;

public class HealShellBulletType extends BasicBulletType{
    public float healPerDamage = 1.5f;

    public HealShellBulletType(float speed, float damage){
        super(speed, damage);
        collidesTeam = true;
        //makes the engine treat it as a healer: allied blocks are hit only when damaged
        healAmount = 1f;
        healEffect = Fx.healBlockFull;
    }

    public float healFor(Bullet b){
        return b.damage * healPerDamage;
    }

    @Override
    public void update(Bullet b){
        super.update(b);
        if(!b.isAdded() || b.hit) return;

        float pad = b.hitSize / 2f;
        Rect sweep = Tmp.r2.set(Math.min(b.lastX, b.x), Math.min(b.lastY, b.y), Math.abs(b.x - b.lastX), Math.abs(b.y - b.lastY));

        sweep.grow(ModularUnitEntity.maxMachineReach * 2f);

        Unit[] found = {null};
        Units.nearby(b.team, sweep.x, sweep.y, sweep.width, sweep.height, u -> {
            if(found[0] != null || u == b.owner || u.dead() || u.health >= u.maxHealth) return;
            if(!u.checkTarget(collidesAir, collidesGround)) return;
            if(touches(u, b, pad)) found[0] = u;
        });
        if(found[0] == null) return;

        Unit ally = found[0];
        if(!net.client()) ally.heal(healFor(b));
        healEffect.at(b.x, b.y, 0f, healColor);
        healSound.at(b.x, b.y, 1f, healSoundVolume);
        b.hit = true;
        b.remove();
    }

    private static boolean touches(Unit u, Bullet b, float pad){
        if(u instanceof ModularUnitEntity e && e.collider != null){
            return e.collider.segmentHits(e.x, e.y, e.rotation, b.lastX, b.lastY, b.x, b.y, pad);
        }
        u.hitbox(Tmp.r3);
        Tmp.r3.grow(pad * 2f);
        return Intersector.intersectSegmentRectangle(b.lastX, b.lastY, b.x, b.y, Tmp.r3);
    }

    @Override
    public void hitTile(Bullet b, Building build, float x, float y, float initialHealth, boolean direct){
        if(build.team != b.team){
            super.hitTile(b, build, x, y, initialHealth, direct);
            return;
        }
        if(build.block instanceof ConstructBlock) return;
        if(!net.client()) build.heal(healFor(b));
        healEffect.at(build.x, build.y, 0f, healColor, build.block);
        healSound.at(build, 1f, healSoundVolume);
        hit(b);
        handlePierce(b, initialHealth, x, y);
    }
}
