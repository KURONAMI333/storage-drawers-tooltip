package com.kuronami.storagedrawerstooltip;

import net.minecraft.world.inventory.tooltip.TooltipComponent;

import java.util.List;

/**
 * tooltip に差し込むデータ側の担体。{@link DrawerContentsReader#readContentRows} が返した行を
 * そのまま持つだけで、描画は client 側の {@link DrawerContentsClientTooltip} が行う。
 *
 * <p>vanilla の tooltip パイプラインは「データ（{@link TooltipComponent}）」と
 * 「描画（{@code ClientTooltipComponent}）」を分けており、両ローダーとも
 * データ→描画の変換を登録する口だけを提供している（NeoForge は
 * {@code RegisterClientTooltipComponentFactoriesEvent}、Fabric は
 * {@code TooltipComponentCallback}）。そのため行の運搬用にこの型が要る。</p>
 *
 * @param rows 表示する行。空の場合はそもそもこの型を作らない
 */
public record DrawerContentsTooltipData(List<DrawerContentsReader.ContentRow> rows) implements TooltipComponent {
}
