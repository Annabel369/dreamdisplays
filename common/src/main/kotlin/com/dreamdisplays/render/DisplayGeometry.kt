package com.dreamdisplays.render

import com.dreamdisplays.api.DisplayFacing
import com.mojang.blaze3d.vertex.PoseStack
import org.joml.Quaternionf

/**
 * Pose-stack math for positioning a display quad in the world: facing-dependent translation,
 * rotation, and scaling. Extracted from [ScreenRenderer] so the renderer only sequences draws.
 */
internal object DisplayGeometry {
    /** Distance the quad floats in front of the supporting blocks, to avoid z-fighting. */
    private const val SURFACE_OFFSET = 0.008f

    /**
     * Applies the full transform for a screen of [width] x [height] blocks facing [facing]:
     * surface offset, per-facing corner correction, rotation, and scale. The stack is expected to
     * already be translated to the screen's anchor block.
     */
    fun applyScreenTransform(stack: PoseStack, facing: DisplayFacing, width: Int, height: Int) {
        moveForward(stack, facing, SURFACE_OFFSET)

        when (facing) {
            DisplayFacing.NORTH -> {
                moveHorizontal(stack, DisplayFacing.NORTH, -width.toFloat())
                moveForward(stack, DisplayFacing.NORTH, 1f)
            }

            DisplayFacing.SOUTH -> {
                moveHorizontal(stack, DisplayFacing.SOUTH, 1f)
                moveForward(stack, DisplayFacing.SOUTH, 1f)
            }

            DisplayFacing.EAST -> {
                moveHorizontal(stack, DisplayFacing.EAST, -(width - 1).toFloat())
                moveForward(stack, DisplayFacing.EAST, 2f)
            }

            DisplayFacing.WEST -> {}
        }

        fixRotation(stack, facing)
        stack.scale(width.toFloat(), height.toFloat(), 0f)
    }

    /** Applies the quaternion rotation and position correction so the quad faces the correct direction for [facing]. */
    private fun fixRotation(stack: PoseStack, facing: DisplayFacing) {
        val rotation: Quaternionf = when (facing) {
            DisplayFacing.NORTH -> Quaternionf().rotationY(Math.toRadians(180.0).toFloat()).also {
                stack.translate(0f, 0f, 1f)
            }

            DisplayFacing.WEST -> Quaternionf().rotationY(Math.toRadians(-90.0).toFloat()).also {
                stack.translate(0f, 0f, 0f)
            }

            DisplayFacing.EAST -> Quaternionf().rotationY(Math.toRadians(90.0).toFloat()).also {
                stack.translate(-1f, 0f, 1f)
            }

            else -> Quaternionf().also { stack.translate(-1f, 0f, 0f) }
        }
        stack.mulPose(rotation)
    }

    /** Translates [stack] by [amount] blocks in the forward axis of [facing]. */
    private fun moveForward(stack: PoseStack, facing: DisplayFacing, amount: Float) {
        when (facing) {
            DisplayFacing.NORTH -> stack.translate(0f, 0f, -amount)
            DisplayFacing.WEST -> stack.translate(-amount, 0f, 0f)
            DisplayFacing.EAST -> stack.translate(amount, 0f, 0f)
            else -> stack.translate(0f, 0f, amount)
        }
    }

    /** Translates [stack] by [amount] blocks along the horizontal axis perpendicular to [facing]. */
    private fun moveHorizontal(stack: PoseStack, facing: DisplayFacing, amount: Float) {
        when (facing) {
            DisplayFacing.NORTH -> stack.translate(-amount, 0f, 0f)
            DisplayFacing.WEST -> stack.translate(0f, 0f, amount)
            DisplayFacing.EAST -> stack.translate(0f, 0f, -amount)
            else -> stack.translate(amount, 0f, 0f)
        }
    }
}
