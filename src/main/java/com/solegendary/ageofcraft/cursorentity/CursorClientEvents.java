package com.solegendary.ageofcraft.cursorentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Vector3d;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraftforge.client.event.DrawSelectionEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.solegendary.ageofcraft.orthoview.OrthoViewClientEvents;
import org.lwjgl.glfw.GLFW;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static net.minecraft.util.Mth.*;
import java.util.List;

/**
 * Handler that implements and manages screen-to-world translations of the cursor and block/entity selection
 */
public class CursorClientEvents {

    // pos of block moused over
    private static BlockPos cursorBlockPos = new BlockPos(0,0,0);
    // pos of cursor exactly on: first non-air block, last frame, near to screen, far from screen
    private static Vector3d cursorPos = new Vector3d(0,0,0);
    private static Vector3d cursorPosLast = new Vector3d(0,0,0);
    private static Vector3d cursorPosNear = new Vector3d(0,0,0);
    private static Vector3d cursorPosFar = new Vector3d(0,0,0);
    private static Vector3d lookVector = new Vector3d(0,0,0);
    // entity moused over, vs entity selected by clicking
    private static Entity mousedEntity = null;
    private static Entity selectedEntity = null;

    private static final Minecraft MC = Minecraft.getInstance();
    private static int winWidth = MC.getWindow().getGuiScaledWidth();
    private static int winHeight = MC.getWindow().getGuiScaledHeight();

    public static BlockPos getCursorBlockPos() {
        return cursorBlockPos;
    }
    public static Vector3d getCursorPos() {
        return cursorPos;
    }
    public static Entity getMousedEntity() {
        return mousedEntity;
    }

    @SubscribeEvent
    public static void onDrawScreen(GuiScreenEvent.DrawScreenEvent evt) {
        if (!OrthoViewClientEvents.isEnabled()) return;

        float zoom = OrthoViewClientEvents.getZoom();
        int mouseX = evt.getMouseX();
        int mouseY = evt.getMouseY();

        if (MC.player == null) return;

        // ************************************
        // Calculate cursor position on screen
        // ************************************

        // at winHeight=240, zoom=10, screen is 20 blocks high, so PTB=240/20=24
        float pixelsToBlocks = winHeight / zoom;

        // make mouse coordinate origin centre of screen
        float x = (mouseX - (float) winWidth / 2) / pixelsToBlocks;
        float y = 0;
        float z = (mouseY - (float) winHeight / 2) / pixelsToBlocks;

        double camRotYRads = Math.toRadians(OrthoViewClientEvents.getCamRotY());
        z = z / (float) (Math.sin(-camRotYRads));

        // get look vector of the player (and therefore the camera)
        // calcs from https://stackoverflow.com/questions/65897792/3d-vector-coordinates-from-x-and-y-rotation
        float a = (float) Math.toRadians(MC.player.getYRot());
        float b = (float) Math.toRadians(MC.player.getXRot());
        final Vector3d lookVector = new Vector3d(-cos(b) * sin(a), -sin(b), cos(b) * cos(a));

        Vec2 XZRotated = OrthoViewClientEvents.rotateCoords(x, z);

        cursorPosLast = new Vector3d(
                cursorPos.x,
                cursorPos.y,
                cursorPos.z
        );
        // for some reason position is off by some y coord so just move it down manually
        cursorPos = new Vector3d(
                MC.player.xo - XZRotated.x,
                MC.player.yo + y + 1.5,
                MC.player.zo - XZRotated.y
        );

        // calc near and far cursorPos to get a cursor line vector
        Vector3d lookVectorNear = new Vector3d(0, 0, 0);
        lookVectorNear.set(lookVector);
        lookVectorNear.scale(-10);
        cursorPosNear.set(cursorPos);
        cursorPosNear.add(lookVectorNear);
        Vector3d lookVectorFar = new Vector3d(0, 0, 0);
        lookVectorFar.set(lookVector);
        lookVectorFar.scale(10);
        cursorPosFar.set(cursorPos);
        cursorPosFar.add(lookVectorFar);

        // ****************************************************
        // Find the first non-air block along the cursorPos ray
        // ****************************************************

        // only spend time doing calcs for cursorEntity if we actually moved the cursor
        if (cursorPos.x != cursorPosLast.x || cursorPos.y != cursorPosLast.y || cursorPos.z != cursorPosLast.z) {

            // if we add a multiple of the lookVector, we can 'raytrace' forward from the camera without
            // changing the on-screen position of the cursorEntity
            double vectorScale = -50;
            Vector3d lookVectorScaled;
            BlockPos bp = null;
            BlockState bs = null;
            BlockState lastbs = null;

            while (true) {
                lastbs = bs;
                Vector3d searchVec = new Vector3d(0, 0, 0);
                searchVec.set(cursorPos);

                lookVectorScaled = new Vector3d(0, 0, 0);
                lookVectorScaled.set(lookVector);
                lookVectorScaled.scale(vectorScale); // has to be high enough to be at the 'front' of the screen
                searchVec.add(lookVectorScaled);

                bp = new BlockPos(searchVec.x, searchVec.y, searchVec.z);
                bs = MC.level.getBlockState(bp);

                if (bs.getMaterial().isLiquid()) {
                    break;
                }
                // found the target block; only pick solid blocking blocks
                if (bs.getMaterial().isSolidBlocking()) {
                    cursorBlockPos = bp;
                    break;
                }
                vectorScale += 1;
            }
            cursorPos.add(lookVectorScaled);
            CursorCommonEvents.moveCursorEntity(cursorPos);
        }

        // ****************************************
        // Find entity moused over and/or selected
        // ****************************************
        AABB aabb = new AABB(
                cursorPos.x - 5,
                cursorPos.y - 5,
                cursorPos.z - 5,
                cursorPos.x + 5,
                cursorPos.y + 5,
                cursorPos.z + 5
        );
        List<Villager> villagers = MC.level.getEntitiesOfClass(Villager.class, aabb);

        mousedEntity = null;

        for (Villager villager : villagers) {
            AABB entityaabb = villager.getBoundingBox();
            entityaabb = entityaabb.setMaxY(entityaabb.maxY);
            entityaabb = entityaabb.setMinY(entityaabb.minY);
            entityaabb = entityaabb.inflate(0.1); // inflate by set amount to improve click accuracy

            if (rayIntersectsAABBCustom(cursorPosNear, lookVector, entityaabb)) {
                mousedEntity = villager;
            }
        }

        // ****************************************
        // Highlight the block moused over
        // ****************************************

        //drawSelectionBox(evt.getMatrixStack(), cursorBlockPos);
    }


    @SubscribeEvent
    public static void onMouseClick(GuiScreenEvent.MouseClickedEvent evt) {
        if (!OrthoViewClientEvents.isEnabled()) return;

        // select a moused over entity by left clicking it
        if (evt.getButton() == GLFW.GLFW_MOUSE_BUTTON_1) {
            if (mousedEntity != null)
                selectedEntity = mousedEntity;
            else
                selectedEntity = null;
        }
    }

    private static boolean rayIntersectsAABBCustom(Vector3d origin, Vector3d rayVector, AABB aabb) {
        // r.dir is unit direction vector of ray
        Vector3d dirfrac = new Vector3d(
                1.0f / rayVector.x,
                1.0f / rayVector.y,
                1.0f / rayVector.z
        );
        // lb is the corner of AABB with minimal coordinates - left bottom, rt is maximal corner
        // r.org is origin of ray
        float t1 = (float) ((aabb.minX - origin.x) * dirfrac.x);
        float t2 = (float) ((aabb.maxX - origin.x) * dirfrac.x);
        float t3 = (float) ((aabb.minY - origin.y) * dirfrac.y);
        float t4 = (float) ((aabb.maxY - origin.y) * dirfrac.y);
        float t5 = (float) ((aabb.minZ - origin.z) * dirfrac.z);
        float t6 = (float) ((aabb.maxZ - origin.z) * dirfrac.z);

        float tmin = max(max(min(t1, t2), min(t3, t4)), min(t5, t6));
        float tmax = min(min(max(t1, t2), max(t3, t4)), max(t5, t6));

        // if tmax < 0, ray (line) is intersecting AABB, but the whole AABB is behind us
        if (tmax < 0) return false;
        // if tmin > tmax, ray doesn't intersect AABB
        if (tmin > tmax) return false;

        return true;
    }

    // prevent moused over blocks being outlined in the usual way (ie. by raytracing from player to block)
    @SubscribeEvent
    public static void onHighlightBlockEvent(DrawSelectionEvent.HighlightBlock evt) {
        if (MC.level != null && OrthoViewClientEvents.isEnabled())
            evt.setCanceled(true);
    }
    @SubscribeEvent
    public static void onRenderWorldLastEvent(RenderWorldLastEvent evt) {
        if (MC.level != null && OrthoViewClientEvents.isEnabled()) {
            if (selectedEntity != null)
                drawEntityOutline(evt.getMatrixStack(), selectedEntity, 1.0f);
            if (mousedEntity != null)
                drawEntityOutline(evt.getMatrixStack(), mousedEntity, 0.5f);
            else
                drawBlockOutline(evt.getMatrixStack(), cursorBlockPos, 0.25f);
        }
    }

    public static void drawBlockOutline(PoseStack matrixStack, BlockPos blockpos, float alpha) {
        BlockState blockstate = MC.level.getBlockState(blockpos);

        if (blockstate.getMaterial().isSolidBlocking())
            drawOutline(matrixStack, new AABB(blockpos), alpha);
    }

    public static void drawEntityOutline(PoseStack matrixStack, Entity entity, float alpha) {
        drawOutline(matrixStack, entity.getBoundingBox(), alpha);
    }

    // TODO: lines are showing through blocks for some reason; look at the vanilla render blocks code to fix?
    public static void drawOutline(PoseStack matrixStack, AABB aabb, float alpha) {
        Entity camEntity = MC.getCameraEntity();
        double d0 = camEntity.getX();
        double d1 = camEntity.getY() + camEntity.getEyeHeight();
        double d2 = camEntity.getZ();

        VertexConsumer vertexConsumer = MC.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        matrixStack.pushPose();
        matrixStack.translate(-d0, -d1, -d2);
        LevelRenderer.renderLineBox(matrixStack, vertexConsumer, aabb, 1.0f, 1.0f, 1.0f, alpha);
        matrixStack.popPose();
    }
}


