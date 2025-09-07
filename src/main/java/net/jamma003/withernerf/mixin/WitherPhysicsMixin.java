package net.jamma003.withernerf.mixin;

import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(WitherEntity.class)
public abstract class WitherPhysicsMixin {

    // --- per-instance fields ---
    @Unique
    private Vec3d currentHeading = new Vec3d(0, 0, 0); // horizontal heading

    // --- constants you can tweak ---
    @Unique
    private static final double ACCEL = 0.05;       // how fast it accelerates toward the target
    @Unique
    private static final double DRAG  = 0.75;       // this is actually movement speed
    @Unique
    private static final double MAX_TURN = 0.03;    // max change in heading per tick (~gradual steering)

    @Unique
    private double randomTurn = 0.0; // per-instance property

    @Redirect(
            method = "tickMovement",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/boss/WitherEntity;setVelocity(Lnet/minecraft/util/math/Vec3d;)V"
            )
    )
    private void redirectSetVelocity(WitherEntity instance, Vec3d desired) {
        if (instance.getHealth() < (instance.getMaxHealth()/2)) {
            instance.setVelocity(desired.x, desired.y, desired.z);
            return;
        }

        Vec3d currentVel = instance.getVelocity();

        // --- compute target horizontal direction ---
        Vec3d targetDir = new Vec3d(desired.x, 0, desired.z);
        if (targetDir.lengthSquared() > 0) {
            targetDir = targetDir.normalize();
        }

        // --- rotate currentHeading toward targetDir gradually ---
        currentHeading = rotateToward(currentHeading, targetDir, MAX_TURN);

        // --- apply acceleration in currentHeading ---
        double newX = currentVel.x * DRAG + currentHeading.x * ACCEL;
        double newZ = currentVel.z * DRAG + currentHeading.z * ACCEL;

        // --- vertical stays as AI computed ---
        double newY = desired.y;

        instance.setVelocity(newX, newY, newZ);
    }

    @Unique
    private void updateRandomTurn() {
        // Add random value between -1 and 1
        double delta = (instanceRandom() * 0.02) - 0.01;
        randomTurn += delta;

        // Clamp between -3 and 3
        if (randomTurn > 2.0) randomTurn = 2.0;
        if (randomTurn < -2.0) randomTurn = -2.0;
    }

    // Utility to access the Wither's random instance
    @Unique
    private double instanceRandom() {
        return ((WitherEntity)(Object)this).getRandom().nextDouble();
    }

    /**
     * Gradually rotates a 2D horizontal vector toward a target.
     * maxAngle is the maximum change in length 1 units per tick (~turn rate).
     */
    @Unique
    private Vec3d rotateToward(Vec3d current, Vec3d target, double maxAngle) {
        updateRandomTurn();

        if (current.lengthSquared() < 0.0001) {
            // if heading is zero, snap to target
            return target;
        }

        // normalize both
        Vec3d curNorm = current.normalize();
        Vec3d tgtNorm = target.normalize();

        // 2D cross product to get rotation direction (Y up)
        double cross = curNorm.x * tgtNorm.z - curNorm.z * tgtNorm.x;
        double dot   = curNorm.x * tgtNorm.x + curNorm.z * tgtNorm.z;

        // clamp rotation
        double angle = Math.atan2(cross, dot);
        if (angle > maxAngle) angle = maxAngle;
        if (angle < -maxAngle) angle = -maxAngle;
        angle += (randomTurn/100);

        double cos = Math.cos(angle);
        double sin = Math.sin(angle);

        double newX = curNorm.x * cos - curNorm.z * sin;
        double newZ = curNorm.x * sin + curNorm.z * cos;

        // preserve original magnitude for blending
        double mag = current.length();
        return new Vec3d(newX * mag, 0, newZ * mag);
    }
}
