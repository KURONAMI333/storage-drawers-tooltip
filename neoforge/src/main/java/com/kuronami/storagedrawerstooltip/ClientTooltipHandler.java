package com.kuronami.storagedrawerstooltip;

import com.mojang.datafixers.util.Either;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

import java.util.List;

/**
 * NeoForge 側の tooltip フック。中身の読み取りロジック自体は common の
 * {@link DrawerContentsReader} に置き、ここは NeoForge のイベントへの配線だけを行う。
 *
 * <p>アイテムのスプライトを描くには行が {@code ClientTooltipComponent} である必要があり、
 * テキスト行しか触れない {@code ItemTooltipEvent} では足りない。
 * {@link RenderTooltipEvent.GatherComponents} は tooltip の要素を
 * {@code Either<FormattedText, TooltipComponent>} のリストとして渡してくるので、ここに
 * {@link DrawerContentsTooltipData} を差し込む。</p>
 *
 * <p>差し込み位置はアイテム名の直後（index 1）。Fabric 側は vanilla の
 * {@code GuiGraphics#renderTooltip} が {@code list.add(list.isEmpty() ? 0 : 1, ...)} と
 * 固定しており位置を選べないため、両ローダーで同じ見え方になるよう NeoForge も揃えている。</p>
 */
@EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT)
public final class ClientTooltipHandler {

    private ClientTooltipHandler() {
    }

    @SubscribeEvent
    public static void onGatherComponents(RenderTooltipEvent.GatherComponents event) {
        Minecraft mc = Minecraft.getInstance();
        HolderLookup.Provider registries = mc.level != null ? mc.level.registryAccess() : null;
        if (registries == null) {
            return;
        }

        List<DrawerContentsReader.ContentRow> rows =
                DrawerContentsReader.readContentRows(event.getItemStack(), registries);
        if (rows.isEmpty()) {
            return;
        }

        List<Either<FormattedText, TooltipComponent>> elements = event.getTooltipElements();
        elements.add(elements.isEmpty() ? 0 : 1, Either.right(new DrawerContentsTooltipData(rows)));
    }
}
