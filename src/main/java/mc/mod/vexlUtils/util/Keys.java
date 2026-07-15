package mc.mod.vexlUtils.util;

import mc.mod.vexlUtils.VexlUtils;
import org.bukkit.NamespacedKey;

public final class Keys {

    public static NamespacedKey owner() {
        return new NamespacedKey(VexlUtils.get(), "owner");
    }

    public static NamespacedKey material() {
        return new NamespacedKey(VexlUtils.get(), "material");
    }

    public static NamespacedKey amount() {
        return new NamespacedKey(VexlUtils.get(), "amount");
    }

    public static NamespacedKey price() {
        return new NamespacedKey(VexlUtils.get(), "price");
    }

    public static NamespacedKey mode() {
        return new NamespacedKey(VexlUtils.get(), "mode");
    }

    public static NamespacedKey display() {
        return new NamespacedKey(VexlUtils.get(), "display");
    }

    public static NamespacedKey chest() {
        return new NamespacedKey(VexlUtils.get(), "chest");
    }

    private Keys() {
    }
}
