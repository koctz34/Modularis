package modularis.type.units;

import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.Mathf;
import arc.util.*;
import mindustry.content.Blocks;
import mindustry.entities.bullet.ArtilleryBulletType;
import mindustry.entities.effect.WrapEffect;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.Floor;

import modularis.content.*;
import modularis.type.NukeFx;

import static mindustry.Vars.net;
import static mindustry.Vars.world;

public class ICBM extends ArtilleryBulletType{
    public float craterFrac = 0.35f;
    public float scorchFrac = 0.7f;

    public ICBM(float spe, float dam){
        super(spe, dam);
        width = 24f;
        height = 32f;
        lifetime = 200f;
        hitSize = 1f;
        splashDamageRadius = 300f;
        splashDamage = 15000f;

        collidesTiles = false;
        collides = false;
        hittable = false;

        trailEffect = MdlFX.icbmFlame;

        hitEffect = despawnEffect = new WrapEffect(NukeFx.nuke, Color.white, 220f);
        hitShake = 22f;
    }

    @Override
    public void update(Bullet b){
        super.update(b);

        if(b.timer(0, (3 + b.fslope() * 2f) * trailMult)){
            trailEffect.at(b.x, b.y, b.rotation());
        }
    }

    @Override
    public void draw(Bullet b){
        super.draw(b);
        float shrink = shrinkInterp.apply(b.fout());
        float height = this.height * ((1f - shrinkY) + shrinkY * shrink);
        float width = this.width * ((1f - shrinkX) + shrinkX * shrink);
        float offset = -90 + (spin != 0 ? Mathf.randomSeed(b.id, 360f) + b.time * spin : 0f) + rotationOffset;

        Color mix = Tmp.c1.set(mixColorFrom).lerp(mixColorTo, b.fin());

        Draw.mixcol(mix, mix.a);

        if(backRegion.found()){
            Draw.color(backColor);
            Draw.rect(backRegion, b.x, b.y, width, height, b.rotation() + offset);
        }

        Draw.color(frontColor);
        Draw.rect(frontRegion, b.x, b.y, width, height, b.rotation() + offset);
        
        Draw.z(Layer.groundUnit + 2f);
        Draw.color(0f, 0f, 0f, 0.22f);

        if(backRegion.found()){
            Draw.rect(backRegion, b.x + shrink * 15, b.y - shrink * 15, width, height, b.rotation() + offset);
        }
        Draw.rect(frontRegion, b.x + shrink * 15, b.y - shrink * 15, width, height, b.rotation() + offset);
        
        Draw.reset();
    }
}