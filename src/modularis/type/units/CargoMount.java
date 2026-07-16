package modularis.type.units;

import arc.util.*;
import arc.math.*;
import mindustry.type.*;
import mindustry.world.modules.*;

import static mindustry.Vars.*;

public class CargoMount{
    public final PlacedModule placed;
    public final ItemModule items = new ItemModule();
    public Item lastItem;
    public Item visualItem;
    public float itemTime;

    public CargoMount(PlacedModule placed){
        this.placed = placed;
    }

    public int capacity(){
        return Math.max(placed.type.cargoCapacity, 0);
    }

    public int accept(Item item, int amount){
        if(item == null) return 0;
        Item stored = storedItem();
        if(stored != null && stored != item) return 0;
        int free = capacity() - items.total();
        return Math.max(0, Math.min(amount, free));
    }

    public Item storedItem(){
        if(lastItem != null && items.get(lastItem) > 0) return lastItem;
        for(int index = 0; index < items.length(); index++){
            if(items.get(index) > 0) return content.item(index);
        }
        return null;
    }

    public void add(Item item, int amount){
        if(item == null || amount <= 0) return;
        items.add(item, amount);
        lastItem = item;
        visualItem = item;
    }

    public int remove(Item item, int amount){
        if(item == null || amount <= 0) return 0;
        int removed = Math.min(amount, items.get(item));
        if(removed > 0){
            items.remove(item, removed);
            visualItem = item;
        }
        return removed;
    }

    public Item displayItem(){
        return storedItem();
    }

    public void updateVisual(){
        Item stored = storedItem();
        if(stored != null) visualItem = stored;
        itemTime = Mathf.lerpDelta(itemTime, stored == null ? 0f : 1f, 0.05f);
        /* Вообще раньше я сам вставлял Time.delta
        Но сейчас вот такая крутая штука есть в Arc или все таки она была? */
    }
}
