package teamzeromanager.omniminer;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class OmniMiner implements ModInitializer {
    public static final String MOD_ID = "omniminer";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Hard cap keeps both the search and the number of break operations predictable.
    private static final int MAX_CONNECTED_ORES = 64;
    private static final TagKey<net.minecraft.block.Block> COMMON_ORES =
            TagKey.of(RegistryKeys.BLOCK, new Identifier("c", "ores"));

    // Prevents the extra block-break events caused by our own break calls from starting new scans.
    private static final Set<UUID> PLAYERS_BREAKING_VEIN = new HashSet<>();

    @Override
    public void onInitialize() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity serverPlayer)) {
                return true;
            }

            UUID playerId = player.getUuid();
            if (PLAYERS_BREAKING_VEIN.contains(playerId)
                    || !isPickaxe(player.getMainHandStack())
                    || !isOre(state)) {
                return true;
            }

            LongOpenHashSet connectedOres = findConnectedOres(world, pos);
            if (connectedOres.size() <= 1) {
                return true;
            }

            PLAYERS_BREAKING_VEIN.add(playerId);
            try {
                LongIterator iterator = connectedOres.iterator();
                while (iterator.hasNext()) {
                    long orePos = iterator.nextLong();
                    if (orePos == pos.asLong()) {
                        continue;
                    }

                    BlockPos target = BlockPos.fromLong(orePos);
                    if (!world.getBlockState(target).isAir()) {
                        serverPlayer.interactionManager.tryBreakBlock(target);
                    }
                }
            } finally {
                PLAYERS_BREAKING_VEIN.remove(playerId);
            }

            return true;
        });

        LOGGER.info("OmniMiner initialized: optimized mixed-ore mining enabled.");
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

    /**
     * Finds face-connected ores with primitive packed positions to avoid allocating
     * a BlockPos and hash node for every visited block.
     */
    private static LongOpenHashSet findConnectedOres(World world, BlockPos start) {
        LongOpenHashSet found = new LongOpenHashSet(MAX_CONNECTED_ORES);
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();

        long startPos = start.asLong();
        found.add(startPos);
        queue.enqueue(startPos);

        BlockPos.Mutable cursor = new BlockPos.Mutable();

        while (!queue.isEmpty() && found.size() < MAX_CONNECTED_ORES) {
            long current = queue.dequeueLong();
            cursor.set(current);

            for (Direction direction : Direction.values()) {
                cursor.set(current).move(direction);

                // Do not cause chunk loads just to search for more ores.
                if (!world.isChunkLoaded(cursor)) {
                    continue;
                }

                if (!isOre(world.getBlockState(cursor))) {
                    continue;
                }

                long next = cursor.asLong();
                if (found.add(next)) {
                    queue.enqueue(next);
                    if (found.size() >= MAX_CONNECTED_ORES) {
                        break;
                    }
                }
            }
        }

        return found;
    }
}
