package com.kuronami.storagedrawerstooltip;

import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * NeoForge 側の tooltip フック。中身の読み取りロジック自体は common の
 * {@link DrawerContentsReader} に置き、ここは NeoForge のイベントへの配線だけを行う。
 */
@EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT)
public final class ClientTooltipHandler {

    private ClientTooltipHandler() {
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        HolderLookup.Provider registries = event.getContext().registries();
        if (registries == null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                registries = mc.level.registryAccess();
            }
        }
        if (registries == null) {
            return;
        }

        event.getToolTip().addAll(DrawerContentsReader.readContentLines(event.getItemStack(), registries));
    }
}
