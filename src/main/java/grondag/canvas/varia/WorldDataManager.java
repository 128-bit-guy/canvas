package grondag.canvas.varia;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.world.World;

public class WorldDataManager {
    public static int LENGTH = 8;
    public static int WORLD_EFFECT_MODIFIER = 0;
    public static int NIGHT_VISION_ACTIVE = 1;
    public static int EFFECTIVE_INTENSITY = 2;
    public static int AMBIENT_INTENSITY = 3;
    public static int HAS_SKYLIGHT = 4;
    public static int DIMENSION_ID = 5;
    public static int MOON_SIZE = 6;
    
    static float[] UNIFORM_DATA = new float[LENGTH];
    
    public static float[] uniformData() {
        return UNIFORM_DATA;
    }
    
    public static void updateLight(float tick, float flicker) {
        final MinecraftClient client = MinecraftClient.getInstance();
        final World world = client.world;
        final GameRenderer gameRenderer = client.gameRenderer;
        
        if (world != null) {
            final boolean hasSkyLight = world.dimension.hasSkyLight();
            boolean nightVision = client.player.hasStatusEffect(StatusEffects.NIGHT_VISION);
            UNIFORM_DATA[DIMENSION_ID] = world.dimension.getType().getRawId();
            UNIFORM_DATA[HAS_SKYLIGHT] = hasSkyLight ? 1 : 0;
            UNIFORM_DATA[AMBIENT_INTENSITY] = world.getAmbientLight(1.0F);
            UNIFORM_DATA[EFFECTIVE_INTENSITY] = hasSkyLight && !nightVision ? UNIFORM_DATA[AMBIENT_INTENSITY] : 1;
            UNIFORM_DATA[MOON_SIZE] = world.getMoonSize();
            
            UNIFORM_DATA[NIGHT_VISION_ACTIVE] = nightVision ? 1 : 0;
            
            
            final float fluidModifier = client.player.method_3140();
            if (nightVision) {
                UNIFORM_DATA[WORLD_EFFECT_MODIFIER] = gameRenderer.getNightVisionStrength(client.player, tick);
            } else if (fluidModifier > 0.0F && client.player.hasStatusEffect(StatusEffects.CONDUIT_POWER)) {
                UNIFORM_DATA[WORLD_EFFECT_MODIFIER] = fluidModifier;
            } else {
                UNIFORM_DATA[WORLD_EFFECT_MODIFIER] = 0.0F;
            }
        }
    }
}
