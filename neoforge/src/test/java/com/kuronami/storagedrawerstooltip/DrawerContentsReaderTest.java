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
 * <p>NBT サンプルは実機採取ではなく、Storage Drawers の save 側コード
 * （{@code StandardDrawerGroup.Slot#serializeNBT} / {@code FractionalDrawerGroup#serializeNBT}、
 * {@code _research/dl-analysis-2026-08/nbt_shape_1211.md} で 1.21.1 に照合済み）と同じ形を
 * 実際の vanilla {@code ItemStack#save} を使って組み立てている。</p>
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
    void fractionalDrawer_primarySlotHasNoPlus_secondarySlotHasPlus() {
        // FractionalDrawerGroup#serializeNBT と同じ形: トップレベル Drawers が compound、
        // Items 各要素は Item キーで包まれず item 自身の save 結果に Slot/Conv が同居する
        ListTag itemsList = new ListTag();

        CompoundTag slot0 = itemProtoTag(Items.REDSTONE_BLOCK);
        slot0.putByte("Slot", (byte) 0);
        slot0.putInt("Conv", 9);
        itemsList.add(slot0);

        CompoundTag slot1 = itemProtoTag(Items.REDSTONE);
        slot1.putByte("Slot", (byte) 1);
        slot1.putInt("Conv", 1);
        itemsList.add(slot1);

        CompoundTag drawersCompound = new CompoundTag();
        drawersCompound.put("Items", itemsList);
        drawersCompound.putInt("Count", 100); // pooledCount

        CompoundTag root = new CompoundTag();
        root.put("Drawers", drawersCompound);

        ItemStack stack = drawerItemStack(root);
        List<Component> lines = DrawerContentsReader.readContentLines(stack, registries);

        assertEquals(2, lines.size());
        assertTrue(lines.get(0).getString().contains("[11]"), lines.get(0).getString()); // 100/9=11、slot0は+無し
        assertTrue(lines.get(1).getString().contains("[+100]"), lines.get(1).getString()); // 100/1=100、slot1は+付き
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
