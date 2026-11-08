package modularis.type.units.nav;


public class ClearanceMap{
    public static final int cap = 20;

    public interface Solid{
        boolean solid(int x, int y);
    }

    public final int width, height;
    private final byte[] values;
    private final Solid solid;

    public ClearanceMap(int width, int height, Solid solid){
        this.width = width;
        this.height = height;
        this.solid = solid;
        values = new byte[width * height];
        recompute(0, 0, width - 1, height - 1);
    }

    public int get(int x, int y){
        return x < 0 || y < 0 || x >= width || y >= height ? 0 : values[x + y * width];
    }

    /** Call after the tile at this position changed solidity. */
    public void update(int x, int y){
        recompute(Math.max(x - cap, 0), Math.max(y - cap, 0), Math.min(x + cap, width - 1), Math.min(y + cap, height - 1));
    }

    private void recompute(int x1, int y1, int x2, int y2){
        for(int y = y1; y <= y2; y++){
            for(int x = x1; x <= x2; x++){
                values[x + y * width] = (byte)(solid.solid(x, y) ? 0 : cap);
            }
        }

        //two sweeps give exact square distances; cells outside the window act as fixed seeds
        for(int y = y1; y <= y2; y++){
            for(int x = x1; x <= x2; x++){
                int i = x + y * width;
                if(values[i] == 0) continue;
                int best = Math.min(get(x - 1, y), Math.min(get(x, y - 1), Math.min(get(x - 1, y - 1), get(x + 1, y - 1))));
                values[i] = (byte)Math.min(values[i], best + 1);
            }
        }
        for(int y = y2; y >= y1; y--){
            for(int x = x2; x >= x1; x--){
                int i = x + y * width;
                if(values[i] == 0) continue;
                int best = Math.min(get(x + 1, y), Math.min(get(x, y + 1), Math.min(get(x + 1, y + 1), get(x - 1, y + 1))));
                values[i] = (byte)Math.min(values[i], best + 1);
            }
        }
    }
}
