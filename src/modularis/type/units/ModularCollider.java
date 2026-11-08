package modularis.type.units;

import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import mindustry.entities.EntityCollisions.*;

import static mindustry.Vars.*;

public class ModularCollider{
    public static final int maxParts = 8;
    public static final float terrainScale = 2f / 3f;
    private static final float tileHalf = 4f;

    public final float[] parts;
    public final float radius;
    public int hitX, hitY;

    private float cachedRotation = Float.NaN, minX, minY, maxX, maxY;

    public ModularCollider(float[] parts){
        this.parts = parts;

        float far = 0f;
        for(int i = 0; i < parts.length; i += 4){
            far = Math.max(far, Mathf.len(Math.abs(parts[i]) + parts[i + 2], Math.abs(parts[i + 1]) + parts[i + 3]));
        }
        radius = far;
    }

    public int count(){
        return parts.length / 4;
    }

    public static ModularCollider of(ModularDesign design, float originX, float originY, float cell){
        int bx = design.minX(), by = design.minY();
        int w = design.widthCells(), h = design.heightCells();

        boolean[][] grid = new boolean[w][h];
        for(PlacedModule m : design.modules){
            for(int x = m.x; x < m.x + m.type.w; x++){
                for(int y = m.y; y < m.y + m.type.h; y++){
                    grid[x - bx][y - by] = true;
                }
            }
        }

        Seq<int[]> rects = decompose(grid);
        float[] parts = new float[rects.size * 4];
        for(int i = 0; i < rects.size; i++){
            int[] r = rects.get(i);
            parts[i * 4] = (bx + r[0] + r[2] / 2f - originX) * cell;
            parts[i * 4 + 1] = (by + r[1] + r[3] / 2f - originY) * cell;
            parts[i * 4 + 2] = r[2] * cell / 2f;
            parts[i * 4 + 3] = r[3] * cell / 2f;
        }
        return new ModularCollider(parts);
    }

    public static Seq<int[]> decompose(boolean[][] grid){
        Seq<int[]> out = new Seq<>();
        if(grid.length == 0 || grid[0].length == 0) return out;

        int w = grid.length, h = grid[0].length;
        boolean[][] left = new boolean[w][h];
        for(int x = 0; x < w; x++) System.arraycopy(grid[x], 0, left[x], 0, h);

        int[] heights = new int[w];
        while(true){
            int[] best = largest(left, heights);
            if(best == null) return out;

            for(int x = best[0]; x < best[0] + best[2]; x++){
                for(int y = best[1]; y < best[1] + best[3]; y++){
                    left[x][y] = false;
                }
            }

            if(out.size < maxParts){
                out.add(best);
            }else{
                absorb(out, best);
            }
        }
    }

    private static int[] largest(boolean[][] cells, int[] heights){
        int w = cells.length, h = cells[0].length;
        int[] best = null;
        int bestArea = 0;

        java.util.Arrays.fill(heights, 0);
        for(int y = 0; y < h; y++){
            for(int x = 0; x < w; x++){
                heights[x] = cells[x][y] ? heights[x] + 1 : 0;
            }

            for(int x = 0; x < w; x++){
                int minHeight = Integer.MAX_VALUE;
                for(int end = x; end < w && heights[end] > 0; end++){
                    minHeight = Math.min(minHeight, heights[end]);
                    int area = minHeight * (end - x + 1);
                    if(area > bestArea){
                        bestArea = area;
                        best = new int[]{x, y - minHeight + 1, end - x + 1, minHeight};
                    }
                }
            }
        }
        return best;
    }

    private static void absorb(Seq<int[]> rects, int[] extra){
        int[] target = null;
        int cheapest = Integer.MAX_VALUE;
        for(int[] r : rects){
            int x1 = Math.min(r[0], extra[0]), y1 = Math.min(r[1], extra[1]);
            int x2 = Math.max(r[0] + r[2], extra[0] + extra[2]), y2 = Math.max(r[1] + r[3], extra[1] + extra[3]);
            int growth = (x2 - x1) * (y2 - y1) - r[2] * r[3];
            if(growth < cheapest){
                cheapest = growth;
                target = r;
            }
        }

        int x1 = Math.min(target[0], extra[0]), y1 = Math.min(target[1], extra[1]);
        int x2 = Math.max(target[0] + target[2], extra[0] + extra[2]);
        int y2 = Math.max(target[1] + target[3], extra[1] + extra[3]);
        target[0] = x1;
        target[1] = y1;
        target[2] = x2 - x1;
        target[3] = y2 - y1;
    }

    public void bounds(float x, float y, float rotation, float scale, Rect out){
        if(rotation != cachedRotation){
            cachedRotation = rotation;
            float cos = Mathf.cosDeg(rotation - 90f), sin = Mathf.sinDeg(rotation - 90f);
            float absCos = Math.abs(cos), absSin = Math.abs(sin);

            minX = minY = Float.MAX_VALUE;
            maxX = maxY = -Float.MAX_VALUE;
            for(int i = 0; i < parts.length; i += 4){
                float cx = parts[i] * cos - parts[i + 1] * sin, cy = parts[i] * sin + parts[i + 1] * cos;
                float ex = parts[i + 2] * absCos + parts[i + 3] * absSin;
                float ey = parts[i + 2] * absSin + parts[i + 3] * absCos;
                minX = Math.min(minX, cx - ex);
                maxX = Math.max(maxX, cx + ex);
                minY = Math.min(minY, cy - ey);
                maxY = Math.max(maxY, cy + ey);
            }
        }
        out.set(x + minX * scale, y + minY * scale, (maxX - minX) * scale, (maxY - minY) * scale);
    }

    public boolean overlapsTiles(float x, float y, float rotation, SolidPred solid){
        float cos = Mathf.cosDeg(rotation - 90f), sin = Mathf.sinDeg(rotation - 90f);
        float absCos = Math.abs(cos), absSin = Math.abs(sin);

        for(int i = 0; i < parts.length; i += 4){
            float lx = parts[i] * terrainScale, ly = parts[i + 1] * terrainScale;
            float hw = parts[i + 2] * terrainScale, hh = parts[i + 3] * terrainScale;
            float px = x + lx * cos - ly * sin, py = y + lx * sin + ly * cos;

            float ex = hw * absCos + hh * absSin, ey = hw * absSin + hh * absCos;
            int x1 = Mathf.round((px - ex) / tilesize), x2 = Mathf.round((px + ex) / tilesize);
            int y1 = Mathf.round((py - ey) / tilesize), y2 = Mathf.round((py + ey) / tilesize);

            //a tile's own axes were covered by that loop range; its projection on the part's are left
            float tileReach = tileHalf * (absCos + absSin);
            for(int tx = x1; tx <= x2; tx++){
                for(int ty = y1; ty <= y2; ty++){
                    float dx = tx * tilesize - px, dy = ty * tilesize - py;
                    if(Math.abs(dx * cos + dy * sin) >= hw + tileReach) continue;
                    if(Math.abs(dy * cos - dx * sin) >= hh + tileReach) continue;
                    if(solid.solid(tx, ty)){
                        hitX = tx;
                        hitY = ty;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean segmentHits(float x, float y, float rotation, float x1, float y1, float x2, float y2, float pad){
        float cos = Mathf.cosDeg(rotation - 90f), sin = Mathf.sinDeg(rotation - 90f);
        float ox1 = x1 - x, oy1 = y1 - y, ox2 = x2 - x, oy2 = y2 - y;
        float ax = ox1 * cos + oy1 * sin, ay = oy1 * cos - ox1 * sin;
        float bx = ox2 * cos + oy2 * sin, by = oy2 * cos - ox2 * sin;

        for(int i = 0; i < parts.length; i += 4){
            if(slabHit(ax, ay, bx, by, parts[i], parts[i + 1], parts[i + 2] + pad, parts[i + 3] + pad)) return true;
        }
        return false;
    }

    private static boolean slabHit(float ax, float ay, float bx, float by, float cx, float cy, float hw, float hh){
        float enter = 0f, exit = 1f;

        float dx = bx - ax;
        if(Math.abs(dx) < 0.00001f){
            if(Math.abs(ax - cx) > hw) return false;
        }else{
            float t1 = (cx - hw - ax) / dx, t2 = (cx + hw - ax) / dx;
            enter = Math.max(enter, Math.min(t1, t2));
            exit = Math.min(exit, Math.max(t1, t2));
            if(enter > exit) return false;
        }

        float dy = by - ay;
        if(Math.abs(dy) < 0.00001f){
            return Math.abs(ay - cy) <= hh;
        }
        float t1 = (cy - hh - ay) / dy, t2 = (cy + hh - ay) / dy;
        enter = Math.max(enter, Math.min(t1, t2));
        exit = Math.min(exit, Math.max(t1, t2));
        return enter <= exit;
    }

    public boolean pushFromCircle(float x, float y, float rotation, float ox, float oy, float r, Vec2 push){
        float cos = Mathf.cosDeg(rotation - 90f), sin = Mathf.sinDeg(rotation - 90f);
        float dx = ox - x, dy = oy - y;
        float lx = dx * cos + dy * sin, ly = dy * cos - dx * sin;

        float deepest = 0f, pushX = 0f, pushY = 0f;
        boolean hit = false;
        for(int i = 0; i < parts.length; i += 4){
            float rx = lx - parts[i], ry = ly - parts[i + 1];
            float hw = parts[i + 2], hh = parts[i + 3];
            float nearX = Mathf.clamp(rx, -hw, hw), nearY = Mathf.clamp(ry, -hh, hh);
            float gapX = rx - nearX, gapY = ry - nearY;
            float dist2 = gapX * gapX + gapY * gapY;
            if(dist2 >= r * r) continue;

            float depth, nx, ny;
            if(dist2 > 0.0001f){
                float dist = Mathf.sqrt(dist2);
                depth = r - dist;
                nx = gapX / dist;
                ny = gapY / dist;
            }else{
                float faceX = hw - Math.abs(rx), faceY = hh - Math.abs(ry);
                if(faceX < faceY){
                    depth = faceX + r;
                    nx = rx < 0f ? -1f : 1f;
                    ny = 0f;
                }else{
                    depth = faceY + r;
                    nx = 0f;
                    ny = ry < 0f ? -1f : 1f;
                }
            }

            if(depth > deepest){
                deepest = depth;
                pushX = -nx * depth;
                pushY = -ny * depth;
                hit = true;
            }
        }
        if(!hit) return false;

        push.set(pushX * cos - pushY * sin, pushX * sin + pushY * cos);
        return true;
    }

    public boolean pushFrom(float x, float y, float rotation, ModularCollider other, float ox, float oy, float otherRotation, Vec2 push){
        float cosA = Mathf.cosDeg(rotation - 90f), sinA = Mathf.sinDeg(rotation - 90f);
        float cosB = Mathf.cosDeg(otherRotation - 90f), sinB = Mathf.sinDeg(otherRotation - 90f);
        float[] axes = {cosA, sinA, -sinA, cosA, cosB, sinB, -sinB, cosB};

        float deepest = 0f, pushX = 0f, pushY = 0f;
        boolean hit = false;

        for(int i = 0; i < parts.length; i += 4){
            float ax = x + parts[i] * cosA - parts[i + 1] * sinA, ay = y + parts[i] * sinA + parts[i + 1] * cosA;
            for(int j = 0; j < other.parts.length; j += 4){
                float bx = ox + other.parts[j] * cosB - other.parts[j + 1] * sinB;
                float by = oy + other.parts[j] * sinB + other.parts[j + 1] * cosB;
                float dx = ax - bx, dy = ay - by;

                float smallest = Float.MAX_VALUE, nx = 0f, ny = 0f;
                boolean apart = false;
                for(int k = 0; k < axes.length; k += 2){
                    float ux = axes[k], uy = axes[k + 1];
                    float reachA = parts[i + 2] * Math.abs(ux * cosA + uy * sinA) + parts[i + 3] * Math.abs(uy * cosA - ux * sinA);
                    float reachB = other.parts[j + 2] * Math.abs(ux * cosB + uy * sinB) + other.parts[j + 3] * Math.abs(uy * cosB - ux * sinB);
                    float along = dx * ux + dy * uy;
                    float overlap = reachA + reachB - Math.abs(along);
                    if(overlap <= 0f){
                        apart = true;
                        break;
                    }
                    if(overlap < smallest){
                        smallest = overlap;
                        float sign = along < 0f ? -1f : 1f;
                        nx = ux * sign;
                        ny = uy * sign;
                    }
                }

                if(!apart && smallest > deepest){
                    deepest = smallest;
                    pushX = nx * smallest;
                    pushY = ny * smallest;
                    hit = true;
                }
            }
        }
        if(!hit) return false;

        push.set(pushX, pushY);
        return true;
    }
}
