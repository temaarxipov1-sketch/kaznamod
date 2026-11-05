package ru.kazna;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Валюта государства: золотые слитки. */
public final class Gold {
    private Gold() {}

    private static final int MAIN_SLOTS = 36;

    public static int count(ServerPlayer p) {
        int total = 0;
        for (int i = 0; i < MAIN_SLOTS; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(Items.GOLD_INGOT)) total += s.getCount();
        }
        return total;
    }

    /** Забирает до amount слитков, возвращает сколько реально забрал. */
    public static int take(ServerPlayer p, int amount) {
        int left = amount;
        for (int i = 0; i < MAIN_SLOTS && left > 0; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(Items.GOLD_INGOT)) {
                int t = Math.min(left, s.getCount());
                s.shrink(t);
                left -= t;
            }
        }
        p.getInventory().setChanged();
        return amount - left;
    }

    public static void give(ServerPlayer p, int amount) {
        int left = amount;
        while (left > 0) {
            int n = Math.min(64, left);
            ItemStack stack = new ItemStack(Items.GOLD_INGOT, n);
            if (!p.getInventory().add(stack) && !stack.isEmpty()) {
                p.drop(stack, false);
            }
            left -= n;
        }
    }
}
