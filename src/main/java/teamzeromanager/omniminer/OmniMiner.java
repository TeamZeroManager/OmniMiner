package teamzeromanager.omniminer;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemTags;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class OmniMiner implements ModInitializer {
    public static final String MOD_ID = "omniminer";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final int MAX_CONNECTED_ORES = 64;
    private static final TagKey<net.minecraft.block.Block> COMMON_ORES =
            TagKey.of(RegistryKeys.BLOCK, new Identifier("c", "ores"));
    private static final Set<UUID> PLAYERS_BREAKING_VEIN = new HashSet<>();

    @Override
    public void onInitialize() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world.isClient || !(world instanceof World) || !(player instanceof ServerPlayerEntity serverPlayer)) {
                return true;
            }
            if (PLAYERS_BREAKING_VEIN.contains(player.getUuid()) || !isPickaxe(player.getMainHandStack()) || !isOre(state)) {
                return true;
            }

            Set<BlockPos> connectedOres = findConnectedOres(world, pos);
            if (connectedOres.size() <= 1) {
                return true;
            }

            PLAYERS_BREAKING_VEIN.add(player.getUuid());
            try {
                for (BlockPos orePos : connectedOres) {
                    if (orePos.equals(pos)) continue;
                    if (!world.getBlockState(orePos).isAir()) {
                        serverPlayer.interactionManager.tryBreakBlock(orePos);
                    }
                }
            } finally {
                PLAYERS_BREAKING_VEIN.remove(player.getUuid());
            }
            return true;
        });

        LOGGER.info("OmniMiner initialized: connected mixed-ore mining enabled.");
    }

    private static boolean isPickaxe(ItemStack stack) {
        return stack.isIn(ItemTags.PICKAXES);
    }

    private static boolean isOre(BlockState state) {
        return state.isIn(COMMON_ORES)
                || state.isIn(BlockTags.COAL_ORES)
                || state.isIn(BlockTags.IRON_ORES)
                || state.isIn(BlockTags.COPPER_ORES)
                || state.isIn(BlockTags.GOLD_ORES)
                || state.isIn(BlockTags.REDSTONE_ORES)
                || state.isIn(BlockTags.LAPIS_ORES)
                || state.isIn(BlockTags.DIAMOND_ORES)
                || state.isIn(BlockTags.EMERALD_ORES)
                || state.isOf(Blocks.NETHER_QUARTZ_ORE)
                || state.isOf(Blocks.NETHER_GOLD_ORE);
    }

    private static Set<BlockPos> findConnectedOres(World world, BlockPos start) {
        Set<BlockPos> found = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        BlockPos origin = start.toImmutable();
        found.add(origin);
        queue.add(origin);

        while (!queue.isEmpty() && found.size() < MAX_CONNECTED_ORES) {
            BlockPos current = queue.removeFirst();
            for (Direction direction : Direction.values()) {
                BlockPos next = current.offset(direction);
                if (found.contains(next)) continue;
                if (isOre(world.getBlockState(next))) {
                    BlockPos immutable = next.toImmutable();
                    found.add(immutable);
                    queue.addLast(immutable);
                    if (found.size() >= MAX_CONNECTED_ORES) break;
                }
            }
        }
        return found;
    }
}
