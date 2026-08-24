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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Storage Drawers の drawer ブロックを壊して拾ったアイテムに付く
 * {@link DataComponents#BLOCK_ENTITY_DATA} の生 NBT から、中身のアイテムと個数を読み取る。
 *
 * <p>Storage Drawers 側のクラス（IDrawer / IDrawerGroup / BlockEntityDataShim 等）には
 * 一切依存しない。vanilla API（{@link CustomData} / {@link CompoundTag} / {@link ItemStack}）
 * だけで完結させることで、Storage Drawers 非導入環境でもクラス解決が起きないようにしている。</p>
 *
 * <p>読み取る NBT の形は Storage Drawers 1.21.1 相当（branch {@code 1.21}, commit
 * {@code 62a33ab}、{@code _research/refs_sd_emc/StorageDrawers_1211/}）の save 側コードに対して
 * 直接照合済み。fractional drawer の要素構造については
 * {@code _research/dl-analysis-2026-08/nbt_shape_1211.md} の記述（"Item キーでラップされて
 * いない"）が同じ commit の実物と食い違っていたため、このファイルは nbt_shape_1211.md では
 * なく実ソース（{@code FractionalDrawerGroup.java:549-568} の
 * {@code slotTag.put("Item", itemTag)}）を正としている。詳細は GAP_LOG 参照。</p>
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
     * 中身1件分。{@code icon} は tooltip の左端に描くスプライトの元になる ItemStack、
     * {@code label} はその右に並べる文字列（アイテム名 + 個数）。
     *
     * @param icon  描画用の ItemStack。個数はここでは意味を持たない（表示は label 側）
     * @param label {@link #readContentLines} が返すのと同一の Component
     */
    public record ContentRow(ItemStack icon, Component label) {
    }

    /**
     * @param stack      drawer ブロックが壊されて落ちたアイテム本体（drawer アイテムとは限らない。
     *                   {@code BLOCK_ENTITY_DATA} を持たない/{@code Drawers} を持たないアイテムは
     *                   即座に空リストで返る）
     * @param registries ItemStack を復元するための registry access。null を渡してよい
     *                   （取得できなければ空リストを返す）
     * @return tooltip に追加する行（テキストのみ）。空なら何も足さない
     */
    public static List<Component> readContentLines(ItemStack stack, HolderLookup.Provider registries) {
        List<ContentRow> rows = readContentRows(stack, registries);
        List<Component> lines = new ArrayList<>(rows.size());
        for (ContentRow row : rows) {
            lines.add(row.label());
        }
        return lines;
    }

    /**
     * {@link #readContentLines} と同じ走査を行い、行ごとにアイコン用の ItemStack も返す。
     * 行の内容・順序・除外条件は {@link #readContentLines} と同一。
     *
     * @param stack      readContentLines と同じ
     * @param registries readContentLines と同じ
     * @return tooltip に追加する行。空なら何も足さない
     */
    public static List<ContentRow> readContentRows(ItemStack stack, HolderLookup.Provider registries) {
        List<ContentRow> rows = new ArrayList<>();
        if (stack == null || stack.isEmpty() || registries == null) {
            return rows;
        }

        CustomData customData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (customData == null) {
            return rows;
        }

        CompoundTag root;
        try {
            root = customData.copyTag();
        } catch (RuntimeException e) {
            Constants.LOG.debug("BLOCK_ENTITY_DATA を CompoundTag として読めなかった", e);
            return rows;
        }

        if (!root.contains(KEY_DRAWERS)) {
            return rows;
        }

        try {
            Tag drawersTag = root.get(KEY_DRAWERS);
            if (drawersTag instanceof ListTag listTag) {
                readStandard(listTag, registries, rows);
            } else if (drawersTag instanceof CompoundTag compoundTag) {
                readFractional(compoundTag, registries, rows);
            }
            // それ以外の型（版差・破損）は何も足さずに無視する
        } catch (RuntimeException e) {
            // 想定外の NBT 形で tooltip 描画自体をクラッシュさせない。行が一部読めなくても
            // ここまでに集めた行はそのまま返す
            Constants.LOG.debug("Drawer contents の一部が読めなかった", e);
        }

        return rows;
    }

    /** 通常 drawer: トップレベル "Drawers" が ListTag。要素順 = slot 番号。 */
    private static void readStandard(ListTag drawersList, HolderLookup.Provider registries, List<ContentRow> rows) {
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

            rows.add(new ContentRow(itemProto, formatStandardLine(itemProto, count)));
        }
    }

    /**
     * fractional drawer: トップレベル "Drawers" が CompoundTag。"Drawers.Count" が pool 全体量、
     * "Drawers.Items" がリスト。各要素は通常 drawer と同じく item 本体が "Item" キーの下に入り、
     * 兄弟キーとして "Slot"(byte) と "Conv"(int) を持つ。Conv は本体が putInt で書き getByte で読み戻すため上流に切り捨ての穴があるが、こちらは書かれた通り getInt で読む（
     * {@code FractionalDrawerGroup.java:549-568} の {@code serializeNBT}）。
     *
     * <p>表示する数量は {@code FractionalStorage#getStoredItemRemainder(slot)}
     * （同ファイル:393-401）と同じ式で、単純な {@code pooledCount / conv}（＝
     * {@code getStoredItemCount()} 相当）ではない。slot 0 は基準単位の総数
     * （{@code pooledCount / convRate[0]}）、slot>0 は「ひとつ上の tier に繰り上がらない
     * 余り」（{@code (pooledCount / convRate[slot]) % (convRate[slot-1] / convRate[slot])}）。</p>
     */
    private static void readFractional(CompoundTag drawersTag, HolderLookup.Provider registries, List<ContentRow> rows) {
        if (!drawersTag.contains(KEY_ITEMS)) {
            return;
        }

        int pooledCount = drawersTag.getInt(KEY_COUNT);
        ListTag itemsList = drawersTag.getList(KEY_ITEMS, Tag.TAG_COMPOUND);

        // getStoredItemRemainder(slot) は convRate[slot-1] を参照する。serializeNBT は
        // 非空 slot だけを書く（protoStack[i].isEmpty() なら continue）ので、正規化された
        // 状態なら non-empty slot は 0 から連続している（FractionalStorage#normalizeGroup が
        // 前詰めする）はずだが、本体の deserializeNBT は読み込み直後に必ず normalizeGroup() を
        // 呼び直しており、そのコメントが "this fixes blocks that were saved broken" と明言して
        // いる。つまり本体自身、保存データに歯抜けが起こりうる前提でいる。読み取り専用のこちらは
        // normalizeGroup 相当の補修をしないため、先に slot -> conv の対応表を作っておく。
        Map<Integer, Integer> convBySlot = new HashMap<>();
        for (int i = 0; i < itemsList.size(); i++) {
            CompoundTag slotTag = itemsList.getCompound(i);
            int slot = slotTag.getByte(KEY_SLOT) & 0xFF;
            int conv = slotTag.getInt(KEY_CONV);
            if (conv > 0) {
                convBySlot.put(slot, conv);
            }
        }

        for (int i = 0; i < itemsList.size(); i++) {
            CompoundTag slotTag = itemsList.getCompound(i);
            int slot = slotTag.getByte(KEY_SLOT) & 0xFF;
            int conv = slotTag.getInt(KEY_CONV);
            if (conv <= 0) {
                continue;
            }
            if (!slotTag.contains(KEY_ITEM)) {
                continue;
            }

            ItemStack itemProto = ItemStack.parseOptional(registries, slotTag.getCompound(KEY_ITEM));
            if (itemProto.isEmpty()) {
                continue;
            }

            int remainder = fractionalRemainder(pooledCount, slot, conv, convBySlot);
            if (remainder <= 0) {
                continue;
            }

            // 本体 DrawerOverlay.addContent() と同じく、物理 slot 0 だけ "+" を付けない
            // （compacting drawer で slot 0 が基準単位、slot>0 がその余剰分を表す）
            rows.add(new ContentRow(itemProto, formatFractionalLine(itemProto, remainder, slot == 0)));
        }
    }

    /**
     * {@code FractionalStorage#getStoredItemRemainder(int)}
     * （{@code FractionalDrawerGroup.java:393-401}）と同じ式:
     * <pre>
     * if (convRate[slot] == 0) return 0;
     * if (slot == 0) return pooledCount / baseRate();          // baseRate() == convRate[0]
     * return (pooledCount / convRate[slot]) % (convRate[slot - 1] / convRate[slot]);
     * </pre>
     * 呼び出し側で {@code conv <= 0} は既に弾いているので、ここでの {@code conv} は常に正。
     *
     * <p>本体は slot-1 の convRate が常に存在する前提で書かれており、そこが 0 のままだと
     * {@code convRate[slot-1] / convRate[slot]} が 0 になり直後の {@code %} でゼロ除算する
     * （本体自身は歯抜けを想定して読み込み直後に normalizeGroup() で補修するため、通常この
     * パスには来ない）。こちらは補修をしないため、slot-1 の Conv が NBT に無い/0 の場合は
     * 繰り上げ計算をせず素の {@code pooledCount / conv} を返し、クラッシュを避ける。</p>
     */
    private static int fractionalRemainder(int pooledCount, int slot, int conv, Map<Integer, Integer> convBySlot) {
        if (slot == 0) {
            return pooledCount / conv;
        }

        Integer prevConv = convBySlot.get(slot - 1);
        if (prevConv == null || prevConv <= 0) {
            return pooledCount / conv;
        }

        int divisor = prevConv / conv;
        if (divisor <= 0) {
            return pooledCount / conv;
        }

        return (pooledCount / conv) % divisor;
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

    private static Component formatFractionalLine(ItemStack itemProto, int remainder, boolean isPrimarySlot) {
        String suffix = isPrimarySlot ? " [" + remainder + "]" : " [+" + remainder + "]";
        return itemProto.getHoverName().copy().append(Component.literal(suffix));
    }
}
