package com.blakebr0.mysticalagriculture.tileentity;

import com.blakebr0.cucumber.inventory.BaseItemStackHandler;
import com.blakebr0.cucumber.tileentity.BaseInventoryTileEntity;
import com.blakebr0.cucumber.util.MultiblockPositions;
import com.blakebr0.mysticalagriculture.api.crafting.RecipeTypes;
import com.blakebr0.mysticalagriculture.crafting.recipe.InfusionRecipe;
import com.blakebr0.mysticalagriculture.init.ModTileEntities;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.particles.IParticleData;
import net.minecraft.particles.ItemParticleData;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

import java.util.ArrayList;
import java.util.List;

public class InfusionAltarTileEntity extends BaseInventoryTileEntity implements ITickableTileEntity {
    private final BaseItemStackHandler inventory = new BaseItemStackHandler(2, this::markDirtyAndDispatch);
    private final BaseItemStackHandler recipeInventory = new BaseItemStackHandler(9);
    private final MultiblockPositions pedestalLocations = new MultiblockPositions.Builder()
            .pos(3, 0, 0).pos(0, 0, 3).pos(-3, 0, 0).pos(0, 0, -3)
            .pos(2, 0, 2).pos(2, 0, -2).pos(-2, 0, 2).pos(-2, 0, -2).build();
    private InfusionRecipe recipe;
    private int progress;
    private boolean active;

    public InfusionAltarTileEntity() {
        super(ModTileEntities.INFUSION_ALTAR.get());
        this.inventory.setDefaultSlotLimit(1);
        this.inventory.setSlotValidator(this::canInsertStack);
        this.inventory.setOutputSlots(1);
    }

    @Override
    public BaseItemStackHandler getInventory() {
        return this.inventory;
    }

    @Override
    public void read(BlockState state, CompoundNBT tag) {
        super.read(state, tag);
        this.progress = tag.getInt("Progress");
        this.active = tag.getBoolean("Active");
    }

    @Override
    public CompoundNBT write(CompoundNBT tag) {
        tag = super.write(tag);
        tag.putInt("Progress", this.progress);
        tag.putBoolean("Active", this.active);
        return tag;
    }

    @Override
    public void tick() {
        World world = this.getWorld();
        if (world != null && !world.isRemote()) {
            ItemStack input = this.inventory.getStackInSlot(0);
            if (input.isEmpty()) {
                this.reset();
                return;
            }

            if (this.isActive()) {
                List<InfusionPedestalTileEntity> pedestals = this.getPedestalsWithStuff();
                this.updateRecipeInventory(pedestals);
                if (this.recipe == null || !this.recipe.matches(this.recipeInventory)) {
                    this.recipe = (InfusionRecipe) world.getRecipeManager().getRecipe(RecipeTypes.INFUSION, this.recipeInventory.toIInventory(), world).orElse(null);
                }

                if (this.recipe != null) {
                    this.progress++;
                    if (this.progress >= 100) {
                        NonNullList<ItemStack> remaining = this.recipe.getRemainingItems(this.recipeInventory);
                        for (int i = 0; i < pedestals.size(); i++) {
                            InfusionPedestalTileEntity pedestal = pedestals.get(i);
                            pedestal.getInventory().setStackInSlot(0, remaining.get(i + 1));
                            this.spawnParticles(ParticleTypes.SMOKE, pedestal.getPos(), 1.2D, 20);
                        }

                        this.setOutput(this.recipe.getRecipeOutput());
                        this.reset();
                        this.markDirtyAndDispatch();
                        this.spawnParticles(ParticleTypes.HAPPY_VILLAGER, this.getPos(), 1.0D, 10);
                    } else {
                        pedestals.forEach(pedestal -> {
                            BlockPos pos = pedestal.getPos();
                            ItemStack stack = pedestal.getInventory().getStackInSlot(0);
                            this.spawnItemParticles(pos, stack);
                        });
                    }
                } else {
                    this.reset();
                }
            } else {
                this.progress = 0;
            }
        }
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return INFINITE_EXTENT_AABB;
    }

    public List<BlockPos> getPedestalPositions() {
        return this.pedestalLocations.get(this.getPos());
    }

    public boolean isActive() {
        if (!this.active) {
            World world = this.getWorld();
            this.active = world != null && world.isBlockPowered(this.getPos());
        }

        return this.active;
    }

    public void setActive() {
        this.active = true;
    }

    private void reset() {
        this.progress = 0;
        this.active = false;
    }

    private void updateRecipeInventory(List<InfusionPedestalTileEntity> pedestals) {
        this.recipeInventory.setSize(pedestals.size() + 1);
        this.recipeInventory.setStackInSlot(0, this.inventory.getStackInSlot(0));
        for (int i = 0; i < pedestals.size(); i++) {
            ItemStack stack = pedestals.get(i).getInventory().getStackInSlot(0);
            this.recipeInventory.setStackInSlot(i + 1, stack);
        }
    }

    private List<InfusionPedestalTileEntity> getPedestalsWithStuff() {
        if (this.getWorld() == null)
            return new ArrayList<>();

        List<InfusionPedestalTileEntity> pedestals = new ArrayList<>();
        this.getPedestalPositions().forEach(pos -> {
            TileEntity tile = this.getWorld().getTileEntity(pos);
            if (tile instanceof InfusionPedestalTileEntity) {
                InfusionPedestalTileEntity pedestal = (InfusionPedestalTileEntity) tile;
                ItemStack stack = pedestal.getInventory().getStackInSlot(0);
                if (!stack.isEmpty())
                    pedestals.add(pedestal);
            }
        });

        return pedestals;
    }

    private <T extends IParticleData> void spawnParticles(T particle, BlockPos pos, double yOffset, int count) {
        if (this.getWorld() == null || this.getWorld().isRemote())
            return;

        ServerWorld world = (ServerWorld) this.getWorld();

        double x = pos.getX() + 0.5D;
        double y = pos.getY() + yOffset;
        double z = pos.getZ() + 0.5D;

        world.spawnParticle(particle, x, y, z, count, 0, 0, 0, 0.1D);
    }

    private void spawnItemParticles(BlockPos pedestalPos, ItemStack stack) {
        if (this.getWorld() == null || this.getWorld().isRemote()) return;
        ServerWorld world = (ServerWorld) this.getWorld();
        BlockPos pos = this.getPos();

        double x = pedestalPos.getX() + (world.getRandom().nextDouble() * 0.2D) + 0.4D;
        double y = pedestalPos.getY() + (world.getRandom().nextDouble() * 0.2D) + 1.2D;
        double z = pedestalPos.getZ() + (world.getRandom().nextDouble() * 0.2D) + 0.4D;

        double velX = pos.getX() - pedestalPos.getX();
        double velY = 0.25D;
        double velZ = pos.getZ() - pedestalPos.getZ();

        world.spawnParticle(new ItemParticleData(ParticleTypes.ITEM, stack), x, y, z, 0, velX, velY, velZ, 0.18D);
    }

    private boolean canInsertStack(int slot, ItemStack stack) {
        return this.inventory.getStackInSlot(1).isEmpty();
    }

    private void setOutput(ItemStack stack) {
        this.inventory.getStacks().set(0, ItemStack.EMPTY);
        this.inventory.getStacks().set(1, stack);
    }
}
