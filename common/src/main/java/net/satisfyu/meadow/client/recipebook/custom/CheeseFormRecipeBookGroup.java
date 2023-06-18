package net.satisfyu.meadow.client.recipebook.custom;

import com.google.common.collect.ImmutableList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.Recipe;
import net.satisfyu.meadow.client.recipebook.IRecipeBookGroup;
import net.satisfyu.meadow.recipes.cheese.CheeseFormRecipe;
import net.satisfyu.meadow.registry.ObjectRegistry;

import java.util.List;

@Environment(EnvType.CLIENT)
public enum CheeseFormRecipeBookGroup implements IRecipeBookGroup {
    SEARCH(new ItemStack(Items.COMPASS)),
    CHEESE(new ItemStack(ObjectRegistry.PIECE_OF_CHEESE.get())),
    MISC(new ItemStack(ObjectRegistry.ALPINE_SALT.get()));

    public static final List<IRecipeBookGroup> CHEESE_GROUPS = ImmutableList.of(SEARCH, CHEESE, MISC);

    private final List<ItemStack> icons;

    CheeseFormRecipeBookGroup(ItemStack... entries) {
        this.icons = ImmutableList.copyOf(entries);
    }

    public boolean fitRecipe(Recipe<?> recipe) {
        if (recipe instanceof CheeseFormRecipe cheeseFormRecipe) {
            switch (this) {
                case SEARCH -> {
                    return true;
                }
                case CHEESE -> {
                    if (true) { //TODO
                        return true;
                    }
                }
                case MISC -> {
                    if (true) { //TODO
                        return false;
                    }
                }
                default -> {
                    return false;
                }
            }
        }
        return false;
    }

    @Override
    public List<ItemStack> getIcons() {
        return this.icons;
    }

}