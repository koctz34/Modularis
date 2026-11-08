package modularis.type.units.nav;

import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.ai.*;
import mindustry.ai.types.*;
import mindustry.core.*;
import mindustry.entities.units.*;
import mindustry.gen.*;

import modularis.type.units.*;

import static mindustry.Vars.*;

/**
 * One machine's route. Sits under the engine's AI instead of replacing it: every controller ends in
 * {@code unit.movePref}, so this only bends that direction, and all the AI's decisions (when to move,
 * when to stop, what to shoot) stay as they are in every game version.
 */
public class ModularNavigator{
    public enum Result{
        drive,
        reverse
    }

    private static final float repathInterval = 90f, stuckInterval = 60f, stuckDistance = 4f, reverseTime = 35f;
    /** Goal drift that makes the route stale, in tiles. */
    private static final int goalDrift = 3;
    /** How far along the route to look for a waypoint with a straight run to it, in tiles. */
    private static final int lookAhead = 12;
    /** Hard turns are taken slowly, or a long hull drives its nose into the wall while swinging round. */
    private static final float slowTurnAngle = 50f, slowTurnSpeed = 0.35f;

    private ModularPathfinder.Request pending;
    private IntSeq path;
    private boolean partial;
    private int index, goalX, goalY;
    private float repathTimer, stuckTimer, stuckX, stuckY, reversing;

    public static @Nullable Position goalOf(Unit unit){
        UnitController controller = unit.controller();
        if(controller instanceof CommandAI ai){
            return ai.currentCommand() == UnitCommand.moveCommand ? ai.targetPos : null;
        }
        if(controller instanceof GroundAI){
            return unit.closestEnemyCore();
        }
        return null;
    }

    private void clear(){
        pending = null;
        path = null;
        partial = false;
        reversing = 0f;
    }

    public Result steer(ModularUnitEntity unit, Vec2 move){
        Position goal = goalOf(unit);
        if(goal == null){
            clear();
            return Result.drive;
        }

        int need = unit.navNeed();
        int tx = unit.tileX(), ty = unit.tileY();
        int gx = World.toTile(goal.getX()), gy = World.toTile(goal.getY());

        if(!ModularPathfinder.ensure()) return Result.drive;

        //nothing in the way: the engine's own steering, formation offsets and all, is fine
        if(ModularPathfinder.lineClear(tx, ty, gx, gy, need)){
            clear();
            return Result.drive;
        }

        if(reversing > 0f){
            reversing -= Time.delta;
            move.trns(unit.rotation + 180f, Math.max(move.len(), 0.1f));
            return Result.reverse;
        }

        repathTimer -= Time.delta;
        boolean drifted = Math.abs(gx - goalX) > goalDrift || Math.abs(gy - goalY) > goalDrift;
        if(pending == null && (path == null || repathTimer <= 0f || drifted)){
            pending = ModularPathfinder.request(tx, ty, gx, gy, need, unit.navTurnNeed(), unit.team, unit.hovering);
            goalX = gx;
            goalY = gy;
            repathTimer = repathInterval;
        }

        if(pending != null && pending.done){
            path = pending.path;
            partial = !pending.reached;
            index = 0;
            pending = null;
        }

        if(path == null) return Result.drive;

        if(path.isEmpty() || (partial && index >= path.size - 1)){
            //as close as this hull fits. Don't go and ram the gap it can't get through
            move.setZero();
            stuckTimer = 0f;
            return Result.drive;
        }

        //catch up: the hull may have been shoved past some of the route
        int end = Math.min(index + lookAhead, path.size);
        int width = world.width();
        float closest = Float.MAX_VALUE;
        for(int i = index; i < end; i++){
            int packed = path.get(i);
            float dst = Mathf.dst2(unit.x, unit.y, (packed % width) * tilesize, (packed / width) * tilesize);
            if(dst < closest){
                closest = dst;
                index = i;
            }
        }

        int waypoint = path.get(index);
        for(int i = end - 1; i > index; i--){
            int packed = path.get(i);
            if(ModularPathfinder.lineClear(tx, ty, packed % width, packed / width, need)){
                waypoint = packed;
                break;
            }
        }

        float angle = Angles.angle(unit.x, unit.y, (waypoint % width) * tilesize, (waypoint / width) * tilesize);
        float speed = move.len();
        if(!unit.type.omniMovement && Angles.angleDist(unit.rotation, angle) > slowTurnAngle) speed *= slowTurnSpeed;
        move.trns(angle, speed);

        stuckTimer += Time.delta;
        if(stuckTimer >= stuckInterval){
            if(Mathf.dst(unit.x, unit.y, stuckX, stuckY) < stuckDistance){
                reversing = reverseTime;
                repathTimer = 0f;
            }
            stuckTimer = 0f;
            stuckX = unit.x;
            stuckY = unit.y;
        }

        return Result.drive;
    }
}
