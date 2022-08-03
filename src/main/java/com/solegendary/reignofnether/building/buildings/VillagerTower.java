package com.solegendary.reignofnether.building.buildings;

import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingBlockData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;

public class VillagerTower extends Building {

    public final static String buildingName = "Villager Tower";

    public VillagerTower(LevelAccessor level, BlockPos originPos, Rotation rotation, String ownerName) {
        super();
        this.name = buildingName;
        this.ownerName = ownerName;
        this.blocks = getAbsoluteBlockData(getRelativeBlockData(), level, originPos, rotation);
        this.portraitBlock = Blocks.OAK_PLANKS;
    }

    public static ArrayList<BuildingBlock> getRelativeBlockData() {
        return (ArrayList) BuildingBlockData.VILLAGER_TOWER_BLOCKS.clone();
    }
}
