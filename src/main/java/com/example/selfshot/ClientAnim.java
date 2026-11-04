package com.example.selfshot;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/**
 * Первое лицо: на время анимации мод прячет рендер оружия TACZ и рисует
 * свою руку и простой пистолет из кубиков (TACZ рисует оружие своим способом,
 * поэтому подкрутить его PoseStack не получается).
 */
@Mod.EventBusSubscriber(modid = SelfShotMod.MODID, value = Dist.CLIENT)
public class ClientAnim {
    public static final KeyMapping KEY =
            new KeyMapping("key.selfshot.start", GLFW.GLFW_KEY_V, "key.categories.gameplay");

    private static final int DURATION = ServerLogic.DURATION;
    private static int ticks = -1; // -1 = анимация не идёт
    private static final ResourceLocation WHITE = new ResourceLocation("minecraft", "textures/misc/white.png");

    // ===== НАСТРОЙКИ ПОЗИЦИИ (в блоках, система координат камеры: x вправо, y вверх, -z вперёд) =====
    private static final float[] START_POS = {0.32f, -0.30f, -0.62f};
    private static final float[] END_POS   = {0.00f, -0.29f, -0.32f}; // дуло у подбородка
    private static final float TILT_UP = 35f;   // на сколько градусов дуло задрано вверх (к подбородку)
    private static final float HEAD_UP = 7f;    // на сколько градусов игрок поднимает голову

    public static void start() {
        ticks = 0;
        var p = Minecraft.getInstance().player;
        if (p != null) p.displayClientMessage(Component.literal("[selfshot] анимация запущена"), true);
    }

    @Mod.EventBusSubscriber(modid = SelfShotMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBus {
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent e) { e.register(KEY); }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { ticks = -1; return; }
        if (KEY.consumeClick() && ticks < 0 && mc.screen == null
                && GunUtil.isPistol(mc.player.getMainHandItem())) {
            Net.CH.sendToServer(new Net.StartMsg());
        }
        if (ticks >= 0) {
            ticks++;
            if (ticks > DURATION + 20 || !mc.player.isAlive()) ticks = -1;
        }
    }

    // HIGHEST + receiveCanceled: встаём перед TACZ и сами отменяем его рендер руки
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onRenderHand(RenderHandEvent e) {
        if (ticks < 0) return;
        e.setCanceled(true);                       // прячем оружие TACZ
        if (e.getHand() != InteractionHand.MAIN_HAND) return;

        float t = Mth.clamp((ticks + e.getPartialTick()) / (float) DURATION, 0f, 1f);
        float r = Mth.clamp(t / 0.75f, 0f, 1f);    // подъём занимает первые 75%
        float k = r * r * (3 - 2 * r);

        PoseStack ps = e.getPoseStack();
        int light = e.getPackedLight();
        VertexConsumer vc = e.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(WHITE));

        ps.pushPose();
        ps.translate(Mth.lerp(k, START_POS[0], END_POS[0]),
                     Mth.lerp(k, START_POS[1], END_POS[1]),
                     Mth.lerp(k, START_POS[2], END_POS[2]));
        // дуло: от себя (старт) -> в лицо (конец)
        ps.mulPose(Axis.YP.rotationDegrees(180f * (1f - k)));
        ps.mulPose(Axis.ZP.rotationDegrees(10f * (1f - k)));
        ps.mulPose(Axis.XP.rotationDegrees(-TILT_UP * k));   // дуло смотрит вверх, на подбородок
        if (t > 0.75f) ps.translate(0f, 0f, 0.03f * Mth.clamp((t - 0.75f) / 0.25f, 0f, 1f)); // упирается в подбородок
        // дрожь перед выстрелом
        if (t > 0.75f) {
            float s = (t - 0.75f) * 0.03f;
            ps.translate(Mth.sin(ticks * 2.3f) * s, Mth.cos(ticks * 3.1f) * s, 0);
        }

        // ---- пистолет (дуло смотрит в +Z локально) ----
        box(vc, ps, -0.025f, -0.02f, -0.16f, 0.025f, 0.04f, 0.12f, 58, 61, 66, light);   // затвор
        box(vc, ps, -0.025f, 0.04f, -0.16f, 0.025f, 0.046f, 0.12f, 90, 94, 102, light);  // блик сверху
        box(vc, ps, -0.012f, 0.0f, 0.12f, 0.012f, 0.03f, 0.152f, 10, 10, 10, light);     // дуло
        box(vc, ps, -0.005f, 0.046f, 0.08f, 0.005f, 0.06f, 0.092f, 20, 20, 20, light);   // мушка
        box(vc, ps, -0.022f, -0.15f, -0.17f, 0.022f, -0.02f, -0.09f, 28, 28, 28, light); // рукоять
        // ---- кисть и предплечье ----
        box(vc, ps, -0.033f, -0.15f, -0.18f, 0.033f, -0.035f, -0.075f, 198, 149, 119, light); // кулак
        box(vc, ps, 0.026f, -0.06f, -0.15f, 0.05f, 0.005f, -0.07f, 198, 149, 119, light);     // большой палец
        ps.pushPose();
        ps.translate(0f, -0.09f, -0.14f);
        ps.mulPose(Axis.XP.rotationDegrees(-10f));
        box(vc, ps, -0.036f, -0.036f, -0.20f, 0.036f, 0.036f, 0.0f, 176, 128, 100, light);   // запястье
        box(vc, ps, -0.04f, -0.04f, -0.95f, 0.04f, 0.04f, -0.20f, 42, 159, 176, light);      // рукав
        ps.popPose();
        // вспышка выстрела на последних тиках
        if (ticks >= DURATION - 1) {
            box(vc, ps, -0.07f, -0.05f, 0.152f, 0.07f, 0.09f, 0.18f, 255, 225, 120, LightTexture.FULL_BRIGHT);
        }
        ps.popPose();
    }

    @SubscribeEvent
    public static void onCamera(net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles e) {
        if (ticks < 0) return;
        float t = Mth.clamp((ticks + (float) e.getPartialTick()) / (float) DURATION, 0f, 1f);
        float r = Mth.clamp(t / 0.75f, 0f, 1f);
        float k = r * r * (3 - 2 * r);
        float pitch = e.getPitch() - HEAD_UP * k;
        if (ticks >= DURATION - 1) pitch -= 5f; // рывок на выстреле
        e.setPitch(pitch);
    }

    private static void box(VertexConsumer vc, PoseStack ps, float x0, float y0, float z0,
                            float x1, float y1, float z1, int r, int g, int b, int light) {
        PoseStack.Pose p = ps.last();
        Matrix4f m = p.pose();
        Matrix3f n = p.normal();
        face(vc, m, n, light, r, g, b, 1.0f, 0, 1, 0,  x0,y1,z0, x0,y1,z1, x1,y1,z1, x1,y1,z0);
        face(vc, m, n, light, r, g, b, 0.5f, 0, -1, 0, x0,y0,z0, x1,y0,z0, x1,y0,z1, x0,y0,z1);
        face(vc, m, n, light, r, g, b, 0.8f, 0, 0, 1,  x0,y0,z1, x1,y0,z1, x1,y1,z1, x0,y1,z1);
        face(vc, m, n, light, r, g, b, 0.8f, 0, 0, -1, x0,y0,z0, x0,y1,z0, x1,y1,z0, x1,y0,z0);
        face(vc, m, n, light, r, g, b, 0.7f, 1, 0, 0,  x1,y0,z0, x1,y1,z0, x1,y1,z1, x1,y0,z1);
        face(vc, m, n, light, r, g, b, 0.7f, -1, 0, 0, x0,y0,z0, x0,y0,z1, x0,y1,z1, x0,y1,z0);
    }

    private static void face(VertexConsumer vc, Matrix4f m, Matrix3f n, int light, int r, int g, int b,
                             float shade, float nx, float ny, float nz, float... v) {
        int rr = (int) (r * shade), gg = (int) (g * shade), bb = (int) (b * shade);
        for (int i = 0; i < 4; i++) {
            vc.vertex(m, v[i * 3], v[i * 3 + 1], v[i * 3 + 2])
              .color(rr, gg, bb, 255)
              .uv(0f, 0f)
              .overlayCoords(OverlayTexture.NO_OVERLAY)
              .uv2(light)
              .normal(n, nx, ny, nz)
              .endVertex();
        }
    }
}
