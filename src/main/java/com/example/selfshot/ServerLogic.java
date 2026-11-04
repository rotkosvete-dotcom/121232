package com.example.selfshot;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = SelfShotMod.MODID)
public class ServerLogic {
    /** Сколько тиков длится анимация до "выстрела" (должно совпадать с ClientAnim.DURATION) */
    public static final int DURATION = 30;
    private static final Map<UUID, Integer> ACTIVE = new HashMap<>();

    public static void start(ServerPlayer p) {
        if (p == null || p.isSpectator()) return;
        if (!GunUtil.isPistol(p.getMainHandItem())) {
            p.displayClientMessage(net.minecraft.network.chat.Component.literal("Возьми пистолет TACZ в основную руку"), true);
            return;
        }
        if (ACTIVE.containsKey(p.getUUID())) return;
        ACTIVE.put(p.getUUID(), DURATION);
        Net.CH.send(PacketDistributor.PLAYER.with(() -> p), new Net.AnimMsg());
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        Iterator<Map.Entry<UUID, Integer>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
            // отмена, если игрок вышел, умер или убрал оружие
            if (p == null || !p.isAlive() || !GunUtil.isPistol(p.getMainHandItem())) {
                it.remove();
                continue;
            }
            int left = en.getValue() - 1;
            if (left <= 0) {
                p.hurt(p.damageSources().genericKill(), Float.MAX_VALUE);
                it.remove();
            } else {
                en.setValue(left);
            }
        }
    }
}
