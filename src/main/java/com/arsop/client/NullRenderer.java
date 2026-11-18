package com.arsop.client;

import com.arsop.ArsOp;
import com.arsop.entity.ArsOpEntities;
import com.arsop.entity.NullEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * A camera-facing disc: pure black inside, a white-hot yellow/orange ring at the edge, "[NULL]" in the middle.
 * The ring uses the emissive "eyes" render type (additive, full bright), which shader packs pick up as glowing.
 */
@EventBusSubscriber(modid = ArsOp.MODID, value = Dist.CLIENT)
public class NullRenderer extends EntityRenderer<NullEntity> {
    private static final ResourceLocation TEXTURE = ArsOp.prefix("textures/entity/null.png");
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int SEGMENTS = 64;
    private static final String LABEL = "[NULL]";

    public NullRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0;
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ArsOpEntities.NULL.get(), NullRenderer::new);
    }

    @Override
    public void render(NullEntity entity, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
        float r = entity.getRadius();
        float t = entity.tickCount + partialTicks;
        boolean max = entity.isChargeMax();
        float flicker = max
                ? 1.0f + 0.07f * Mth.sin(t * 0.9f)
                : 1.0f + 0.04f * Mth.sin(t * 0.45f) + 0.02f * Mth.sin(t * 1.7f);
        // 0..1 pulse for the max-size glow.
        float beat = max ? 0.5f + 0.5f * Mth.sin(t * 0.9f) : 0f;

        pose.pushPose();
        pose.mulPose(this.entityRenderDispatcher.cameraOrientation());
        PoseStack.Pose p = pose.last();

        // Black core.
        ring(buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), p, 0, r, 0, 0, 0, 0, 0, 0);

        // Glow, additive: black = invisible, so each band fades out by going to black.
        VertexConsumer glow = buffers.getBuffer(RenderType.eyes(TEXTURE));
        float edge = r * flicker;
        float band = Math.max(0.06f, r * 0.045f);
        if (max) band *= 1.6f; // thicker, white-hot line at full size
        int lineG = max ? 255 : 160, lineB = max ? (int) (150 + 100 * beat) : 50;
        int bloomR = max ? 255 : 210, bloomG = max ? (int) (170 + 60 * beat) : 100, bloomB = max ? (int) (60 + 120 * beat) : 15;
        float bloomOut = max ? 1.5f + 0.15f * beat : 1.35f;
        ring(glow, p, edge * 0.78f, edge - band, 0, 0, 0, 150, 60, 5);                        // inner haze
        ring(glow, p, edge - band, edge, 255, lineG, lineB, 255, 245, 210);                   // inner half of the line
        ring(glow, p, edge, edge + band, 255, 245, 210, 255, lineG, lineB);                   // outer half of the line
        ring(glow, p, edge + band, edge * bloomOut + 0.1f, bloomR, bloomG, bloomB, 0, 0, 0);   // outer bloom

        // [NULL] label.
        Font font = getFont();
        float textWidth = font.width(LABEL);
        float scale = Math.max(0.004f, r * 0.42f / textWidth);
        pose.scale(scale, -scale, scale);
        font.drawInBatch(LABEL, -textWidth / 2f, -font.lineHeight / 2f + 0.5f, (max ? 0xFFFFFFFF : 0xFFFFE08A), false,
                pose.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, FULL_BRIGHT);
        pose.popPose();
        super.render(entity, yaw, partialTicks, pose, buffers, light);
    }

    /** Flat annulus in the XY plane from radius {@code r0} to {@code r1}, colors interpolated inner -> outer. */
    private static void ring(VertexConsumer vc, PoseStack.Pose p, float r0, float r1,
                             int ir, int ig, int ib, int or, int og, int ob) {
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = Mth.TWO_PI * i / SEGMENTS, a1 = Mth.TWO_PI * (i + 1) / SEGMENTS;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            vertex(vc, p, c0 * r0, s0 * r0, ir, ig, ib);
            vertex(vc, p, c0 * r1, s0 * r1, or, og, ob);
            vertex(vc, p, c1 * r1, s1 * r1, or, og, ob);
            vertex(vc, p, c1 * r0, s1 * r0, ir, ig, ib);
        }
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, int red, int green, int blue) {
        vc.addVertex(p, x, y, 0)
                .setColor(red, green, blue, 255)
                .setUv(0.5f, 0.5f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(p, 0, 0, 1);
    }

    @Override
    public ResourceLocation getTextureLocation(NullEntity entity) {
        return TEXTURE;
    }
}
