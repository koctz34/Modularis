package modularis.type.units;

import arc.audio.*;
import arc.graphics.*;
import mindustry.content.*;
import mindustry.entities.bullet.*;
import mindustry.gen.*;

public class CannonCaliber{
    public static final int standard = 0, he = 1, ap = 2, heal = 3;
    private static final CannonCaliber[] all = new CannonCaliber[4];

    public final int size;
    public final String title;
    public float damage, speed, reload, inaccuracy, range, recoil, shake, splash, splashRadius, width, height;
    public int optimalLength;
    public Sound sound = Sounds.shoot;
    public final BulletType[] bullets = new BulletType[4];

    CannonCaliber(int size, String title){
        this.size = size;
        this.title = title;
    }

    public static CannonCaliber get(int size){
        return all[Math.max(1, Math.min(size, 3))];
    }

    public static float maxRange(){
        float range = 0f;
        for(int i = 1; i < all.length; i++) if(all[i] != null) range = Math.max(range, all[i].range);
        return range * CannonSpec.maxRangeScale;
    }

    public static void load(){
        if(all[1] != null) return;

        all[1] = new CannonCaliber(1, "Light"){{
            damage = 24f; speed = 7f; reload = 45f; inaccuracy = 3f; range = 280f;
            optimalLength = 3; recoil = 1f; shake = 0f; width = 7f; height = 10f;
            sound = Sounds.shoot;
        }};
        all[2] = new CannonCaliber(2, "Medium"){{
            damage = 85f; speed = 6f; reload = 110f; inaccuracy = 2f; range = 340f;
            optimalLength = 4; recoil = 2f; shake = 2f; splash = 30f; splashRadius = 20f; width = 10f; height = 14f;
            sound = Sounds.shootArtillery;
        }};
        all[3] = new CannonCaliber(3, "Heavy"){{
            damage = 280f; speed = 5f; reload = 240f; inaccuracy = 1.2f; range = 420f;
            optimalLength = 6; recoil = 3.5f; shake = 6f; splash = 110f; splashRadius = 36f; width = 14f; height = 20f;
            sound = Sounds.explosionTitan;
        }};

        for(int i = 1; i < all.length; i++){
            CannonCaliber c = all[i];
            c.bullets[standard] = c.bullet(new BasicBulletType(c.speed, c.damage), 1f, 1f, false, "ffd37f");
            c.bullets[he] = c.bullet(new BasicBulletType(c.speed, c.damage * 0.6f), 2.5f, 1.6f, false, "ff8a5c");
            c.bullets[ap] = c.bullet(new BasicBulletType(c.speed, c.damage * 1.4f), 0f, 0f, true, "9fd4ff");
            c.bullets[heal] = c.bullet(new HealShellBulletType(c.speed, c.damage * 0.5f), 0f, 0f, false, "84f491");
        }
    }

    private BulletType bullet(BasicBulletType b, float splashScale, float radiusScale, boolean piercing, String tint){
        float blast = splashScale <= 0f ? 0f : Math.max(splash, size * 6f) * splashScale;
        b.lifetime = range / speed;
        b.width = width;
        b.height = height;
        b.splashDamage = blast;
        b.splashDamageRadius = blast > 0f ? Math.max(splashRadius, 12f) * radiusScale : 0f;
        b.pierce = piercing;
        b.pierceCap = piercing ? 2 : -1;
        b.pierceBuilding = piercing;
        b.trailLength = size * 3;
        b.trailWidth = size * 0.8f;
        b.backColor = b.trailColor = b.hitColor = Color.valueOf(tint);
        b.hitEffect = b.despawnEffect = size >= 3 ? Fx.blastExplosion : size == 2 ? Fx.flakExplosion : Fx.hitBulletSmall;
        b.shootEffect = size >= 2 ? Fx.shootBig : Fx.shootSmall;
        b.smokeEffect = size >= 2 ? Fx.shootBigSmoke : Fx.shootSmallSmoke;
        return b;
    }
}
