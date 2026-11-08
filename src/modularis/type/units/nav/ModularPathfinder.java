package modularis.type.units.nav;

import arc.math.geom.*;
import arc.struct.*;
import mindustry.game.*;
import mindustry.world.*;

import static mindustry.Vars.*;

public class ModularPathfinder{
    /** Tiles a search may expand per update, across all machines. */
    private static final int frameBudget = 6000;
    /** Tiles one search may expand before settling for the closest it got. */
    private static final int searchLimit = 120000;
    /** Extra cost of a route through something that has to be shot down first. */
    private static final int breachCost = 70;
    /** A hull already pressed against a wall may leave it through tiles this close to its start. */
    private static final int startSlack = 3;

    /** One route being asked for. */
    public static class Request{
        final int startX, startY, goalX, goalY, need, turnNeed;
        final Team team;
        final boolean hover;

        public boolean done, reached;
        public IntSeq path;

        Request(int startX, int startY, int goalX, int goalY, int need, int turnNeed, Team team, boolean hover){
            this.startX = startX;
            this.startY = startY;
            this.goalX = goalX;
            this.goalY = goalY;
            this.need = need;
            this.turnNeed = turnNeed;
            this.team = team;
            this.hover = hover;
        }
    }

    private static ClearanceMap all, walls;
    private static GridSearch search;
    private static final Queue<Request> queue = new Queue<>();
    private static final IntSet dirty = new IntSet();
    private static Request active;

    public static void reset(){
        all = walls = null;
        search = null;
        active = null;
        queue.clear();
        dirty.clear();
    }

    public static void tileChanged(Tile tile){
        if(all != null) dirty.add(tile.pos());
    }

    /** Builds the maps on first use. False if there is no world to build them for. */
    public static boolean ensure(){
        if(all != null && all.width == world.width() && all.height == world.height()) return true;
        if(world.width() <= 0 || world.height() <= 0) return false;

        all = new ClearanceMap(world.width(), world.height(), (x, y) -> world.tile(x, y).solid());
        walls = new ClearanceMap(world.width(), world.height(), (x, y) -> {
            Tile tile = world.tile(x, y);
            return tile.solid() && (tile.build == null || tile.team() == Team.derelict);
        });
        search = new GridSearch(world.width(), world.height());
        return true;
    }

    public static void update(){
        if(net.client() || !state.isGame() || all == null) return;
        if(all.width != world.width() || all.height != world.height()){
            reset();
            return;
        }

        if(!dirty.isEmpty()){
            IntSet.IntSetIterator it = dirty.iterator();
            while(it.hasNext){
                int pos = it.next();
                all.update(Point2.x(pos), Point2.y(pos));
                walls.update(Point2.x(pos), Point2.y(pos));
            }
            dirty.clear();
        }

        int budget = frameBudget;
        while(budget > 0){
            if(active == null){
                if(queue.isEmpty()) return;
                active = queue.removeFirst();
                search.begin(active.startX, active.startY, active.goalX, active.goalY, costFor(active), searchLimit);
            }

            budget -= Math.max(search.step(budget), 1);

            if(!search.running()){
                active.path = new IntSeq(search.path);
                active.reached = search.reached();
                active.done = true;
                active = null;
            }
        }
    }

    public static Request request(int startX, int startY, int goalX, int goalY, int need, int turnNeed, Team team, boolean hover){
        if(!ensure()) return null;

        int w = world.width(), h = world.height();
        Request request = new Request(clamp(startX, w), clamp(startY, h), clamp(goalX, w), clamp(goalY, h),
            Math.min(need, ClearanceMap.cap), Math.min(turnNeed, ClearanceMap.cap), team, hover);
        queue.addLast(request);
        return request;
    }

    private static int clamp(int value, int size){
        return Math.max(0, Math.min(value, size - 1));
    }

    private static GridSearch.Cost costFor(Request r){
        return (x, y) -> {
            if(walls.get(x, y) < r.need){
                boolean nearStart = Math.abs(x - r.startX) <= startSlack && Math.abs(y - r.startY) <= startSlack;
                if(!nearStart || all.get(x, y) == 0) return -1;
                return 4;
            }

            Tile tile = world.tile(x, y);
            if(!r.hover && tile.floor().isDeep()) return -1;

            int clearance = all.get(x, y);
            int cost = clearance < r.turnNeed ? 3 : 1;
            if(clearance >= r.need) return cost;

            //only buildings are in the way. Ours stay; a hull can't shoot its own team's walls
            int reach = r.need - 1;
            for(int dx = -reach; dx <= reach; dx++){
                for(int dy = -reach; dy <= reach; dy++){
                    Tile other = world.tile(x + dx, y + dy);
                    if(other != null && other.solid() && other.team() == r.team) return -1;
                }
            }
            return cost + breachCost;
        };
    }

    /** True if a hull needing this much room can drive straight along the line between two tiles. */
    public static boolean lineClear(int x1, int y1, int x2, int y2, int need){
        if(all == null) return false;

        int dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1, sy = y1 < y2 ? 1 : -1;
        int error = dx - dy;

        while(true){
            if(all.get(x1, y1) < need) return false;
            if(x1 == x2 && y1 == y2) return true;

            int twice = error * 2;
            if(twice > -dy){
                error -= dy;
                x1 += sx;
            }
            if(twice < dx){
                error += dx;
                y1 += sy;
            }
        }
    }
}
