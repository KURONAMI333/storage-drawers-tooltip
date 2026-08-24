package com.kuronami.storagedrawerstooltip;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback;

/**
 * Fabric 側の tooltip 配線。行の読み取りは common の {@link DrawerContentsReader}、
 * 描画は common の {@link DrawerContentsClientTooltip} が持つ。
 *
 * <p>tooltip への差し込み自体は {@code com.kuronami.storagedrawerstooltip.mixin.ItemStackMixin}
 * が行う。ここで登録するのは、そこで載せた {@link DrawerContentsTooltipData}（データ）を
 * 描画用の {@code ClientTooltipComponent} に変換する口だけ。</p>
 */
public final class StorageDrawersTooltipClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        TooltipComponentCallback.EVENT.register(data -> {
            if (data instanceof DrawerContentsTooltipData drawerData) {
                return new DrawerContentsClientTooltip(drawerData);
            }
            return null;
        });
    }
}
