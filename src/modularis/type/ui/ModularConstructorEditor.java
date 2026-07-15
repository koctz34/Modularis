package modularis.type.ui;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.input.*;
import arc.math.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;

import modularis.content.*;
import modularis.type.units.*;
import modularis.type.units.modules.*;

public class ModularConstructorEditor extends BaseDialog{
    /** Shared instance. */
    public static final ModularConstructorEditor dialog = new ModularConstructorEditor();

    private ModularDesign design = new ModularDesign();
    private @Nullable ModuleType selected;

    private final Seq<ModBtn> buttonRefs = new Seq<>();
    private final Table infoTable = new Table();
    private @Nullable ModuleType shownInfo = null;
    private boolean forceInfo;

    private Canvas canvas;
    private @Nullable Runnable onSave;

    public ModularConstructorEditor(){
        super("Modular Editor");
        MdlModules.load();
        build();
        addCloseListener();
        //persist / sync the blueprint whenever the editor closes
        hidden(() -> {
            if(onSave != null) onSave.run();
        });
    }

    public void show(ModularDesign design, @Nullable Runnable onSave){
        this.design = design;
        this.onSave = onSave;
        this.selected = null;
        this.shownInfo = null;
        this.forceInfo = true;
        canvas.reset();
        super.show();
    }

    private void build(){
        cont.clear();

        //left: parts list + info
        cont.table(Tex.buttonTrans, left -> {
            left.top();
            left.add("Modules").color(Pal.accent).growX().left().pad(6f).row();
            left.image().color(Pal.accent).height(2f).growX().pad(2f).row();

            //scrollable categorised list
            Table list = new Table();
            list.top();
            buildList(list);
            ScrollPane pane = new ScrollPane(list, Styles.smallPane);
            pane.setScrollingDisabled(true, false);
            pane.setFadeScrollBars(false);
            left.add(pane).grow().pad(4f).row();

            //hovered / selected module stats - FIXED height so hovering never reflows the
            //list above it (that reflow used to bounce the cursor off the button = flicker)
            infoTable.top().left();
            infoTable.update(this::refreshInfo);
            ScrollPane infoPane = new ScrollPane(infoTable, Styles.smallPane);
            infoPane.setFadeScrollBars(false);
            left.add(infoPane).growX().height(150f).pad(4f).row();

            //always-on machine physics readout, also fixed height
            left.image().color(Pal.gray).height(2f).growX().pad(2f).row();
            left.add("Machine").color(Pal.accent).growX().left().pad(4f).row();
            Table machine = new Table();
            machine.left().top();
            machine.labelWrap(this::machineSummary).growX().left();
            ScrollPane machinePane = new ScrollPane(machine, Styles.smallPane);
            machinePane.setFadeScrollBars(false);
            left.add(machinePane).growX().height(150f).pad(4f).row();
        }).width(320f).growY();

        canvas = new Canvas();
        cont.add(canvas).grow();

        buttons.clearChildren();
        buttons.button("Back", Icon.left, this::hide).size(160f, 55f);
        buttons.button("Clear", Icon.trash, () -> design.clear()).size(160f, 55f);
    }

    private void buildList(Table list){
        buttonRefs.clear();
        for(ModuleCategory cat : ModuleCategory.values()){
            Seq<ModuleType> inCat = MdlModules.all.select(m -> m.category == cat);
            if(inCat.isEmpty()) continue;

            list.add(cat.title).color(cat.color).left().growX().padTop(8f).padLeft(4f).row();
            list.image().color(cat.color).height(2f).growX().pad(2f).row();

            Table grid = new Table();
            grid.left();
            int col = 0;
            for(ModuleType type : inCat){
                grid.add(moduleButton(type)).size(52f).pad(4f);
                if(++col % 4 == 0){
                    grid.row();
                    col = 0;
                }
            }
            list.add(grid).left().growX().row();
        }
    }

    private Element moduleButton(ModuleType type){
        TextureRegionDrawable icon = new TextureRegionDrawable(type.region());
        ImageButton button = new ImageButton(icon, Styles.clearNoneTogglei);
        button.getImageCell().grow().pad(6f);
        button.clicked(() -> selected = (selected == type ? null : type));
        button.update(() -> {
            button.setChecked(selected == type);
            //dimmed when it can't be added: at its count limit, or no free slots for it
            boolean blocked = !design.canAdd(type);
            button.getImage().setColor(selected == type ? Pal.accent : (blocked ? Color.gray : Color.white));
        });
        buttonRefs.add(new ModBtn(button, type));
        return button;
    }

    private void refreshInfo(){
        ModuleType show = null;
        for(ModBtn b : buttonRefs){
            if(b.button.isOver()){
                show = b.type;
                break;
            }
        }
        if(show == null) show = selected;

        if(show == shownInfo && !forceInfo) return;
        forceInfo = false;
        shownInfo = show;

        infoTable.clear();
        if(show == null){
            infoTable.add("Hover a module to see its stats,\nor pick one and place it on the grid.")
                .color(Pal.lightishGray).left().wrap().growX();
            return;
        }
        show.display(infoTable);
    }

    private String machineSummary(){
        ModularPhysics.Stats s = ModularPhysics.compute(design);
        StringBuilder sb = new StringBuilder();

        sb.append("Modules: ").append(design.modules.size).append('\n');
        sb.append("Weight: ").append(Strings.autoFixed(s.weight, 1)).append('\n');
        sb.append("Health: ").append(Strings.autoFixed(design.totalHealth(), 0)).append('\n');
        if(s.armor > 0f) sb.append("Armor: ").append(Strings.autoFixed(s.armor, 1)).append('\n');
        if(s.cargoCapacity > 0) sb.append("Cargo: ").append(s.cargoCapacity).append(" items\n");
        if(s.canBuild()) sb.append("Build: [lime]x").append(Strings.autoFixed(s.buildSpeed, 2)).append("[]\n");
        if(s.canMine()){
            sb.append("Drill: [lime]tier ").append(s.drillTier)
                .append(", x").append(Strings.autoFixed(s.drillSpeed, 2)).append("[]\n");
        }

        //power
        String pcol = s.underpowered ? "[scarlet]" : "[lime]";
        sb.append("Power: ").append(pcol)
            .append(Strings.autoFixed(s.powerProd * 60f, 1)).append('/')
            .append(Strings.autoFixed(s.powerUse * 60f, 1)).append("[]\n");

        //weapon fire rate: starved turrets cycle slowly, no core means they can't fire at all
        if(!s.canShoot()){
            sb.append("Turrets: [scarlet]disarmed[]\n");
        }else if(s.fireRateMultiplier() < 0.999f){
            sb.append("Fire rate: [orange]x")
                .append(Strings.autoFixed(s.fireRateMultiplier(), 2)).append("[]\n");
        }

        //load
        if(s.hasWheels){
            String lcol = s.overloaded ? "[scarlet]" : "[lime]";
            sb.append("Load: ").append(lcol).append(Strings.autoFixed(s.loadPercent(), 0)).append("%[]\n");
        }

        //weight-imposed speed ceiling
        String wcol = s.weightFactor < 0.5f ? "[orange]" : "[lightgray]";
        sb.append("Weight cap: ").append(wcol)
            .append(Strings.autoFixed(s.weightSpeedPercent(), 0)).append("%[] speed\n");

        //balance: is the mass sitting over the drive parts?
        if(s.hasWheels){
            String bcol = s.balanceFactor < 0.6f ? "[scarlet]" : s.unbalanced ? "[orange]" : "[lime]";
            sb.append("Balance: ").append(bcol)
                .append(Strings.autoFixed(s.balancePercent(), 0)).append("%[]\n");
        }

        //hover lift limit
        if(s.hasHover){
            String hcol = s.hoverOverweight ? "[scarlet]" : "[cyan]";
            sb.append("Hover lift: ").append(hcol)
                .append(Strings.autoFixed(s.weight, 0)).append('/')
                .append(Strings.autoFixed(s.hoverMaxWeight, 0)).append("[]\n");
        }

        //C4 / kamikaze
        if(s.isKamikaze()){
            sb.append("[scarlet]KAMIKAZE[] (").append(s.c4Count).append(" C4)\n");
            sb.append("Blast: [scarlet]").append(Strings.autoFixed(s.blastDamage, 0))
                .append("[] dmg, ").append(Strings.autoFixed(s.blastRadius / 8f, 1)).append(" tiles\n");
        }

        //stat multipliers stacked from every module carrying them
        if(s.hasMultipliers()){
            convMult(sb, "Health", s.healthMultiplier, false);
            convMult(sb, "Damage", s.damageMultiplier, false);
            convMult(sb, "Fire rate", s.reloadMultiplier, false);
            convMult(sb, "Speed", s.speedMod, false);
        }

        //top speed
        sb.append("Top speed: ").append(Strings.autoFixed(s.speedTiles(), 1)).append(" tiles/s\n");

        //slots granted by the command core(s) vs slots consumed
        for(SlotType st : SlotType.values()){
            if(st == SlotType.none) continue;
            int provided = design.slotsProvided(st), used = design.slotsUsed(st);
            if(provided == 0 && used == 0) continue;
            String scol = used > provided ? "[scarlet]" : used == provided ? "[orange]" : "[lime]";
            sb.append(st.title).append(" slots: ").append(scol)
                .append(used).append('/').append(provided).append("[]\n");
        }

        //warnings
        if(!s.hasRoot) sb.append("[scarlet]! No command core[]\n");
        if(!s.hasWheels) sb.append("[scarlet]! No wheels - immobile[]\n");
        if(s.underpowered) sb.append("[orange]! Underpowered[]\n");
        if(s.overloaded) sb.append("[orange]! Overloaded - too heavy for its drivetrain[]\n");
        if(s.unbalanced) sb.append("[orange]! Unbalanced - mass is not over the drive parts[]\n");
        if(s.hoverOverweight) sb.append("[scarlet]! Too heavy to hover - it lies grounded[]\n");
        if(s.inactiveCount > 0){
            sb.append("[scarlet]! ").append(s.inactiveCount)
                .append(" module(s) have no slot - dead weight[]\n");
        }
        if(s.hasRoot && s.hasWheels && !s.overloaded && !s.underpowered && !s.unbalanced
            && !design.slotsOverused()){
            sb.append("[lime]Ready to roll[]");
        }

        return sb.toString();
    }

    /** Appends a convertor multiplier line, but only when it actually changes something. */
    private void convMult(StringBuilder sb, String key, float value, boolean lowerIsBetter){
        if(Mathf.equal(value, 1f, 0.001f)) return;
        boolean good = lowerIsBetter ? value < 1f : value > 1f;
        sb.append(key).append(": ").append(good ? "[lime]" : "[scarlet]")
            .append('x').append(Strings.autoFixed(value, 2)).append("[]\n");
    }

    private static class ModBtn{
        final Button button;
        final ModuleType type;

        ModBtn(Button button, ModuleType type){
            this.button = button;
            this.type = type;
        }
    }

    /** The infinite build grid. Draws itself and handles pan/zoom/place input. */
    private class Canvas extends Element{
        static final float baseCell = 22f, minZoom = 0.4f, maxZoom = 3f;

        float camX = 0f, camY = 0f, zoom = 1f;
        float hoverLx, hoverLy;
        boolean panning, painting;
        float pressLx, pressLy, lastLx, lastLy;
        boolean dragged;

        Canvas(){
            touchable = Touchable.enabled;
            addListener(new InputListener(){
                @Override
                public boolean touchDown(InputEvent event, float mx, float my, int pointer, KeyCode button){
                    pressLx = lastLx = hoverLx = mx;
                    pressLy = lastLy = hoverLy = my;
                    dragged = false;

                    if(button == KeyCode.mouseLeft){
                        painting = true;
                        leftAction(mx, my);
                    }else if(button == KeyCode.mouseRight || button == KeyCode.mouseMiddle){
                        panning = true;
                    }
                    return true;
                }

                @Override
                public void touchDragged(InputEvent event, float mx, float my, int pointer){
                    float dx = mx - lastLx, dy = my - lastLy;
                    if(Math.abs(mx - pressLx) > 4f || Math.abs(my - pressLy) > 4f) dragged = true;

                    hoverLx = mx;
                    hoverLy = my;

                    if(panning){
                        camX -= dx / cellPx();
                        camY -= dy / cellPx();
                    }else if(painting && selected != null){
                        leftAction(mx, my);
                    }

                    lastLx = mx;
                    lastLy = my;
                }

                @Override
                public void touchUp(InputEvent event, float mx, float my, int pointer, KeyCode button){
                    //right click with no drag cancels the current selection
                    if(button == KeyCode.mouseRight && !dragged) selected = null;
                    panning = false;
                    painting = false;
                }

                @Override
                public boolean mouseMoved(InputEvent event, float mx, float my){
                    hoverLx = mx;
                    hoverLy = my;
                    return false;
                }

                @Override
                public boolean scrolled(InputEvent event, float mx, float my, float amountX, float amountY){
                    //zoom towards the cursor
                    float preX = cellX(mx), preY = cellY(my);
                    zoom = Mathf.clamp(zoom * (1f - amountY * 0.12f), minZoom, maxZoom);
                    camX = preX - (mx - getWidth() / 2f) / cellPx();
                    camY = preY - (my - getHeight() / 2f) / cellPx();
                    return true;
                }
            });
        }

        void reset(){
            camX = design.isEmpty() ? 0f : design.centerX();
            camY = design.isEmpty() ? 0f : design.centerY();
            zoom = 1f;
        }

        float cellPx(){
            return baseCell * zoom;
        }

        float cellX(float lx){
            return camX + (lx - getWidth() / 2f) / cellPx();
        }

        float cellY(float ly){
            return camY + (ly - getHeight() / 2f) / cellPx();
        }

        void leftAction(float mx, float my){
            int cx = Mathf.floor(cellX(mx));
            int cy = Mathf.floor(cellY(my));
            if(selected != null){
                design.place(selected, cx, cy);
            }else{
                design.removeAt(cx, cy);
            }
        }

        //absolute screen position of a cell coordinate (may be fractional)
        float screenX(float cx){
            return x + getWidth() / 2f + (cx - camX) * cellPx();
        }

        float screenY(float cy){
            return y + getHeight() / 2f + (cy - camY) * cellPx();
        }

        @Override
        public void draw(){
            //background
            Draw.color(0.09f, 0.09f, 0.11f, 1f);
            Fill.crect(x, y, getWidth(), getHeight());
            Draw.reset();

            if(!clipBegin(x, y, getWidth(), getHeight())) return;

            drawGrid();
            drawModules();
            drawGhost();

            clipEnd();

            //frame
            Draw.color(Pal.accent);
            Lines.stroke(2f);
            Lines.rect(x, y, getWidth(), getHeight());
            Draw.reset();
        }

        void drawGrid(){
            float cp = cellPx();
            int pad = 1;
            int left = Mathf.floor(camX - (getWidth() / 2f) / cp) - pad;
            int right = Mathf.ceil(camX + (getWidth() / 2f) / cp) + pad;
            int bottom = Mathf.floor(camY - (getHeight() / 2f) / cp) - pad;
            int top = Mathf.ceil(camY + (getHeight() / 2f) / cp) + pad;

            //avoid drawing an absurd number of lines when zoomed far out
            if((right - left) > 400 || (top - bottom) > 400) return;

            Lines.stroke(1f);
            for(int gx = left; gx <= right; gx++){
                boolean axis = gx == 0;
                Draw.color(axis ? Pal.accent : Color.gray, axis ? 0.6f : 0.18f);
                float sx = screenX(gx);
                Lines.line(sx, screenY(bottom), sx, screenY(top));
            }
            for(int gy = bottom; gy <= top; gy++){
                boolean axis = gy == 0;
                Draw.color(axis ? Pal.accent : Color.gray, axis ? 0.6f : 0.18f);
                float sy = screenY(gy);
                Lines.line(screenX(left), sy, screenX(right), sy);
            }
            Draw.reset();
        }

        void drawModules(){
            float cp = cellPx();
            boolean deleteHover = selected == null;
            int hx = Mathf.floor(cellX(hoverLx)), hy = Mathf.floor(cellY(hoverLy));

            //bodies (each module decides how it draws), then overlays (cells, turrets)
            for(PlacedModule m : design.modules){
                float sx = screenX(m.x + m.type.w / 2f);
                float sy = screenY(m.y + m.type.h / 2f);
                float w = m.type.w * cp, h = m.type.h * cp;
                m.type.drawBody(null, m, sx, sy, w, h, 0f);
            }
            for(PlacedModule m : design.modules){
                float sx = screenX(m.x + m.type.w / 2f);
                float sy = screenY(m.y + m.type.h / 2f);
                float w = m.type.w * cp, h = m.type.h * cp;
                m.type.drawTop(null, m, sx, sy, w, h, 0f);
            }

            //modules with no slot to run in: struck through in red, they are dead weight
            for(PlacedModule m : design.modules){
                if(design.isActive(m)) continue;
                float sx = screenX(m.x + m.type.w / 2f);
                float sy = screenY(m.y + m.type.h / 2f);
                float w = m.type.w * cp, h = m.type.h * cp;
                Draw.color(Pal.remove, 0.4f);
                Fill.crect(sx - w / 2f, sy - h / 2f, w, h);
            }

            //delete-highlight
            if(deleteHover){
                for(PlacedModule m : design.modules){
                    if(!m.covers(hx, hy)) continue;
                    float sx = screenX(m.x + m.type.w / 2f);
                    float sy = screenY(m.y + m.type.h / 2f);
                    float w = m.type.w * cp, h = m.type.h * cp;
                    Draw.color(Pal.remove, 0.45f);
                    Fill.crect(sx - w / 2f, sy - h / 2f, w, h);
                }
            }
            Draw.reset();
        }

        void drawGhost(){
            if(selected == null) return;
            float cp = cellPx();
            int cx = Mathf.floor(cellX(hoverLx));
            int cy = Mathf.floor(cellY(hoverLy));
            boolean ok = design.canPlace(selected, cx, cy);

            float sx = screenX(cx + selected.w / 2f);
            float sy = screenY(cy + selected.h / 2f);
            float w = selected.w * cp, h = selected.h * cp;

            Draw.color(ok ? Pal.accent : Pal.remove, 0.35f);
            Fill.crect(sx - w / 2f, sy - h / 2f, w, h);

            Draw.color(Color.white, ok ? 0.7f : 0.4f);
            Draw.rect(selected.region(), sx, sy, w, h);
            Draw.reset();
        }
    }
}
