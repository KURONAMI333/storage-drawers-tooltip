package com.kuronami.storagedrawerstooltip;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;

/**
 * Fabric 側の tooltip フック。中身の読み取りロジック自体は common の
 * {@link DrawerContentsReader} に置き、ここは Fabric API のイベントへの配線だけを行う。
 */
public final class StorageDrawersTooltipClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            HolderLookup.Provider registries = context.registries();
            if (registries == null) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level != null) {
                    registries = mc.level.registryAccess();
                }
            }
            if (registries == null) {
                return;
            }

            lines.addAll(DrawerContentsReader.readContentLines(stack, registries));
        });
    }
}
