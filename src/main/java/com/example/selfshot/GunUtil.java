package com.example.selfshot;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import net.minecraft.world.item.ItemStack;

public class GunUtil {
    /** true только для пистолетов TACZ (тип оружия "pistol") */
    public static boolean isPistol(ItemStack s) {
        if (!(s.getItem() instanceof IGun gun)) return false;
        return TimelessAPI.getCommonGunIndex(gun.getGunId(s))
                .map(i -> "pistol".equals(i.getType()))
                .orElse(false);
    }
}
