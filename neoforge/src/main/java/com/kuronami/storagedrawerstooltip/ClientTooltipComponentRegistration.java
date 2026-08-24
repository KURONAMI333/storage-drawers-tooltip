package com.kuronami.storagedrawerstooltip;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;

/**
 * {@link DrawerContentsTooltipData}（データ）から {@link DrawerContentsClientTooltip}（描画）への
 * 変換を NeoForge に登録する。登録が無いと {@code ClientTooltipComponent.create} が
 * {@code IllegalArgumentException("Unknown TooltipComponent")} を投げる。
 *
 * <p>{@link RegisterClientTooltipComponentFactoriesEvent} は MOD バス側のイベントなので、
 * ゲームバスに載る {@link ClientTooltipHandler} とはクラスを分けている。</p>
 */
@EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientTooltipComponentRegistration {

    private ClientTooltipComponentRegistration() {
    }

    @SubscribeEvent
    public static void onRegisterFactories(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(DrawerContentsTooltipData.class, DrawerContentsClientTooltip::new);
    }
}
