package modularis.type.units;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.entities.units.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;

import modularis.content.*;

import static mindustry.Vars.*;

public class HarpoonWeapon extends Weapon{
    static final float flightLayer = Layer.bullet - 0.001f;
    private static final float ropeLayer = Layer.bullet - 0.01f;
    private static final float spriteScale = 0.25f;
    private static final float outlineOffset = 0.75f;
    private static final Vec2 muzzle = new Vec2(), anchor = new Vec2();

    public float reelSpeed = 0.55f;
    public float holdTime = 60f * 8f;
    public float returnSpeed = 7f;
    public float breakStretch = 24f;
    public float breakForce = 420f;
    public float ropeDamping = 0.15f;
    public float loadedY = 1.75f;

    public HarpoonWeapon(String name){
        super(name);
        mountType = HarpoonMount::new;
        autoTarget = true;
    }

    void hook(Unit owner, HarpoonMount mount, Unit target, float hitX, float hitY){
        if(target.dead() || target.team == owner.team) return;

        mount.state = HarpoonMount.State.hooked;
        mount.target = target;
        mount.shot = null;
        mount.hookTime = 0f;
        mount.strain = 0f;

        Tmp.v1.set(hitX - target.x, hitY - target.y).rotate(-target.rotation);
        mount.anchorX = Tmp.v1.x;
        mount.anchorY = Tmp.v1.y;

        muzzle(owner, mount, muzzle);
        mount.rope = muzzle.dst(hitX, hitY);
    }

    private void muzzle(Unit unit, WeaponMount mount, Vec2 out){
        float rotation = unit.rotation - 90f, weaponRotation = rotation + mount.rotation;
        out.set(unit.x + Angles.trnsx(rotation, x, y) + Angles.trnsx(weaponRotation, shootX, shootY),
            unit.y + Angles.trnsy(rotation, x, y) + Angles.trnsy(weaponRotation, shootX, shootY));
    }

    private void anchor(HarpoonMount mount, Vec2 out){
        out.set(mount.anchorX, mount.anchorY).rotate(mount.target.rotation).add(mount.target.x, mount.target.y);
    }

    private void wind(Unit unit, HarpoonMount mount, float fromX, float fromY){
        mount.state = HarpoonMount.State.returning;
        mount.headX = fromX;
        mount.headY = fromY;
        mount.target = null;
        mount.shot = null;
        mount.strain = 0f;
    }

    @Override
    protected void handleBullet(Unit unit, WeaponMount mount, Bullet bullet){
        super.handleBullet(unit, mount, bullet);
        if(mount instanceof HarpoonMount h){
            h.state = HarpoonMount.State.flying;
            h.shot = bullet;
            bullet.data = h;
        }
    }

    @Override
    public void update(Unit unit, WeaponMount mount){
        HarpoonMount h = (HarpoonMount)mount;

        //it can't fire what isn't in the barrel, and reloading starts when it gets back
        if(h.state != HarpoonMount.State.loaded) mount.reload = reload;

        super.update(unit, mount);

        switch(h.state){
            case flying -> {
                if(h.shot != null && h.shot.isAdded() && h.shot.type == bullet){
                    h.headX = h.shot.x;
                    h.headY = h.shot.y;
                }else{
                    wind(unit, h, h.headX, h.headY);
                }
            }
            case hooked -> updateHook(unit, h);
            case returning -> {
                muzzle(unit, mount, muzzle);
                float dst = muzzle.dst(h.headX, h.headY);
                float step = returnSpeed * Time.delta;
                if(dst <= step){
                    h.state = HarpoonMount.State.loaded;
                    mount.reload = reload;
                }else{
                    h.headX += (muzzle.x - h.headX) / dst * step;
                    h.headY += (muzzle.y - h.headY) / dst * step;
                }
            }
            default -> {}
        }
    }

    private void updateHook(Unit unit, HarpoonMount h){
        Unit target = h.target;
        if(target == null || !target.isAdded() || target.dead() || target.team == unit.team){
            wind(unit, h, h.headX, h.headY);
            return;
        }

        muzzle(unit, h, muzzle);
        anchor(h, anchor);
        h.headX = anchor.x;
        h.headY = anchor.y;

        h.hookTime += Time.delta;
        if(!net.client() && h.hookTime >= holdTime){
            wind(unit, h, anchor.x, anchor.y);
            return;
        }

        float closest = (unit.hitSize + target.hitSize) / 2f;
        h.rope = Math.max(h.rope - reelSpeed * Time.delta, closest);

        float distance = muzzle.dst(anchor);
        float stretch = Math.max(distance - h.rope, 0f);

        if(stretch > 0f && distance > 0.001f){
            float nx = (anchor.x - muzzle.x) / distance, ny = (anchor.y - muzzle.y) / distance;
            float invA = 1f / Math.max(unit.mass(), 1f), invB = 1f / Math.max(target.mass(), 1f);
            float relative = (target.vel.x - unit.vel.x) * nx + (target.vel.y - unit.vel.y) * ny;
            float spring = breakForce / breakStretch * stretch * Time.delta;
            float damper = relative * Math.min(ropeDamping * Time.delta, 1f) / (invA + invB);
            float impulse = Math.max(spring + damper, 0f);

            if(!net.client() || unit.isLocal()){
                unit.vel.add(nx * impulse * invA, ny * impulse * invA);
                if(unit instanceof ModularUnitEntity e) TowInteraction.twist(e, muzzle, nx * impulse, ny * impulse);
            }
            if(!net.client() || target.isLocal()){
                target.vel.add(-nx * impulse * invB, -ny * impulse * invB);
                if(target instanceof ModularUnitEntity e) TowInteraction.twist(e, anchor, -nx * impulse, -ny * impulse);
            }
        }

        if(net.client()) return;

        h.strain = stretch / breakStretch;
        if(h.strain >= 1f){
            float mx = (muzzle.x + anchor.x) / 2f, my = (muzzle.y + anchor.y) / 2f;
            MdlFX.towSnap.at(mx, my, Angles.angle(muzzle.x, muzzle.y, anchor.x, anchor.y));
            Sounds.rockBreak.at(mx, my, Mathf.random(1.3f, 1.7f));
            wind(unit, h, anchor.x, anchor.y);
        }
    }

    @Override
    public void draw(Unit unit, WeaponMount mount){
        super.draw(unit, mount);

        HarpoonMount h = (HarpoonMount)mount;
        float z = Draw.z();

        if(h.state == HarpoonMount.State.loaded){
            float rotation = unit.rotation - 90f, weaponRotation = rotation + mount.rotation;
            float along = loadedY - Mathf.pow(mount.recoil, recoilPow) * recoil;
            drawHead(unit.x + Angles.trnsx(rotation, x, y) + Angles.trnsx(weaponRotation, 0f, along),
                unit.y + Angles.trnsy(rotation, x, y) + Angles.trnsy(weaponRotation, 0f, along),
                weaponRotation + 90f, z + 0.002f);
            return;
        }

        muzzle(unit, mount, muzzle);
        float hx = h.headX, hy = h.headY;
        if(h.state == HarpoonMount.State.flying && h.shot != null){
            hx = h.shot.x;
            hy = h.shot.y;
        }else if(h.state == HarpoonMount.State.hooked && h.target != null){
            anchor(h, anchor);
            hx = anchor.x;
            hy = anchor.y;
        }

        Draw.z(ropeLayer);
        float length = h.state == HarpoonMount.State.hooked ? h.rope : muzzle.dst(hx, hy);
        TowInteraction.drawConnected(muzzle.x, muzzle.y, hx, hy, length, h.strain);
        Draw.reset();

        if(h.state != HarpoonMount.State.flying){
            drawHead(hx, hy, Angles.angle(muzzle.x, muzzle.y, hx, hy), flightLayer);
        }
        Draw.z(z);
    }

    public static void drawHead(float x, float y, float angle, float layer){
        TextureRegion region = Core.atlas.find("modularis-harpoon");
        float w = region.width * spriteScale, h = region.height * spriteScale;
        float z = Draw.z();

        //each gets its own layer: draws that share one can land in either order
        Draw.z(layer - 0.0005f);
        Draw.color(MdlUnits.modularUnit.outlineColor);
        for(int i = 0; i < 4; i++){
            Draw.rect(region, x + Angles.trnsx(i * 90f, outlineOffset), y + Angles.trnsy(i * 90f, outlineOffset), w, h, angle - 90f);
        }

        Draw.z(layer);
        Draw.color();
        Draw.rect(region, x, y, w, h, angle - 90f);
        Draw.z(z);
    }

    public static void writeSync(ModularUnitEntity unit, Writes write){
        int count = 0;
        for(WeaponMount mount : unit.mounts) if(mount instanceof HarpoonMount) count++;

        write.b(count);
        for(WeaponMount mount : unit.mounts){
            if(mount instanceof HarpoonMount h){
                boolean hooked = h.state == HarpoonMount.State.hooked && h.target != null;
                write.i(hooked ? h.target.id : -1);
                write.f(h.rope);
                write.f(h.anchorX);
                write.f(h.anchorY);
                write.f(h.strain);
            }
        }
    }

    public static void readSync(ModularUnitEntity unit, Reads read){
        int count = read.b(), seen = 0;
        WeaponMount[] mounts = unit.mounts;

        for(int i = 0; i < count; i++){
            int id = read.i();
            float rope = read.f(), anchorX = read.f(), anchorY = read.f(), strain = read.f();

            while(seen < mounts.length && !(mounts[seen] instanceof HarpoonMount)) seen++;
            if(seen >= mounts.length) continue;

            HarpoonMount h = (HarpoonMount)mounts[seen++];
            HarpoonWeapon weapon = (HarpoonWeapon)h.weapon;
            Unit target = id < 0 ? null : Groups.unit.getByID(id);

            if(target != null){
                h.state = HarpoonMount.State.hooked;
                h.target = target;
                h.shot = null;
                h.rope = rope;
                h.anchorX = anchorX;
                h.anchorY = anchorY;
                h.strain = strain;
            }else if(h.state == HarpoonMount.State.hooked){
                weapon.wind(unit, h, h.headX, h.headY);
            }
        }
    }
}
