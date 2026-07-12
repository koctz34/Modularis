package modularis.type.units;

import arc.struct.*;
import arc.util.io.*;
import mindustry.entities.units.*;
import mindustry.gen.*;

import modularis.type.units.modules.*;

/**
 * Custom tank entity that carries its {@link ModularDesign} as real, serialized
 * state. Because the design lives on the entity (not in a side map), it survives
 * world save/load and is sent to clients in multiplayer.
 *
 * Registered with {@link mindustry.gen.EntityMapping} in the mod's init so the
 * network layer knows how to reconstruct it; {@link #classId()} returns that id.
 */
public class ModularUnitEntity extends TankUnit{
    /** Network/save class id, assigned at registration time. */
    public static int classID = -1;

    public ModularDesign design;

    /** Ability (mender) mounts, simulated by us; weapons live in the native {@link #mounts} array. */
    public final Seq<MenderMount> menders = new Seq<>();

    /** Sets the blueprint and derives dependent stats (max health, hitbox, weapon mounts). */
    public void setDesign(ModularDesign d){
        design = d == null ? null : d.copy();
        rebuildMounts();

        if(design != null && !design.isEmpty()){
            maxHealth(Math.max(1f, design.totalHealth()));
            float w = Math.max(1, design.widthCells());
            float h = Math.max(1, design.heightCells());
            hitSize(Math.max(w, h) * ModularUnitType.cellWorld() * 0.8f);
            clampHealth();
        }
    }

    private void rebuildMounts(){
        menders.clear();
        mounts = new WeaponMount[0];
        if(design == null) return;

        float cell = ModularUnitType.cellWorld();
        float cx = design.centerX(), cy = design.centerY();

        Seq<WeaponMount> weaponMounts = new Seq<>();
        for(PlacedModule m : design.modules){
            if(m.type instanceof ModulTurret t){
                float lx = (m.x + t.w / 2f - cx) * cell;
                float ly = (m.y + t.h / 2f - cy) * cell;
                WeaponMount wm = t.createMount(lx, ly);
                if(wm != null){
                    wm.rotation = rotation;
                    weaponMounts.add(wm);
                }
            }else if(m.type instanceof ModulMender mm){
                menders.add(new MenderMount(m, mm));
            }
        }
        mounts = weaponMounts.toArray(WeaponMount.class);
    }

    @Override
    public int classId(){
        return classID;
    }

    // ---- persistence (save files) ----

    @Override
    public void write(Writes write){
        super.write(write);
        write.str(design == null ? "" : design.serialize());
    }

    @Override
    public void read(Reads read){
        super.read(read);
        setDesign(ModularDesign.read(read.str()));
    }

    // ---- network sync (full state) ----

    @Override
    public void writeSync(Writes write){
        super.writeSync(write);
        write.str(design == null ? "" : design.serialize());
    }

    @Override
    public void readSync(Reads read){
        super.readSync(read);
        setDesign(ModularDesign.read(read.str()));
    }
}
