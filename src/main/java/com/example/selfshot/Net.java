package com.example.selfshot;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class Net {
    private static final String V = "1";
    public static final SimpleChannel CH = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SelfShotMod.MODID, "main"), () -> V, V::equals, V::equals);

    /** клиент -> сервер: игрок нажал клавишу */
    public record StartMsg() {}
    /** сервер -> клиент: запустить анимацию */
    public record AnimMsg() {}

    public static void register() {
        CH.registerMessage(0, StartMsg.class, (m, b) -> {}, b -> new StartMsg(), (m, ctx) -> {
            var c = ctx.get();
            c.enqueueWork(() -> ServerLogic.start(c.getSender()));
            c.setPacketHandled(true);
        });
        CH.registerMessage(1, AnimMsg.class, (m, b) -> {}, b -> new AnimMsg(), (m, ctx) -> {
            var c = ctx.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientAnim::start));
            c.setPacketHandled(true);
        });
    }
}
