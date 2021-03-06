package refinedstorage.apiimpl.storage.item;

import net.minecraft.item.ItemStack;
import refinedstorage.api.autocrafting.ICraftingPattern;
import refinedstorage.api.network.INetworkMaster;
import refinedstorage.api.storage.AccessType;
import refinedstorage.api.storage.item.IItemStorage;
import refinedstorage.api.storage.item.IItemStorageCache;
import refinedstorage.api.storage.item.IItemStorageProvider;
import refinedstorage.api.util.IItemStackList;
import refinedstorage.apiimpl.API;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class ItemStorageCache implements IItemStorageCache {
    private INetworkMaster network;
    private List<IItemStorage> storages = new ArrayList<>();
    private IItemStackList list = API.instance().createItemStackList();

    public ItemStorageCache(INetworkMaster network) {
        this.network = network;
    }

    @Override
    public synchronized void invalidate() {
        storages.clear();

        network.getNodeGraph().all().stream()
            .filter(node -> node.canUpdate() && node instanceof IItemStorageProvider)
            .forEach(node -> ((IItemStorageProvider) node).addItemStorages(storages));

        list.clear();

        for (IItemStorage storage : storages) {
            if (storage.getAccessType() == AccessType.WRITE) {
                continue;
            }

            for (ItemStack stack : storage.getItems()) {
                add(stack, true);
            }
        }

        for (ICraftingPattern pattern : network.getPatterns()) {
            for (ItemStack output : pattern.getOutputs()) {
                ItemStack patternStack = output.copy();
                patternStack.stackSize = 0;
                add(patternStack, true);
            }
        }

        network.sendItemStorageToClient();
    }

    @Override
    public synchronized void add(@Nonnull ItemStack stack, boolean rebuilding) {
        list.add(stack);

        if (!rebuilding) {
            network.sendItemStorageDeltaToClient(stack, stack.stackSize);
        }
    }

    @Override
    public synchronized void remove(@Nonnull ItemStack stack) {
        if (list.remove(stack, !network.hasPattern(stack))) {
            network.sendItemStorageDeltaToClient(stack, -stack.stackSize);
        }
    }

    @Override
    public IItemStackList getList() {
        return list;
    }

    @Override
    public List<IItemStorage> getStorages() {
        return storages;
    }
}
