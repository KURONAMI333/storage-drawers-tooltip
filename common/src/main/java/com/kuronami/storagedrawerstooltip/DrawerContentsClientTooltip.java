package com.kuronami.storagedrawerstooltip;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * drawer の中身1行を「16x16 のアイテムスプライト + 文字列」で描く tooltip 部品。
 *
 * <p>骨格は vanilla の {@code ClientActivePlayersTooltip}（顔アイコン + プレイヤー名）と同じで、
 * アイコンと文字列の両方を {@link #renderImage} の中で描く。tooltip の枠の大きさは
 * {@link #getWidth} / {@link #getHeight} の戻り値だけで決まるので、この2つと実際に描く座標は
 * 同じ定数から導く。</p>
 *
 * <p>寸法: アイコン 16px 角、行の高さ 16px、アイコンと文字列の間隔 2px。
 * 文字列は行の中で縦中央に置く（フォントの字面 8px を 16px の中に収めるので上から 4px）。
 * テキストのみの行（vanilla の {@code ClientTextTooltip}）は 10px なので、中身が N 行あると
 * tooltip は N×6px だけ縦に伸びる。</p>
 */
public final class DrawerContentsClientTooltip implements ClientTooltipComponent {

    /** アイテムスプライトの一辺。{@code GuiGraphics#renderItem} は 16x16 で描く。 */
    private static final int ICON_SIZE = 16;
    /** 1行の高さ。アイコンと同じにして行間を詰める（テキストのみの行は vanilla では 10px）。 */
    private static final int ROW_HEIGHT = 16;
    /** アイコンと文字列の間隔。 */
    private static final int GAP = 2;
    /** 文字列の字面（8px）を行の中央に置くための上オフセット。 */
    private static final int TEXT_Y_OFFSET = (ROW_HEIGHT - 8) / 2;

    private final List<DrawerContentsReader.ContentRow> rows;

    public DrawerContentsClientTooltip(DrawerContentsTooltipData data) {
        this.rows = data.rows();
    }

    @Override
    public int getHeight() {
        return this.rows.size() * ROW_HEIGHT;
    }

    @Override
    public int getWidth(Font font) {
        int widest = 0;
        for (DrawerContentsReader.ContentRow row : this.rows) {
            int width = font.width(row.label());
            if (width > widest) {
                widest = width;
            }
        }
        return ICON_SIZE + GAP + widest;
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics guiGraphics) {
        for (int i = 0; i < this.rows.size(); i++) {
            DrawerContentsReader.ContentRow row = this.rows.get(i);
            int rowY = y + i * ROW_HEIGHT;

            ItemStack icon = row.icon();
            if (icon != null && !icon.isEmpty()) {
                try {
                    guiGraphics.renderItem(icon, x, rowY);
                } catch (RuntimeException e) {
                    // スプライトが解決できないアイテム（別 MOD 由来で model が無い等）でも
                    // tooltip 全体を落とさない。アイコンだけ諦めて文字列は出す
                    Constants.LOG.debug("中身のアイコンを描けなかった", e);
                }
            }

            // 文字列の x はアイコンの有無に関わらず固定（行ごとに左端がずれない）
            guiGraphics.drawString(font, row.label(), x + ICON_SIZE + GAP, rowY + TEXT_Y_OFFSET, -1);
        }
    }
}
