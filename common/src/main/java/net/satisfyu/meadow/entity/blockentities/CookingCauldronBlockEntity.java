package net.satisfyu.meadow.entity.blockentities;


import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.Ingredient;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.satisfyu.meadow.block.CookingCauldronBlock;
import net.satisfyu.meadow.client.screen.handler.CookingCauldronGuiHandler;
import net.satisfyu.meadow.recipes.cooking.CookingCauldronRecipe;
import net.satisfyu.meadow.registry.BlockEntityRegistry;
import net.satisfyu.meadow.registry.RecipeRegistry;
import net.satisfyu.meadow.registry.TagRegistry;
import org.jetbrains.annotations.Nullable;

public class CookingCauldronBlockEntity extends BlockEntity implements Inventory, NamedScreenHandlerFactory {

    private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(MAX_CAPACITY, ItemStack.EMPTY);
    private static final int MAX_CAPACITY = 7;
    public static final int MAX_COOKING_TIME = 600; // Time in ticks (30s)
    private int cookingTime;
    public static final int OUTPUT_SLOT = 0;
    private static final int INGREDIENTS_AREA = 6;

    private boolean isBeingBurned;

    private final PropertyDelegate delegate;

    public CookingCauldronBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistry.COOKING_CAULDRON.get(), pos, state);
        this.delegate = new PropertyDelegate() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> cookingTime;
                    case 1 -> isBeingBurned ? 1 : 0;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                switch (index) {
                    case 0 -> cookingTime = value;
                    case 1 -> isBeingBurned = value != 0;
                }
            }

            @Override
            public int size() {
                return 2;
            }
        };
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        Inventories.readNbt(nbt, this.inventory);
        this.cookingTime = nbt.getInt("CookingTime");
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        Inventories.writeNbt(nbt, this.inventory);
        nbt.putInt("CookingTime", this.cookingTime);
    }

    public boolean isBeingBurned() {
        if (getWorld() == null)
            throw new NullPointerException("Null world invoked");
        if(this.getCachedState().get(CookingCauldronBlock.HANGING)) return true;
        final BlockState belowState = this.getWorld().getBlockState(getPos().down());
        final var optionalList = Registry.BLOCK.getEntryList(TagRegistry.ALLOWS_COOKING);
        final var entryList = optionalList.orElse(null);
        if (entryList == null) {
            return false;
        } else return entryList.contains(belowState.getBlock().getRegistryEntry());
    }

    private boolean canCraft(CookingCauldronRecipe recipe) {
        if (recipe == null || recipe.getOutput().isEmpty()) {
            return false;
        } else if (this.getStack(OUTPUT_SLOT).isEmpty()) {
            return true;
        } else {
            final ItemStack recipeOutput = recipe.getOutput();
            final ItemStack outputSlotStack = this.getStack(OUTPUT_SLOT);
            final int outputSlotCount = outputSlotStack.getCount();
            if (!outputSlotStack.isItemEqualIgnoreDamage(recipeOutput)) {
                return false;
            } else if (outputSlotCount < this.getMaxCountPerStack() && outputSlotCount < outputSlotStack.getMaxCount()) {
                return true;
            } else {
                return outputSlotCount < recipeOutput.getMaxCount();
            }
        }
    }

    private void craft(CookingCauldronRecipe recipe) {
        if (!canCraft(recipe)) {
            return;
        }
        final ItemStack recipeOutput = recipe.getOutput();
        final ItemStack outputSlotStack = this.getStack(OUTPUT_SLOT);
        if (outputSlotStack.isEmpty()) {
            setStack(OUTPUT_SLOT, recipeOutput.copy());
        } else if (outputSlotStack.isOf(recipeOutput.getItem())) {
            outputSlotStack.increment(recipeOutput.getCount());
        }
        final DefaultedList<Ingredient> ingredients = recipe.getIngredients();
        // each slot can only be used once because in canMake we only checked if decrement by 1 still retains the recipe
        // otherwise recipes can break when an ingredient is used multiple times
        boolean[] slotUsed = new boolean[INGREDIENTS_AREA];
        for (int i = 0; i < recipe.getIngredients().size(); i++) {
            Ingredient ingredient = ingredients.get(i);
            // Looks for the best slot to take it from
            final ItemStack bestSlot = this.getStack(i + 1);
            if (ingredient.test(bestSlot) && !slotUsed[i]) {
                slotUsed[i] = true;
                bestSlot.decrement(1);
            } else {
                // check all slots in search of the ingredient
                for (int j = 1; j <= INGREDIENTS_AREA; j++) {
                    ItemStack stack = this.getStack(j);
                    if (ingredient.test(stack) && !slotUsed[j]) {
                        slotUsed[j] = true;
                        stack.decrement(1);
                    }
                }
            }
        }
    }

    public void tick(World world, BlockPos pos, BlockState state, CookingCauldronBlockEntity blockEntity) {
        if (world.isClient()) {
            return;
        }
        this.isBeingBurned = isBeingBurned();
        if (!this.isBeingBurned) {
            if (state.get(CookingCauldronBlock.LIT))
                world.setBlockState(pos, state.with(CookingCauldronBlock.LIT, false), Block.NOTIFY_ALL);
            return;
        }
        CookingCauldronRecipe recipe = world.getRecipeManager().getFirstMatch(RecipeRegistry.COOKING.get(), this, world).orElse(null);

        boolean canCraft = canCraft(recipe);
        if (canCraft) {
            this.cookingTime++;
            if (this.cookingTime >= MAX_COOKING_TIME) {
                this.cookingTime = 0;
                craft(recipe);
            }
        } else if (!canCraft(recipe)) {
            this.cookingTime = 0;
        }
        if (canCraft) {
            world.setBlockState(pos, state.with(CookingCauldronBlock.COOKING, true).with(CookingCauldronBlock.LIT, true), Block.NOTIFY_ALL);
        } else if (state.get(CookingCauldronBlock.COOKING)) {
            world.setBlockState(pos, state.with(CookingCauldronBlock.COOKING, false).with(CookingCauldronBlock.LIT, true), Block.NOTIFY_ALL);
        } else if (state.get(CookingCauldronBlock.LIT) != isBeingBurned) {
            world.setBlockState(pos, state.with(CookingCauldronBlock.LIT, isBeingBurned), Block.NOTIFY_ALL);
        }
    }


    @Override
    public int size() {
        return inventory.size();
    }

    @Override
    public boolean isEmpty() {
        return inventory.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getStack(int slot) {
        return this.inventory.get(slot);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        return Inventories.splitStack(this.inventory, slot, amount);
    }

    @Override
    public ItemStack removeStack(int slot) {
        return Inventories.removeStack(this.inventory, slot);
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        this.inventory.set(slot, stack);
        if (stack.getCount() > this.getMaxCountPerStack()) {
            stack.setCount(this.getMaxCountPerStack());
        }
        this.markDirty();
    }


    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        if (this.world.getBlockEntity(this.pos) != this) {
            return false;
        } else {
            return player.squaredDistanceTo((double) this.pos.getX() + 0.5, (double) this.pos.getY() + 0.5, (double) this.pos.getZ() + 0.5) <= 64.0;
        }
    }

    @Override
    public void clear() {
        inventory.clear();
    }

    @Override
    public Text getDisplayName() {
        return Text.of("");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
        return new CookingCauldronGuiHandler(syncId, inv, this, this.delegate);
    }
}