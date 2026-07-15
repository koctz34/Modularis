package modularis.type.units;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.input.*;
import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;

import static mindustry.Vars.*;

public class CargoInteraction{
    public static final String packetName = "modularis-cargo-transfer";
    public static final Vec2 position = new Vec2();
    private static final float playerSelectRange = mobile ? 17f : 11f;
    private static InputProcessor inputProcessor;
    private static boolean cargoDragging;
    private static Item draggedItem;
    private static CargoMount draggedCargo;

    public static void installInput(){
        if(Core.input == null) return;
        if(inputProcessor != null && Core.input.getInputProcessors().contains(inputProcessor)) return;

        inputProcessor = new InputProcessor(){
            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, KeyCode button){
                if(button != KeyCode.mouseLeft || player == null || player.dead()) return false;

                Vec2 mouse = Core.input.mouseWorld(screenX, screenY);
                if(player.unit() instanceof ModularUnitEntity modular && player.within(mouse.x, mouse.y, playerSelectRange)){
                    CargoMount selected = cargoUnderCursor(modular, mouse.x, mouse.y);
                    if(selected == null || selected.displayItem() == null) return false;
                    cargoDragging = true;
                    draggedCargo = selected;
                    draggedItem = selected.displayItem();
                    modular.stack.item = draggedItem;
                    control.input.droppingItem = true;
                    player.shooting = false;
                    return true;
                }
                return false;
            }

            @Override
            public boolean touchUp(int screenX, int screenY, int pointer, KeyCode button){
                if(button != KeyCode.mouseLeft || !cargoDragging) return false;

                Vec2 mouse = Core.input.mouseWorld(screenX, screenY);
                boolean transferred = player != null && !player.dead() &&
                    tryDepositToBuilding(mouse.x, mouse.y);
                if(!transferred && player != null && player.unit() instanceof ModularUnitEntity modular && draggedItem != null){
                    int dropped = draggedCargo == null ? 0 : draggedCargo.remove(draggedItem, draggedCargo.items.get(draggedItem));
                    if(dropped > 0){
                        Fx.dropItem.at(modular.x, modular.y, modular.angleTo(mouse.x, mouse.y), Color.white, draggedItem);
                        transferred = true;
                    }
                }
                cargoDragging = false;
                draggedItem = null;
                draggedCargo = null;
                control.input.droppingItem = false;
                if(player != null) player.shooting = false;
                return transferred;
            }
        };
        Core.input.getInputProcessors().insert(0, inputProcessor);
    }

    public static void update(){
        if(headless || !state.isGame() || player == null || player.dead()) return;
        installInput();
        if(Core.scene.hasDialog() || Core.scene.hasMouse()) return;
    }

    private static boolean isDropActive(){
        return !headless && state.isGame() && player != null && !player.dead()
            && player.unit() != null && player.unit().hasItem()
            && control.input.isDroppingItem();
    }

    private static boolean tryDeposit(float x, float y){
        CargoTarget target = findTarget(x, y, true);
        if(target == null) return false;

        Unit source = player.unit();
        if(target.unit == source) return false;
        Item item = activeItem(source);
        int amount = activeAmount(source, item);
        if(item == null || amount <= 0) return false;
        int accepted = target.cargo.accept(item, amount);
        if(accepted <= 0) return false;

        control.input.droppingItem = false;
        player.shooting = false;
        if(net.client()){
            removeFromUnit(source, item, accepted);
            target.cargo.add(item, accepted);
            showTransferEffect(source, target.unit, target.cargo, item, accepted);
        }
        send(target.unit, target.cargo, item, accepted, true);
        return true;
    }

    private static boolean tryDepositToBuilding(float x, float y){
        Unit source = player.unit();
        if(!(source instanceof ModularUnitEntity modular)) return false;

        Building build = world.buildWorld(x, y);
        Item item = draggedItem != null ? draggedItem : modular.cargoItem();
        if(build == null || item == null || build.items == null || !build.interactable(source.team()) ||
            !build.allowDeposit() || !source.within(build, itemTransferRange)) return false;

        int available = draggedCargo == null ? 0 : draggedCargo.items.get(item);
        int accepted = build.acceptStack(item, available, source);
        if(accepted <= 0) return false;

        if(net.client()){
            int removed = draggedCargo.remove(item, accepted);
            if(removed <= 0) return false;
            showCargoToBuildingEffect(modular, build, item, removed);
        }
        sendBuilding(build, draggedCargo, item, accepted);
        return true;
    }

    public static void drawOverlay(){
        if(headless || !state.isGame() || player == null || player.dead()) return;

        if(!mobile && !cargoDragging && player.unit() instanceof ModularUnitEntity modular){
            Vec2 hover = Core.input.mouseWorld();
            if(!Core.scene.hasMouse() && player.within(hover.x, hover.y, playerSelectRange) && cargoUnderCursor(modular, hover.x, hover.y) != null){
                Core.graphics.cursor(ui.unloadCursor);
            }
        }
        if(!control.input.isDroppingItem()) return;

        Unit source = player.unit();
        Item item = activeItem(source);
        if(source == null || item == null) return;

        Vec2 mouse = Core.input.mouseWorld();
        Draw.rect(item.fullIcon, mouse.x, mouse.y, 8f, 8f);
        Draw.color(Pal.accent);
        Lines.circle(mouse.x, mouse.y, 6f + Mathf.absin(Time.time, 5f, 1f));
        Draw.reset();

        if(source instanceof ModularUnitEntity modular){
            Building build = world.buildWorld(mouse.x, mouse.y);
            if(build != null && build.items != null && build.interactable(source.team()) && build.allowDeposit() &&
                source.within(build, itemTransferRange) && draggedCargo != null &&
                build.acceptStack(item, draggedCargo.items.get(item), source) > 0){
                Lines.stroke(3f, Pal.gray);
                Lines.square(build.x, build.y, build.block.size * tilesize / 2f + 3f + Mathf.absin(Time.time, 5f, 1f));
                Lines.stroke(1f, Pal.place);
                Lines.square(build.x, build.y, build.block.size * tilesize / 2f + 2f + Mathf.absin(Time.time, 5f, 1f));
                Draw.reset();
            }
        }
    }

    static CargoTarget findTarget(float x, float y, boolean depositing){
        CargoTarget[] result = {null};
        float[] distance = {Float.MAX_VALUE};
        Unit source = player.unit();
        if(source == null) return null;
        Item sourceItem = activeItem(source);
        Groups.unit.each(unit -> {
            if(!(unit instanceof ModularUnitEntity modular) || unit.team != player.team()) return;
            if(modular == source) return;
            for(CargoMount cargo : modular.cargoMounts){
                modular.modulePos(cargo.placed, position);
                float radius = Math.max(cargo.placed.type.w, cargo.placed.type.h) * ModularUnitType.cellWorld() / 2f + 5f;
                float current = position.dst(x, y);
                if(depositing && (sourceItem == null || cargo.accept(sourceItem, 1) <= 0)) continue;
                if(!depositing && cargo.displayItem() == null) continue;
                if(current <= radius && current < distance[0] && source.within(position.x, position.y, itemTransferRange)){
                    distance[0] = current;
                    result[0] = new CargoTarget(modular, cargo);
                }
            }
        });
        return result[0];
    }

    static void send(ModularUnitEntity unit, CargoMount cargo, Item item, int amount, boolean deposit){
        if(item == null || amount <= 0) return;
        String data = (deposit ? "D" : "W") + "," + unit.id + "," + cargo.placed.x + "," + cargo.placed.y + "," + item.name + "," + amount;
        if(net.client()){
            Call.serverPacketReliable(packetName, data);
        }else{
            handle(player, data);
        }
    }

    static void sendBuilding(Building build, CargoMount cargo, Item item, int amount){
        if(build == null || cargo == null || item == null || amount <= 0) return;
        String data = "B," + build.pos() + "," + cargo.placed.x + "," + cargo.placed.y + "," + item.name + "," + amount;
        if(net.client()){
            Call.serverPacketReliable(packetName, data);
        }else{
            handle(player, data);
        }
    }

    public static void handle(Player player, String data){
        if(player == null || player.dead() || data == null) return;
        String[] parts = data.split(",", 6);
        try{
            if(parts[0].equals("B")){
                handleBuildingDeposit(player, parts);
                return;
            }

            if(parts.length != 6) return;

            boolean deposit = parts[0].equals("D");
            if(!deposit && !parts[0].equals("W")) return;
            Unit found = Groups.unit.getByID(Integer.parseInt(parts[1]));
            if(!(found instanceof ModularUnitEntity target) || target.team != player.team()) return;
            CargoMount cargo = target.cargoAt(Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
            Item item = content.item(parts[4]);
            int amount = Integer.parseInt(parts[5]);
            if(cargo == null || item == null || amount <= 0) return;

            target.modulePos(cargo.placed, position);
            Unit source = player.unit();
            if(source == null || !source.within(position.x, position.y, itemTransferRange)) return;

            if(deposit){
                return;
            }else{
                if(target != source) return;
            }
        }catch(RuntimeException ignored){
        }
    }

    private static void handleBuildingDeposit(Player player, String[] parts){
        if(parts.length != 6 || player.unit() == null || !(player.unit() instanceof ModularUnitEntity modular)) return;
        Building build = world.build(Integer.parseInt(parts[1]));
        CargoMount cargo = modular.cargoAt(Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        Item item = content.item(parts[4]);
        int amount = Integer.parseInt(parts[5]);
        if(build == null || item == null || amount <= 0 || build.items == null || !build.interactable(player.team()) ||
            cargo == null || !build.allowDeposit() || !player.unit().within(build, itemTransferRange)) return;

        int accepted = build.acceptStack(item, Math.min(amount, cargo.items.get(item)), modular);
        if(accepted <= 0) return;
        int removed = cargo.remove(item, accepted);
        if(removed <= 0) return;
        build.handleStack(item, removed, modular);
        showCargoToBuildingEffect(modular, build, item, removed);
    }

    static Item activeItem(Unit unit){
        if(unit == null) return null;
        if(cargoDragging && draggedItem != null) return draggedItem;
        if(unit.stack.amount > 0) return unit.stack.item;
        return unit instanceof ModularUnitEntity modular ? modular.cargoItem() : null;
    }

    static int activeAmount(Unit unit, Item item){
        if(unit == null || item == null) return 0;
        if(unit.stack.amount > 0 && unit.stack.item == item) return unit.stack.amount;
        return unit instanceof ModularUnitEntity modular ? modular.cargoAmount(item) : 0;
    }

    private static CargoMount cargoUnderCursor(ModularUnitEntity unit, float x, float y){
        CargoMount best = null;
        float bestDistance = Float.MAX_VALUE;
        for(CargoMount cargo : unit.cargoMounts){
            if(cargo.displayItem() == null) continue;
            unit.modulePos(cargo.placed, position);
            float radius = Math.max(cargo.placed.type.w, cargo.placed.type.h) * ModularUnitType.cellWorld() / 2f + 5f;
            float distance = position.dst(x, y);
            if(distance <= radius && distance < bestDistance){
                best = cargo;
                bestDistance = distance;
            }
        }
        return best;
    }

    static int removeFromUnit(Unit unit, Item item, int amount){
        if(unit instanceof ModularUnitEntity modular && unit.stack.amount <= 0){
            return modular.removeCargo(item, amount);
        }
        if(unit.stack.item != item) return 0;
        int removed = Math.min(amount, unit.stack.amount);
        unit.stack.amount -= removed;
        return removed;
    }

    private static void showTransferEffect(Unit source, ModularUnitEntity target, CargoMount cargo, Item item, int amount){
        target.modulePos(cargo.placed, position);
        float sourceX = source.x;
        float sourceY = source.y;
        for(int index = 0; index < Mathf.clamp(amount / 3, 1, 8); index++){
            int delay = index * 3;
            Vec2 destination = new Vec2(position);
            Time.run(delay, () -> Fx.itemTransfer.at(sourceX, sourceY, amount, item.color, destination));
        }
    }

    private static void showCargoToBuildingEffect(ModularUnitEntity source, Building target, Item item, int amount){
        position.set(source.x, source.y);
        for(CargoMount cargo : source.cargoMounts){
            if(cargo.items.get(item) > 0){
                source.modulePos(cargo.placed, position);
                break;
            }
        }
        float sourceX = position.x;
        float sourceY = position.y;
        float targetX = target.x;
        float targetY = target.y;
        for(int index = 0; index < Mathf.clamp(amount / 3, 1, 8); index++){
            int delay = index * 3;
            Time.run(delay, () -> Fx.itemTransfer.at(sourceX, sourceY, amount, item.color, new Vec2(targetX, targetY)));
        }
    }

    static class CargoTarget{
        final ModularUnitEntity unit;
        final CargoMount cargo;

        CargoTarget(ModularUnitEntity unit, CargoMount cargo){
            this.unit = unit;
            this.cargo = cargo;
        }
    }
}
