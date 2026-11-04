package com.example.selfshot;

import net.minecraftforge.fml.common.Mod;

@Mod(SelfShotMod.MODID)
public class SelfShotMod {
    public static final String MODID = "selfshot";

    public SelfShotMod() {
        Net.register();
    }
}
