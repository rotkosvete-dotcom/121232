package com.example.selfshot;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/** Работает с TACZ через reflection, поэтому для сборки jar TACZ не нужен. */
public class GunUtil {
    private static Class<?> IGUN;
    private static boolean init;

    private static void init() {
        if (init) return;
        init = true;
        try { IGUN = Class.forName("com.tacz.guns.api.item.IGun"); } catch (Throwable ignored) {}
    }

    public static boolean isGun(ItemStack s) {
        init();
        return IGUN != null && IGUN.isInstance(s.getItem());
    }

    /** Пистолет TACZ. Если тип определить не удалось - считаем любым оружием TACZ. */
    public static boolean isPistol(ItemStack s) {
        if (!isGun(s)) return false;
        try {
            Object id = IGUN.getMethod("getGunId", ItemStack.class).invoke(s.getItem(), s);
            Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
            Object opt = api.getMethod("getCommonGunIndex", ResourceLocation.class).invoke(null, id);
            Object index = ((Optional<?>) opt).orElse(null);
            if (index == null) return true;
            Object type = index.getClass().getMethod("getType").invoke(index);
            return "pistol".equals(type);
        } catch (Throwable t) {
            return true;
        }
    }
}
