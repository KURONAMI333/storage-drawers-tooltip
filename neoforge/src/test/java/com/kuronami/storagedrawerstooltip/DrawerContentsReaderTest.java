package com.kuronami.storagedrawerstooltip;

import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DrawerContentsReader} のゲート2/ゲート3 機械検証。
 *
 * <p>NBT サンプルは実機採取ではなく、Storage Drawers の save 側コード（
 * {@code StandardDrawerGroup.Slot#serializeNBT} / {@code FractionalDrawerGroup.FractionalStorage#serializeNBT}、
 * {@code _research/refs_sd_emc/StorageDrawers_1211/} の branch {@code 1.21} commit {@code 62a33ab}
 * で直接確認済み）と同じ形を、実際の vanilla {@code ItemStack#save} を使って組み立てている。</p>
 */
class DrawerContentsReaderTest {

    private static HolderLookup.Provider registries;

    @BeforeAll
    static void bootstrapGame() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    private static CompoundTag itemProtoTag(Item item) {
        ItemStack proto = new ItemStack(item, 1);
        return (CompoundTag) proto.save(registries, new CompoundTag());
    }

    /** {@code StandardDrawerGroup.Slot#serializeNBT} と同じ形の1要素を作る。 */
    private static CompoundTag standardSlot(Item item, int count, boolean missing) {
        CompoundTag slot = new CompoundTag();
        slot.putBoolean("Missing", missing);
        if (!missing) {
            slot.put("Item", itemProtoTag(item));
            slot.putInt("Count", count);
        }
        return slot;
    }

    /**
     * {@code FractionalDrawerGroup.FractionalStorage#serializeNBT}（:549-568）と同じ形の1要素を作る。
     * item 本体は "Item" キーの下に入り、兄弟キーとして "Slot"/"Conv" を持つ
     * （{@code slotTag.put("Item", itemTag)}。"Item" キーで包まれないという記述は
     * {@code nbt_shape_1211.md} の誤りで、同じ commit の実ソースと食い違っていた。GAP_LOG 参照）。
     */
    private static CompoundTag fractionalSlot(Item item, int slot, int conv) {
        CompoundTag slotTag = new CompoundTag();
        slotTag.putByte("Slot", (byte) slot);
        slotTag.putInt("Conv", conv);
        slotTag.put("Item", itemProtoTag(item));
        return slotTag;
    }

    private static CompoundTag fractionalDrawers(int pooledCount, CompoundTag... slots) {
        ListTag itemsList = new ListTag();
        for (CompoundTag s : slots) {
            itemsList.add(s);
        }
        CompoundTag drawersCompound = new CompoundTag();
        drawersCompound.put("Items", itemsList);
        drawersCompound.putInt("Count", pooledCount);
        return drawersCompound;
    }

    private static CompoundTag wrapAsDrawers(CompoundTag drawersValue) {
        CompoundTag root = new CompoundTag();
        root.put("Drawers", drawersValue);
        return root;
    }

    private static ItemStack drawerItemStack(CompoundTag blockEntityTag) {
        // BLOCK_ENTITY_DATA しか見ないので item 自体は何でもよい（drawer アイテム本体を再現する必要は無い）
        ItemStack stack = new ItemStack(Items.STICK);
        stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(blockEntityTag));
        return stack;
    }

    // ---- ゲート2: Phase 2 の4項目 ----

    @Test
    void singleStandardDrawer_ironIngot_showsStackNotation() {
        CompoundTag drawers = new CompoundTag();
        ListTag list = new ListTag();
        list.add(standardSlot(Items.IRON_INGOT, 200, false)); // 200 = 3x64 + 8
        drawers.put("Drawers", list);

        ItemStack stack = drawerItemStack(drawers);
        List<Component> lines = DrawerContentsReader.readContentLines(stack, registries);

        assertEquals(1, lines.size());
        String text = lines.get(0).getString();
        assertTrue(text.contains("3x64 + 8"), "unexpected line: " + text);
    }

    @Test
    void fourSlotDrawer_fourDifferentItems_showsFourLines() {
        CompoundTag drawers = new CompoundTag();
        ListTag list = new ListTag();
        list.add(standardSlot(Items.IRON_INGOT, 64, false));
        list.add(standardSlot(Items.COAL, 10, false));
        list.add(standardSlot(Items.OAK_LOG, 5, false));
        list.add(standardSlot(Items.COBBLESTONE, 640, false));
        drawers.put("Drawers", list);

        ItemStack stack = drawerItemStack(drawers);
        List<Component> lines = DrawerContentsReader.readContentLines(stack, registries);

        assertEquals(4, lines.size());
    }

    @Test
    void fractionalDrawer_twoSlot_slot1IsModuloRemainderNotRawCount() {
        // pooledCount=100, slot0 conv=9, slot1 conv=1
        // slot0 = pooledCount/convRate[0] = 100/9 = 11（基準単位の総数、+無し）
        // slot1 = (100/1) % (9/1) = 100 % 9 = 1（繰り上げ後の余り、+付き）
        // 旧実装は slot1 を単純な pooledCount/conv = 100 と誤って出していた（二重カウント）
        CompoundTag drawers = fractionalDrawers(100,
                fractionalSlot(Items.REDSTONE_BLOCK, 0, 9),
                fractionalSlot(Items.REDSTONE, 1, 1));

        ItemStack stack = drawerItemStack(wrapAsDrawers(drawers));
        List<Component> lines = DrawerContentsReader.readContentLines(stack, registries);

        assertEquals(2, lines.size());
        assertTrue(lines.get(0).getString().contains("[11]"), lines.get(0).getString());
        assertTrue(lines.get(1).getString().contains("[+1]"), lines.get(1).getString());
    }

    @Test
    void fractionalDrawer_threeTierCompacting_ironBlockIngotNugget() {
        // 統括からの実例（鉄1000個相当、塊換算）。
        // block(conv81)=1000/81=12(+無し) / ingot(conv9)=(1000/9)%(81/9)=111%9=3(+3) /
        // nugget(conv1)=(1000/1)%(9/1)=1000%9=1(+1)
        CompoundTag drawers = fractionalDrawers(1000,
                fractionalSlot(Items.IRON_BLOCK, 0, 81),
                fractionalSlot(Items.IRON_INGOT, 1, 9),
                fractionalSlot(Items.IRON_NUGGET, 2, 1));

        ItemStack stack = drawerItemStack(wrapAsDrawers(drawers));
        List<Component> lines = DrawerContentsReader.readContentLines(stack, registries);

        assertEquals(3, lines.size());
        assertTrue(lines.get(0).getString().contains("[12]"), lines.get(0).getString());
        assertTrue(lines.get(1).getString().contains("[+3]"), lines.get(1).getString());
        assertTrue(lines.get(2).getString().contains("[+1]"), lines.get(2).getString());
    }

    @Test
    void fractionalDrawer_realWorldSample_wholeMultiple_onlyPrimaryLineShown() {
        // 実際の Storage Drawers が生成した block entity データから採取した値
        // （3段圧縮ドロワー・pooled 4941）。この drawer 自体は壊されておらず world に placed のままなので
        // ItemStack の BLOCK_ENTITY_DATA component としては未確認だが、"Drawers" compound の
        // 構造自体は本体の serializeNBT がそのまま item へコピーする形と同一。
        // pooledCount=4941 は conv=81 の丁度61倍（4941=61*81）で、ingot(conv9)/nugget(conv1)の
        // remainder が両方ちょうど0になる実例。手組みの既存テスト（1000, 100）はどれも全 slot で
        // remainder>0 になるよう選ばれており、「上位 tier の remainder がちょうど0で行ごと消える」
        // ケースは未カバーだった。
        CompoundTag drawers = fractionalDrawers(4941,
                fractionalSlot(Items.IRON_BLOCK, 0, 81),
                fractionalSlot(Items.IRON_INGOT, 1, 9),
                fractionalSlot(Items.IRON_NUGGET, 2, 1));

        ItemStack stack = drawerItemStack(wrapAsDrawers(drawers));
        List<Component> lines = DrawerContentsReader.readContentLines(stack, registries);

        // slot0 = 4941/81 = 61（+無し）/ slot1 = (4941/9)%(81/9) = 549%9 = 0（行なし）/
        // slot2 = (4941/1)%(9/1) = 4941%9 = 0（行なし）
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).getString().contains("[61]"), lines.get(0).getString());
    }

    @Test
    void fractionalDrawer_missingPreviousSlot_fallsBackWithoutCrash() {
        // 歯抜けデータ（slot 0 が Items に無いのに slot 1 だけがある壊れた保存を想定）。
        // 本体の getStoredItemRemainder は convRate[slot-1]==0 のままだとゼロ除算で落ちるが、
        // こちらは繰り上げ計算をせず pooledCount/conv にフォールバックしてクラッシュを避ける
        CompoundTag drawers = fractionalDrawers(100,
                fractionalSlot(Items.REDSTONE, 1, 1)); // slot 0 が欠落

        ItemStack stack = drawerItemStack(wrapAsDrawers(drawers));
        List<Component> lines = DrawerContentsReader.readContentLines(stack, registries);

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).getString().contains("[+100]"), lines.get(0).getString());
    }

    @Test
    void emptyDrawer_noBlockEntityData_noLines() {
        // 空の drawer は BLOCK_ENTITY_DATA 自体が付かない（Storage Drawers 側の仕様）
        ItemStack stack = new ItemStack(Items.STICK);
        List<Component> lines = DrawerContentsReader.readContentLines(stack, registries);
        assertTrue(lines.isEmpty());
    }

    // ---- ゲート3: 異常系 ----

    @Test
    void blockEntityDataWithoutDrawersKey_noLinesNoCrash() {
        // dropMode=DROP/VOID や、drawer 以外の block entity データが乗った場合の想定
        CompoundTag root = new CompoundTag();
        root.putString("SomeOtherKey", "value");
        ItemStack stack = drawerItemStack(root);
        List<Component> lines = DrawerContentsReader.readContentLines(stack, registries);
        assertTrue(lines.isEmpty());
    }

    @Test
    void drawersKeyWrongType_noLinesNoCrash() {
        // 版差・破損で "Drawers" が ListTag でも CompoundTag でもない場合
        CompoundTag root = new CompoundTag();
        root.putString("Drawers", "not-a-list-or-compound");
        ItemStack stack = drawerItemStack(root);
        List<Component> lines = DrawerContentsReader.readContentLines(stack, registries);
        assertTrue(lines.isEmpty());
    }

    @Test
    void missingSlotIsSkipped() {
        CompoundTag drawers = new CompoundTag();
        ListTag list = new ListTag();
        list.add(standardSlot(Items.IRON_INGOT, 64, false));
        list.add(standardSlot(Items.IRON_INGOT, 0, true)); // Missing=true の空 slot
        drawers.put("Drawers", list);

        ItemStack stack = drawerItemStack(drawers);
        List<Component> lines = DrawerContentsReader.readContentLines(stack, registries);
        assertEquals(1, lines.size());
    }

    @Test
    void unknownItemIdIsSkippedButOtherRowsStillShown() {
        CompoundTag drawers = new CompoundTag();
        ListTag list = new ListTag();

        CompoundTag badSlot = new CompoundTag();
        badSlot.putBoolean("Missing", false);
        CompoundTag badItem = new CompoundTag();
        badItem.putString("id", "some_uninstalled_mod:does_not_exist");
        badItem.putInt("count", 1);
        badSlot.put("Item", badItem);
        badSlot.putInt("Count", 5);
        list.add(badSlot);

        list.add(standardSlot(Items.IRON_INGOT, 64, false));
        drawers.put("Drawers", list);

        ItemStack stack = drawerItemStack(drawers);
        List<Component> lines = DrawerContentsReader.readContentLines(stack, registries);

        assertEquals(1, lines.size()); // 不明アイテムの行はスキップされ、鉄インゴットの行だけ残る
    }

    @Test
    void unknownFractionalItemIdIsSkippedButOtherRowsStillShown() {
        // fractional 側も Item キーの下を読むようになったので、通常 drawer と同様に
        // 未知 item id をスキップできることを確認する
        // slot0(conv=9)を未知item、slot1(conv=1)を既知itemにする。conv値は実際の
        // compacting drawer同様に厳密減少させる（等しいconvだとremainderが必ず0になり
        // 「行が出ない」の原因が未知item判定なのかremainder計算なのか切り分けられないため）
        CompoundTag badSlot = new CompoundTag();
        badSlot.putByte("Slot", (byte) 0);
        badSlot.putInt("Conv", 9);
        CompoundTag badItem = new CompoundTag();
        badItem.putString("id", "some_uninstalled_mod:does_not_exist");
        badItem.putInt("count", 1);
        badSlot.put("Item", badItem);

        CompoundTag goodSlot = fractionalSlot(Items.REDSTONE, 1, 1);

        CompoundTag drawers = fractionalDrawers(50, badSlot, goodSlot);

        ItemStack stack = drawerItemStack(wrapAsDrawers(drawers));
        List<Component> lines = DrawerContentsReader.readContentLines(stack, registries);

        // slot1 = (50/1) % (9/1) = 50 % 9 = 5
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).getString().contains("[+5]"), lines.get(0).getString());
    }

    @Test
    void nullRegistries_returnsEmptyNoCrash() {
        ItemStack stack = new ItemStack(Items.STICK);
        List<Component> lines = DrawerContentsReader.readContentLines(stack, null);
        assertTrue(lines.isEmpty());
    }

    @Test
    void nonDrawerItem_noBlockEntityData_noLines() {
        // Storage Drawers 未導入環境でも、他 MOD の BLOCK_ENTITY_DATA 付きアイテムを誤検出しない
        ItemStack stack = new ItemStack(Items.CHEST);
        List<Component> lines = DrawerContentsReader.readContentLines(stack, registries);
        assertTrue(lines.isEmpty());
    }
}
