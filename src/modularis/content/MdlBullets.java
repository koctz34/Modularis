package modularis.content;

import arc.graphics.*;
import mindustry.content.*;
import mindustry.entities.bullet.*;

public class MdlBullets{
    public static BulletType ricochet;

    private static boolean loaded;

    public static void load(){
        if(loaded) return;
        loaded = true;

        ricochet = new BasicBulletType(3.6f, 1f){{
            lifetime = 20f;
            width = 5f;
            height = 8f;
            shrinkY = 0.6f;

            hittable = false;
            reflectable = false;
            absorbable = false;

            frontColor = Color.valueOf("ffe9a8");
            backColor = Color.valueOf("b8823c");
            trailColor = Color.valueOf("b8823c");
            trailLength = 5;
            trailWidth = 0.7f;

            hitSize = 3f;
            knockback = 0f;
            hitEffect = Fx.hitBulletSmall;
            despawnEffect = Fx.hitBulletSmall;
            shootEffect = smokeEffect = Fx.none;
        }};
    }
}
