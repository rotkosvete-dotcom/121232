package com.example.selfshot;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = SelfShotMod.MODID, value = Dist.CLIENT)
public class ClientAnim {
    public static final KeyMapping KEY =
            new KeyMapping("key.selfshot.start", GLFW.GLFW_KEY_V, "key.categories.gameplay");

    private static final int DURATION = ServerLogic.DURATION;
    private static int ticks = -1; // -1 = анимация не идёт

    public static void start() {
        ticks = 0;
        var p = Minecraft.getInstance().player;
        if (p != null) p.displayClientMessage(net.minecraft.network.chat.Component.literal("[selfshot] анимация запущена"), true);
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
            // сбросить, если анимация закончилась или оружие убрали
            if (ticks > DURATION + 20 || !(GunUtil.isPistol(mc.player.getMainHandItem()))) ticks = -1;
        }
    }

    // HIGHEST + receiveCanceled: TACZ сам перехватывает отрисовку руки, поэтому встаём перед ним
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST, receiveCanceled = true)
    public static void onRenderHand(RenderHandEvent e) {
        if (ticks < 0 || e.getHand() != InteractionHand.MAIN_HAND) return;

        float t = Mth.clamp((ticks + e.getPartialTick()) / (float) DURATION, 0f, 1f);
        float k = t * t * (3 - 2 * t); // плавное приближение (smoothstep)

        PoseStack ps = e.getPoseStack();

        // ===== ЗДЕСЬ ПОДКРУЧИВАТЬ ПОД СВОЙ ВКУС =====
        // 1) оружие едет к центру экрана, вверх и ближе к лицу
        ps.translate(-0.40f * k, 0.35f * k, 0.45f * k);
        // 2) разворачиваем ствол дулом к себе
        ps.mulPose(Axis.YP.rotationDegrees(-165f * k));
        // 3) лёгкий наклон
        ps.mulPose(Axis.ZP.rotationDegrees(12f * k));
        // 4) дрожь перед выстрелом
        if (t > 0.7f) {
            float s = (t - 0.7f) * 0.02f;
            ps.translate(Mth.sin(ticks * 2.3f) * s, Mth.cos(ticks * 3.1f) * s, 0);
        }
    }
}
