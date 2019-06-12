package io.anuke.mindustry.entities.type.base;

import io.anuke.arc.math.Mathf;
import io.anuke.arc.math.geom.Geometry;
import io.anuke.arc.util.Structs;
import io.anuke.mindustry.content.Blocks;
import io.anuke.mindustry.entities.traits.MinerTrait;
import io.anuke.mindustry.entities.type.*;
import io.anuke.mindustry.entities.units.UnitState;
import io.anuke.mindustry.gen.Call;
import io.anuke.mindustry.type.*;
import io.anuke.mindustry.world.*;
import io.anuke.mindustry.world.meta.BlockFlag;

import java.io.*;

import static io.anuke.mindustry.Vars.world;

/** A drone that only mines.*/
public class MinerDrone extends FlyingUnit implements MinerTrait{
    protected Item targetItem;
    protected Tile mineTile;

    public final UnitState

    mine = new UnitState(){
        public void entered(){
            target = null;
        }

        public void update(){
            TileEntity entity = getClosestCore();

            if(entity == null) return;

            if(targetItem == null){
                findItem();
            }

            //core full
            if(targetItem != null && entity.block.acceptStack(targetItem, 1, entity.tile, MinerDrone.this) == 0){
                MinerDrone.this.clearItem();
                return;
            }

            //if inventory is full, drop it off.
            if(item.amount >= getItemCapacity()){
                setState(drop);
            }else{
                if(targetItem != null && !acceptsItem(targetItem)){
                    setState(drop);
                    return;
                }

                retarget(() -> {
                    if(getMineTile() == null){
                        findItem();
                    }

                    if(targetItem == null) return;

                    target = world.indexer.findClosestOre(x, y, targetItem);
                });

                if(target instanceof Tile){
                    moveTo(type.range / 1.5f);

                    if(dst(target) < type.range && mineTile != target){
                        setMineTile((Tile)target);
                    }

                    if(((Tile)target).block() != Blocks.air){
                        setState(drop);
                    }
                }else{
                    //nothing to mine anymore, core full: circle spawnpoint
                    if(getSpawner() != null){
                        target = getSpawner();

                        circle(40f);
                    }
                }
            }
        }

        public void exited(){
            setMineTile(null);
        }
    },

    drop = new UnitState(){
        public void entered(){
            target = null;
        }

        public void update(){
            if(item.amount == 0){
                setState(mine);
                return;
            }

            if(item.item.type != ItemType.material){
                item.amount = 0;
                setState(mine);
                return;
            }

            target = getClosestCore();

            if(target == null) return;

            TileEntity tile = (TileEntity)target;

            if(dst(target) < type.range){
                if(tile.tile.block().acceptStack(item.item, item.amount, tile.tile, MinerDrone.this) == item.amount){
                    Call.transferItemTo(item.item, item.amount, x, y, tile.tile);
                    item.amount = 0;
                }

                setState(mine);
            }

            circle(type.range / 1.8f);
        }
    },

    retreat = new UnitState(){
        public void entered(){
            target = null;
        }

        public void update(){
            if(health >= maxHealth()){
                state.set(attack);
            }else if(!targetHasFlag(BlockFlag.repair)){
                retarget(() -> {
                    Tile repairPoint = Geometry.findClosest(x, y, world.indexer.getAllied(team, BlockFlag.repair));
                    if(repairPoint != null){
                        target = repairPoint;
                    }else{
                        setState(mine);
                    }
                });
            }else{
                circle(40f);
            }
        }
    };

    @Override
    public void update(){
        super.update();

        updateMining();
    }

    @Override
    protected void updateRotation(){
        if(target != null && state.is(mine)){
            rotation = Mathf.slerpDelta(rotation, angleTo(target), 0.3f);
        }else{
            rotation = Mathf.slerpDelta(rotation, velocity.angle(), 0.3f);
        }
    }

    @Override
    public void behavior(){
        if(health <= health * type.retreatPercent){
            setState(retreat);
        }
    }

    @Override
    public void drawOver(){
        drawMining();
    }

    @Override
    public boolean canMine(Item item){
        return type.toMine.contains(item);
    }

    @Override
    public float getMinePower(){
        return type.minePower;
    }

    @Override
    public Tile getMineTile(){
        return mineTile;
    }

    @Override
    public void setMineTile(Tile tile){
        mineTile = tile;
    }

    @Override
    public void write(DataOutput data) throws IOException{
        super.write(data);
        data.writeInt(mineTile == null || !state.is(mine) ? Pos.invalid : mineTile.pos());
    }

    @Override
    public void read(DataInput data) throws IOException{
        super.read(data);
        mineTile = world.tile(data.readInt());
    }

    protected void findItem(){
        TileEntity entity = getClosestCore();
        if(entity == null){
            return;
        }
        targetItem = Structs.findMin(type.toMine, world.indexer::hasOre, (a, b) -> -Integer.compare(entity.items.get(a), entity.items.get(b)));
    }
}
