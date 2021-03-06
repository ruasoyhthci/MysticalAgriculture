package com.blakebr0.mysticalagriculture.augment;

import com.blakebr0.cucumber.helper.BlockHelper;
import com.blakebr0.cucumber.helper.ColorHelper;
import com.blakebr0.mysticalagriculture.api.tinkering.Augment;
import com.blakebr0.mysticalagriculture.api.tinkering.AugmentType;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;

import java.util.EnumSet;

public class MiningAOEAugment extends Augment {
    private final int range;

    public MiningAOEAugment(ResourceLocation id, int tier, int range) {
        super(id, tier, EnumSet.of(AugmentType.PICKAXE, AugmentType.AXE, AugmentType.SHOVEL), getColor(0xD5FFF6, tier), getColor(0x0EBABD, tier));
        this.range = range;
    }

    @Override
    public boolean onBlockStartBreak(ItemStack stack, BlockPos pos, PlayerEntity player) {
        World world = player.getEntityWorld();
        BlockRayTraceResult trace = BlockHelper.rayTraceBlocks(world, player);
        int side = trace.getFace().ordinal();
        return !this.harvest(stack, this.range, world, pos, side, player);
    }

    private boolean harvest(ItemStack stack, int radius, World world, BlockPos pos, int side, PlayerEntity player) {
        if (player.isCrouching())
            radius = 0;

        int xRange = radius;
        int yRange = radius;
        int zRange = 0;

        if (side == 0 || side == 1) {
            zRange = radius;
            yRange = 0;
        }

        if (side == 4 || side == 5) {
            xRange = 0;
            zRange = radius;
        }

        BlockState state = world.getBlockState(pos);
        float hardness = state.getBlockHardness(world, pos);

        if (!this.tryHarvest(world, pos, false, stack, player))
            return false;

        Block block = state.getBlock();
        if (radius > 0 && hardness >= 0.2F && block.getHarvestTool(state) == null || stack.getToolTypes().stream().anyMatch(t -> block.isToolEffective(state, t))) {
            BlockPos.getAllInBox(pos.add(-xRange, -yRange, -zRange), pos.add(xRange, yRange, zRange)).forEach(aoePos -> {
                if (aoePos != pos) {
                    BlockState aoeState = world.getBlockState(aoePos);
                    if (!aoeState.hasTileEntity() && aoeState.getBlockHardness(world, aoePos) <= hardness + 5.0F) {
                        Block aoeBlock = aoeState.getBlock();
                        if (block.getHarvestTool(state) == null || stack.getToolTypes().stream().anyMatch(t -> aoeBlock.isToolEffective(state, t))) {
                            this.tryHarvest(world, aoePos, true, stack, player);
                        }
                    }
                }
            });
        }

        return true;
    }

    private boolean tryHarvest(World world, BlockPos pos, boolean extra, ItemStack stack, PlayerEntity player) {
        BlockState state = world.getBlockState(pos);
        float hardness = state.getBlockHardness(world, pos);
        Item item = stack.getItem();
        boolean harvest = (ForgeHooks.canHarvestBlock(state, player, world, pos) || item.canHarvestBlock(stack, state)) && (!extra || item.getDestroySpeed(stack, world.getBlockState(pos)) > 1.0F);
        if (hardness >= 0.0F && (!extra || harvest))
            return BlockHelper.breakBlocksAOE(stack, world, player, pos);

        return false;
    }

    private static int getColor(int color, int tier) {
        return ColorHelper.saturate(color, Math.min((float) tier / 5, 1));
    }
}
