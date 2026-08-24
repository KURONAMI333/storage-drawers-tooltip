package com.kuronami.storagedrawerstooltip.mixin;

import com.kuronami.storagedrawerstooltip.DrawerContentsReader;
import com.kuronami.storagedrawerstooltip.DrawerContentsTooltipData;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;

/**
 * Fabric 側で drawer の中身を「アイコン付きの行」として tooltip に出すための唯一のフック。
 *
 * <p>Fabric API には NeoForge の {@code RenderTooltipEvent.GatherComponents} に相当する
 * 「tooltip の要素リストそのものを触る」口が無い。あるのは
 * {@code ItemTooltipCallback}（テキスト行しか足せない）と
 * {@code TooltipComponentCallback}（データ→描画の変換だけ）の2つで、データを tooltip に
 * 載せる経路は vanilla の {@code ItemStack#getTooltipImage()} しかない。そこで
 * ShulkerBoxTooltip（{@code 1.21.1} / {@code aecaa53}）と同じく、そこへ 1 箇所だけ inject する。</p>
 *
 * <p>差し込み位置は選べない。{@code GuiGraphics#renderTooltip} が
 * {@code list.add(list.isEmpty() ? 0 : 1, ...)} と固定しているので、必ずアイテム名の直後に入る。</p>
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @Inject(method = "getTooltipImage()Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
    private void storage_drawers_tooltip$drawerContents(CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        List<DrawerContentsReader.ContentRow> rows =
                DrawerContentsReader.readContentRows((ItemStack) (Object) this, minecraft.level.registryAccess());
        if (rows.isEmpty()) {
            // drawer 以外のアイテムには一切干渉しない（cancel しないので vanilla の束等はそのまま）
            return;
        }

        cir.setReturnValue(Optional.of(new DrawerContentsTooltipData(rows)));
    }
}
