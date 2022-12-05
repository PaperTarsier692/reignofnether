package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.resources.ResourceBlocks;
import com.solegendary.reignofnether.resources.ResourceName;
import com.solegendary.reignofnether.unit.goals.GatherResourcesGoal;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.unit.units.monsters.CreeperUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.ArrayList;

public class UnitActionItem {
    private final String ownerName;
    private final UnitAction action;
    private final int unitId;
    private final int[] unitIds;
    private final BlockPos preselectedBlockPos;

    public UnitActionItem(
            String ownerName,
            UnitAction action,
            int unitId,
            int[] unitIds,
            BlockPos preselectedBlockPos) {

        this.ownerName = ownerName;
        this.action = action;
        this.unitId = unitId;
        this.unitIds = unitIds;
        this.preselectedBlockPos = preselectedBlockPos;
    }

    public void resetBehaviours(Unit unit) {
        Unit.resetBehaviours(unit);
        if (unit instanceof WorkerUnit workerUnit)
            WorkerUnit.resetBehaviours(workerUnit);
        if (unit instanceof AttackerUnit attackerUnit)
            AttackerUnit.resetBehaviours(attackerUnit);
    }

    // can be done server or clientside - but only serverside will have an effect on the world
    // clientside actions are purely for tracking data
    public void action(Level level) {

        // filter out unowned units and non-unit entities
        ArrayList<Unit> actionableUnits = new ArrayList<>();
        for (int id : unitIds)
            if (level.getEntity(id) instanceof Unit unit && unit.getOwnerName().equals(this.ownerName))
                actionableUnits.add(unit);

        for (Unit unit : actionableUnits) {

            // have to do this before resetBehaviours so we can assign the correct resourceName first
            if (action == UnitAction.TOGGLE_GATHER_TARGET) {
                if (unit instanceof WorkerUnit workerUnit) {
                    GatherResourcesGoal goal = workerUnit.getGatherResourceGoal();
                    ResourceName targetResourceName = goal.getTargetResourceName();
                    resetBehaviours(unit);
                    if (goal != null) {
                        switch (targetResourceName) {
                            case NONE -> goal.setTargetResourceName(ResourceName.FOOD);
                            case FOOD -> goal.setTargetResourceName(ResourceName.WOOD);
                            case WOOD -> goal.setTargetResourceName(ResourceName.ORE);
                            case ORE -> goal.setTargetResourceName(ResourceName.NONE);
                        }
                    }
                }
            }
            else
                resetBehaviours(unit);

            switch (action) {
                case STOP -> { }
                case HOLD -> {
                    unit.setHoldPosition(true);
                }
                case MOVE -> {
                    ResourceName resName = ResourceBlocks.getResourceBlockName(preselectedBlockPos, level);
                    if (unit instanceof WorkerUnit workerUnit && resName != ResourceName.NONE) {
                        workerUnit.getGatherResourceGoal().setTargetResourceName(resName);
                        workerUnit.getGatherResourceGoal().setMoveTarget(preselectedBlockPos);
                    }
                    else
                        unit.setMoveTarget(preselectedBlockPos);
                }
                case ATTACK_MOVE -> {
                    // if the unit can't actually attack just treat this as a move action
                    if (unit instanceof AttackerUnit attackerUnit)
                        attackerUnit.setAttackMoveTarget(preselectedBlockPos);
                    else
                        unit.setMoveTarget(preselectedBlockPos);
                }
                case ATTACK -> {
                    // if the unit can't actually attack just treat this as a follow action
                    if (unit instanceof AttackerUnit attackerUnit)
                        attackerUnit.setAttackTarget((LivingEntity) level.getEntity(unitId));
                    else
                        unit.setFollowTarget((LivingEntity) level.getEntity(unitId));
                }
                case ATTACK_BUILDING -> {
                    // if the unit can't actually attack just treat this as a move action
                    if (unit instanceof AttackerUnit attackerUnit && attackerUnit.canAttackBuildings())
                        attackerUnit.setAttackBuildingTarget(preselectedBlockPos);
                    else
                        unit.setMoveTarget(preselectedBlockPos);
                }
                case FOLLOW -> {
                    unit.setFollowTarget((LivingEntity) level.getEntity(unitId));
                }
                case EXPLODE -> {
                    if (unit instanceof CreeperUnit creeper)
                        creeper.explode();
                }
                case BUILD_REPAIR -> {
                    // if the unit can't actually build/repair just treat this as a move action
                    if (unit instanceof WorkerUnit workerUnit) {
                        Building building = BuildingUtils.findBuilding(BuildingServerEvents.getBuildings(), preselectedBlockPos);
                        workerUnit.getBuildRepairGoal().setBuildingTarget(building);
                    }
                    else
                        unit.setMoveTarget(preselectedBlockPos);
                }
                case FARM -> {
                    if (unit instanceof WorkerUnit workerUnit) {
                        GatherResourcesGoal goal = workerUnit.getGatherResourceGoal();
                        if (goal != null) {
                            goal.setTargetResourceName(ResourceName.FOOD);
                            goal.setMoveTarget(preselectedBlockPos);
                        }
                    }
                }
            }
        }
    }
}