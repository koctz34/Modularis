package modularis.type.units.nav;

import arc.struct.*;

/**
 * Resumable 8-way A*, so a long route can be spread over many frames. One search runs at a time.
 * If the goal can't be reached it settles for the visited tile closest to it.
 */
public class GridSearch{
    /** Cost of standing on a tile, at least 1. Negative = blocked. */
    public interface Cost{
        int cost(int x, int y);
    }

    private static final float diagonal = 1.4142f;

    private final int width, height;
    private final int[] stamp, parent;
    private final float[] score;
    private int generation;

    private int[] heap = new int[1024];
    private float[] heapKeys = new float[1024];
    private int heapSize;

    private Cost cost;
    private int goalX, goalY, best, expanded, maxNodes;
    private float bestDistance;
    private boolean running, reached;

    /** The finished route as packed tiles, start excluded. */
    public final IntSeq path = new IntSeq();

    public GridSearch(int width, int height){
        this.width = width;
        this.height = height;
        stamp = new int[width * height];
        parent = new int[width * height];
        score = new float[width * height];
    }

    public void begin(int startX, int startY, int goalX, int goalY, Cost cost, int maxNodes){
        this.cost = cost;
        this.goalX = goalX;
        this.goalY = goalY;
        this.maxNodes = maxNodes;
        generation++;
        heapSize = 0;
        expanded = 0;
        reached = false;
        running = true;
        path.clear();

        int start = startX + startY * width;
        stamp[start] = generation;
        score[start] = 0f;
        parent[start] = -1;
        best = start;
        bestDistance = heuristic(startX, startY);
        push(start, bestDistance);
    }

    public boolean running(){
        return running;
    }

    /** Whether the route ends at the goal rather than as close as it could get. */
    public boolean reached(){
        return reached;
    }

    /** Expands up to this many tiles and returns how many it used. */
    public int step(int budget){
        int used = 0;
        int goal = goalX + goalY * width;

        while(used < budget && running){
            if(heapSize == 0 || expanded >= maxNodes){
                finish(best);
                break;
            }

            int current = pop();
            used++;
            expanded++;

            if(current == goal){
                reached = true;
                finish(current);
                break;
            }

            int cx = current % width, cy = current / width;
            float base = score[current];

            float distance = heuristic(cx, cy);
            if(distance < bestDistance){
                bestDistance = distance;
                best = current;
            }

            for(int dx = -1; dx <= 1; dx++){
                for(int dy = -1; dy <= 1; dy++){
                    if(dx == 0 && dy == 0) continue;

                    int nx = cx + dx, ny = cy + dy;
                    if(nx < 0 || ny < 0 || nx >= width || ny >= height) continue;

                    int tileCost = cost.cost(nx, ny);
                    if(tileCost < 0) continue;
                    //no cutting corners
                    if(dx != 0 && dy != 0 && (cost.cost(cx + dx, cy) < 0 || cost.cost(cx, cy + dy) < 0)) continue;

                    float next = base + tileCost * (dx != 0 && dy != 0 ? diagonal : 1f);
                    int index = nx + ny * width;
                    if(stamp[index] != generation || next < score[index]){
                        stamp[index] = generation;
                        score[index] = next;
                        parent[index] = current;
                        push(index, next + heuristic(nx, ny));
                    }
                }
            }
        }
        return used;
    }

    private void finish(int end){
        running = false;
        cost = null;

        for(int at = end; at != -1 && parent[at] != -1; at = parent[at]){
            path.add(at);
        }
        path.reverse();
    }

    private float heuristic(int x, int y){
        int dx = Math.abs(x - goalX), dy = Math.abs(y - goalY);
        return Math.max(dx, dy) + (diagonal - 1f) * Math.min(dx, dy);
    }

    private void push(int value, float key){
        if(heapSize == heap.length){
            heap = java.util.Arrays.copyOf(heap, heapSize * 2);
            heapKeys = java.util.Arrays.copyOf(heapKeys, heapSize * 2);
        }

        int i = heapSize++;
        while(i > 0){
            int up = (i - 1) / 2;
            if(heapKeys[up] <= key) break;
            heap[i] = heap[up];
            heapKeys[i] = heapKeys[up];
            i = up;
        }
        heap[i] = value;
        heapKeys[i] = key;
    }

    private int pop(){
        int top = heap[0];
        heapSize--;

        int value = heap[heapSize];
        float key = heapKeys[heapSize];
        int i = 0;
        while(true){
            int child = i * 2 + 1;
            if(child >= heapSize) break;
            if(child + 1 < heapSize && heapKeys[child + 1] < heapKeys[child]) child++;
            if(heapKeys[child] >= key) break;
            heap[i] = heap[child];
            heapKeys[i] = heapKeys[child];
            i = child;
        }
        heap[i] = value;
        heapKeys[i] = key;
        return top;
    }
}
