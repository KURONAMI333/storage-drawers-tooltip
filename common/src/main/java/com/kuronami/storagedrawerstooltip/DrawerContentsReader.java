package com.kuronami.storagedrawerstooltip;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;

/**
 * Storage Drawers の drawer ブロックを壊して拾ったアイテムに付く
 * {@link DataComponents#BLOCK_ENTITY_DATA} の生 NBT から、中身のアイテムと個数を読み取る。
 *
 * <p>Storage Drawers 側のクラス（IDrawer / IDrawerGroup / BlockEntityDataShim 等）には
 * 一切依存しない。vanilla API（{@link CustomData} / {@link CompoundTag} / {@link ItemStack}）
 * だけで完結させることで、Storage Drawers 非導入環境でもクラス解決が起きないようにしている。</p>
 *
 * <p>読み取る NBT の形は {@code _research/dl-analysis-2026-08/nbt_shape_1211.md} で
 * Storage Drawers 1.21.1 の実 API（{@code branch 1.21}, commit {@code 62a33ab}）に対して
 * 照合済み。</p>
 */
public final class DrawerContentsReader {

    private static final String KEY_DRAWERS = "Drawers";
    private static final String KEY_ITEM = "Item";
    private static final String KEY_ITEMS = "Items";
    private static final String KEY_COUNT = "Count";
    private static final String KEY_MISSING = "Missing";
    private static final String KEY_SLOT = "Slot";
    private static final String KEY_CONV = "Conv";

    private DrawerContentsReader() {
    }

    /**
     * @param stack      drawer ブロックが壊されて落ちたアイテム本体（drawer アイテムとは限らない。
     *                   {@code BLOCK_ENTITY_DATA} を持たない/{@code Drawers} を持たないアイテムは
     *                   即座に空リストで返る）
     * @param registries ItemStack を復元するための registry access。null を渡してよい
     *                   （取得できなければ空リストを返す）
     * @return tooltip に追加する行。空なら何も足さない
     */
    public static List<Component> readContentLines(ItemStack stack, HolderLookup.Provider registries) {
        List<Component> lines = new ArrayList<>();
        if (stack == null || stack.isEmpty() || registries == null) {
            return lines;
        }

        CustomData customData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (customData == null) {
            return lines;
        }

        CompoundTag root;
        try {
            root = customData.copyTag();
        } catch (RuntimeException e) {
            Constants.LOG.debug("BLOCK_ENTITY_DATA を CompoundTag として読めなかった", e);
            return lines;
        }

        if (!root.contains(KEY_DRAWERS)) {
            return lines;
        }

        try {
            Tag drawersTag = root.get(KEY_DRAWERS);
            if (drawersTag instanceof ListTag listTag) {
                readStandard(listTag, registries, lines);
            } else if (drawersTag instanceof CompoundTag compoundTag) {
                readFractional(compoundTag, registries, lines);
            }
            // それ以外の型（版差・破損）は何も足さずに無視する
        } catch (RuntimeException e) {
            // 想定外の NBT 形で tooltip 描画自体をクラッシュさせない。行が一部読めなくても
            // ここまでに集めた行はそのまま返す
            Constants.LOG.debug("Drawer contents の一部が読めなかった", e);
        }

        return lines;
    }

    /** 通常 drawer: トップレベル "Drawers" が ListTag。要素順 = slot 番号。 */
    private static void readStandard(ListTag drawersList, HolderLookup.Provider registries, List<Component> lines) {
        for (int i = 0; i < drawersList.size(); i++) {
            CompoundTag slotTag = drawersList.getCompound(i);
            if (slotTag.getBoolean(KEY_MISSING)) {
                continue;
            }
            if (!slotTag.contains(KEY_ITEM)) {
                continue;
            }

            ItemStack itemProto = ItemStack.parseOptional(registries, slotTag.getCompound(KEY_ITEM));
            if (itemProto.isEmpty()) {
                continue;
            }

            int count = slotTag.getInt(KEY_COUNT);
            if (count <= 0) {
                continue;
            }

            lines.add(formatStandardLine(itemProto, count));
        }
    }

    /**
     * fractional drawer: トップレベル "Drawers" が CompoundTag。"Drawers.Count" が pool 全体量、
     * "Drawers.Items" がリスト。要素は Item キーでラップされず、ItemStack 自身の save 結果に
     * "Slot"(byte) と "Conv"(int) が直接同居する。表示数 = pooledCount / conv。
     */
    private static void readFractional(CompoundTag drawersTag, HolderLookup.Provider registries, List<Component> lines) {
        if (!drawersTag.contains(KEY_ITEMS)) {
            return;
        }

        int pooledCount = drawersTag.getInt(KEY_COUNT);
        ListTag itemsList = drawersTag.getList(KEY_ITEMS, Tag.TAG_COMPOUND);

        for (int i = 0; i < itemsList.size(); i++) {
            CompoundTag slotTag = itemsList.getCompound(i);
            int slot = slotTag.getByte(KEY_SLOT) & 0xFF;
            int conv = slotTag.getByte(KEY_CONV) & 0xFF;
            if (conv <= 0) {
                continue;
            }

            // item 自身のフィールドがこの compound に直接同居する（"Item" キーで包まれていない）
            ItemStack itemProto = ItemStack.parseOptional(registries, slotTag);
            if (itemProto.isEmpty()) {
                continue;
            }

            int displayCount = pooledCount / conv;
            if (displayCount <= 0) {
                continue;
            }

            // 本体 DrawerOverlay.addContent() と同じく、物理 slot 0 だけ "+" を付けない
            // （compacting drawer で slot 0 が基準単位、slot>0 がその余剰分を表す）
            lines.add(formatFractionalLine(itemProto, displayCount, slot == 0));
        }
    }

    private static Component formatStandardLine(ItemStack itemProto, int count) {
        int stackSize = itemProto.getItem().getDefaultMaxStackSize();
        String suffix;
        if (stackSize <= 0) {
            suffix = " [" + count + "]";
        } else {
            int stacks = count / stackSize;
            int remainder = count - stacks * stackSize;
            if (stacks > 0 && remainder > 0) {
                suffix = " [" + stacks + "x" + stackSize + " + " + remainder + "]";
            } else if (stacks > 0) {
                suffix = " [" + stacks + "x" + stackSize + "]";
            } else {
                suffix = " [" + remainder + "]";
            }
        }
        return itemProto.getHoverName().copy().append(Component.literal(suffix));
    }

    private static Component formatFractionalLine(ItemStack itemProto, int displayCount, boolean isPrimarySlot) {
        String suffix = isPrimarySlot ? " [" + displayCount + "]" : " [+" + displayCount + "]";
        return itemProto.getHoverName().copy().append(Component.literal(suffix));
    }
}
