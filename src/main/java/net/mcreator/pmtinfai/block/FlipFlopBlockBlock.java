
package net.mcreator.pmtinfai.block;

import net.minecraftforge.registries.ObjectHolder;
import net.minecraftforge.fml.network.NetworkHooks;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.RegistryEvent;

import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.World;
import net.minecraft.world.IWorldReader;
import net.minecraft.world.IBlockReader;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.NonNullList;
import net.minecraft.util.Hand;
import net.minecraft.util.Direction;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.LockableLootTileEntity;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.state.StateContainer;
import net.minecraft.state.IntegerProperty;
import net.minecraft.state.EnumProperty;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.NetworkManager;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.item.BlockItem;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.block.material.Material;
import net.minecraft.block.SoundType;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.Block;

import net.mcreator.pmtinfai.itemgroup.LogicBlocksItemGroup;
import net.mcreator.pmtinfai.gui.LogicBlockGUIGui;
import net.mcreator.pmtinfai.gui.FlipFlopGUIGui;
import net.mcreator.pmtinfai.PMTINFAIElements;
import net.mcreator.pmtinfai.InputSide;
import net.mcreator.pmtinfai.FFSpecies;

import java.util.Random;
import java.util.List;
import java.util.Collections;
import java.util.ArrayList;

import io.netty.buffer.Unpooled;

@PMTINFAIElements.ModElement.Tag
public class LogicBlockBlock extends PMTINFAIElements.ModElement {
	@ObjectHolder("pmtinfai:flipflopblock")
	public static final Block block = null;
	@ObjectHolder("pmtinfai:flipflopblock")
	public static final TileEntityType<CustomTileEntity> tileEntityType = null;
	public LogicBlockBlock(PMTINFAIElements instance) {
		super(instance, 12);
		FMLJavaModLoadingContext.get().getModEventBus().register(this);
	}

	@Override
	public void initElements() {
		elements.blocks.add(() -> new CustomBlock());
		elements.items
				.add(() -> new BlockItem(block, new Item.Properties().group(LogicBlocksItemGroup.tab)).setRegistryName(block.getRegistryName()));
	}

	@SubscribeEvent
	public void registerTileEntity(RegistryEvent.Register<TileEntityType<?>> event) {
		event.getRegistry().register(TileEntityType.Builder.create(CustomTileEntity::new, block).build(null).setRegistryName("flipflopblock"));
	}
	public static class CustomBlock extends Block {
		private final String SetItem_ = "input";
		private final String ResetItem_ = "input";
		private final String OutputItem_ = "output";
		private final String ClockItem_ = "output";
		// Properties des Blocks
		public static final IntegerProperty POWER = BlockStateProperties.POWER_0_15;
		public static final EnumProperty<InputSide> INPUT1 = EnumProperty.create("set_side", InputSide.class);
		public static final EnumProperty<InputSide> INPUT2 = EnumProperty.create("reset_side", InputSide.class);
		public static final EnumProperty<InputSide> INPUT3 = EnumProperty.create("clock_side", InputSide.class);
		public static final EnumProperty<InputSide> OUTPUT = EnumProperty.create("output", InputSide.class);
		public static final EnumProperty<FFSpecies> LOGIC = EnumProperty.create("logic", FFSpecies.class);
		// weitere Variablen
		private static boolean aa = false;
		// boolean Variablen zum Abfangen von Multithreading
		public CustomBlock() {
			super(Block.Properties.create(Material.MISCELLANEOUS).sound(SoundType.STEM).hardnessAndResistance(0f, 0f).lightValue(0));
			setRegistryName("flipflopblock");
			// Laden der Default Properties der Bl�cke
			this.setDefaultState(this.stateContainer.getBaseState().with(POWER, Integer.valueOf(0)).with(INPUT1, InputSide.NONE)
					.with(INPUT2, InputSide.NONE).with(INPUT3, InputSide.NONE).with(OUTPUT, InputSide.NONE).with(LOGIC, FFSpecies.NONE));
		}

		// --------------------------------------------Getter------------
		/**
		 * Gibt den Block als Item zur�ck
		 * 
		 * @param state
		 *            BlockState des Blockes
		 * @param builder
		 *            Builder des LootContexes
		 * @return Block als Item oder Items
		 */
		@Override
		public List<ItemStack> getDrops(BlockState state, LootContext.Builder builder) {
			List<ItemStack> dropsOriginal = super.getDrops(state, builder);
			return !dropsOriginal.isEmpty() ? dropsOriginal : Collections.singletonList(new ItemStack(this, 1));
		}

		@Override
		public INamedContainerProvider getContainer(BlockState state, World worldIn, BlockPos pos) {
			TileEntity tileEntity = worldIn.getTileEntity(pos);
			return tileEntity instanceof INamedContainerProvider ? (INamedContainerProvider) tileEntity : null;
		}

		/**
		 * Gibt die Tickrate des Blockes zur�ck
		 * 
		 * @param worldIn
		 *            Teil der Welt des Blockes
		 * @return Die aktuelle Tickrate des Blockes
		 */
		public int tickRate(IWorldReader worldIn) {
			return 1;
		}

		/**
		 * Abfgage wie stark die WeakPower(direkte Redstoneansteuerung) ist
		 * 
		 * @param blockState
		 *            BlockState des Blockes
		 * @param blockAccess
		 *            Angabe welche Art der Block ist
		 * @param pos
		 *            Position des Blockes
		 * @param side
		 *            Seite an der die Power abgefragt wird
		 * @return Gibt die RedstonePower(0-15) zur�ck
		 */
		@Override
		public int getWeakPower(BlockState blockState, IBlockReader blockAccess, BlockPos pos, Direction side) {
			return ((InputSide) blockState.get(OUTPUT)).GetDirection() == side ? blockState.get(POWER) : 0;
		}

		/**
		 * Abfgage wie stark die StrongPower(indirekte Redstoneansteuerung) ist
		 * 
		 * @param blockState
		 *            BlockState des Blockes
		 * @param blockAccess
		 *            Angabe welche Art der Block ist
		 * @param pos
		 *            Position des Blockes
		 * @param side
		 *            Seite an der die Power abgefragt wird
		 * @return Gibt die RedstonePower(0-15) zur�ck
		 */
		@Override
		public int getStrongPower(BlockState blockState, IBlockReader blockAccess, BlockPos pos, Direction side) {
			return this.getWeakPower(blockState, blockAccess, pos, side);
		}

		/**
		 * Gibt den VoxelShape(Aussehen) des Blockes zur�ck
		 * 
		 * @param state
		 *            Blockstate des Blockes
		 * @param worldIn
		 *            Teil der Welt des Blockes
		 * @param pos
		 *            Position des Blockes
		 * @param context
		 *            Kontext
		 * @return VoxelShape des Blockes
		 */
		public VoxelShape getShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context) {
			return Block.makeCuboidShape(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D);
		}

		/**
		 * Erstellt die neue Warheitstabelle
		 * 
		 * @param exp
		 *            Expresion der neuen Logik
		 * @param world
		 *            Welt des Blockes
		 * @param pos
		 *            Position des Blockes
		 */
		public void GetAllStates(String exp, World world, BlockPos pos) {
			BlockState bs = world.getBlockState(pos);
			FFSpecies species = FFSpecies.GetEnum(exp);
			if (species == bs.get(LOGIC))
				return;
			world.setBlockState(pos, bs.with(LOGIC, species));
			getTE(world, pos).SetHIGH(false);
			getTE(world, pos).SetLOW(false);
			getTE(world, pos).SetMS(0);
			update(world.getBlockState(pos), world, pos, null, getPowerOnSides(world, pos, world.getBlockState(pos)));
		}

		// -------------------------------------Eventlistener----------------------
		/**
		 * EventListener wenn ein Rechtsklick auf den Block durchgef�hrt wird
		 * 
		 * @param state
		 *            BlockState des Blockes
		 * @param world
		 *            Welt des Blockes
		 * @param pos
		 *            Position des Blockes
		 * @param entity
		 *            Player der den Rechtsklick ausf�hrt
		 * @param hand
		 *            Hand die das ausf�hrt
		 * @param hit
		 *            Hit
		 */
		@Override
		public boolean onBlockActivated(BlockState state, World world, BlockPos pos, PlayerEntity entity, Hand hand, BlockRayTraceResult hit) {
			boolean retval = super.onBlockActivated(state, world, pos, entity, hand, hit);
			int x = pos.getX();
			int y = pos.getY();
			int z = pos.getZ();
			if (entity instanceof ServerPlayerEntity) {
				NetworkHooks.openGui((ServerPlayerEntity) entity, new INamedContainerProvider() {
					@Override
					public ITextComponent getDisplayName() {
						return new StringTextComponent("FlipFlop Block");
					}

					@Override
					public Container createMenu(int id, PlayerInventory inventory, PlayerEntity player) {
						return new FlipFlopGUIGui.GuiContainerMod(id, inventory,
								new PacketBuffer(Unpooled.buffer()).writeBlockPos(new BlockPos(x, y, z)));
					}
				}, new BlockPos(x, y, z));
			}
			return true;
		}

		/**
		 * EventListener wenn der Block ersetzt wird
		 * 
		 * @param state
		 *            BlockState des Blockes
		 * @param world
		 *            Welt des Blockes
		 * @param pos
		 *            Position des Blockes
		 *
		 * @param newState
		 *            neuer BlockState des Blockes
		 * @param isMoving
		 *            Gibt an ob der Block sich bewegt
		 */
		@Override
		public void onReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean isMoving) {
			if (state.getBlock() != newState.getBlock()) {
				TileEntity tileentity = world.getTileEntity(pos);
				if (tileentity instanceof CustomTileEntity) {
					InventoryHelper.dropInventoryItems(world, pos, (CustomTileEntity) tileentity);
					world.updateComparatorOutputLevel(pos, this);
				}
				super.onReplaced(state, world, pos, newState, isMoving);
			}
		}

		/**
		 * EventListener wenn Nachbar des Blockes sich �ndert - Aktualisiert weitere
		 * Bl�cke
		 * 
		 * @param state
		 *            Blockstate des Blockes
		 * @param world
		 *            Welt in der der Block steht
		 * @param pos
		 *            Position des Blockes
		 * @param neighborBlock
		 *            Nachbarblock der sich �ndert
		 * @param fromPos
		 *            Position von dem sich �nderndem Block
		 * @param isMoving
		 *            Gibt an ob der Block sich bewegt
		 */
		@Override
		public void neighborChanged(BlockState state, World worldIn, BlockPos pos, Block blockIn, BlockPos fromPos, boolean isMoving) {
			if (!state.isValidPosition(worldIn, pos)) {
				TileEntity tileentity = state.hasTileEntity() ? worldIn.getTileEntity(pos) : null;
				spawnDrops(state, worldIn, pos, tileentity);
				worldIn.removeBlock(pos, false);
				for (Direction d : Direction.values())
					worldIn.notifyNeighborsOfStateChange(pos.offset(d), this);
				return;
			}
			if (state.get(POWER) != this.getPowerOnSides(worldIn, pos, state) && !worldIn.getPendingBlockTicks().isTickPending(pos, this)) {
				update(state, worldIn, pos, null, this.getPowerOnSides(worldIn, pos, state));
			}
		}

		/**
		 * EventListener wenn Block durch Entity gesetzt wurde
		 * 
		 * @param state
		 *            Blockstate des Blockes
		 * @param world
		 *            Welt in der der Block steht
		 * @param pos
		 *            Position des Blockes
		 * @param placer
		 *            Entity die den Block gesetzt hat
		 * @param stack
		 *            Stack des Items
		 */
		public void onBlockPlacedBy(World worldIn, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
			if (this.getPowerOnSides(worldIn, pos, state) > 0) {
				worldIn.getPendingBlockTicks().scheduleTick(pos, this, 1);
			}
		}

		// ----------------------------------Abfrage----------------------------
		/**
		 * Abfrage ob Block TileEntitys hat
		 * 
		 * @param state
		 *            BlockState des Blockes
		 * @return Gibt an ob Block TileEntitys hat
		 */
		@Override
		public boolean hasTileEntity(BlockState state) {
			return true;
		}

		/**
		 *
		 * Abfrage ob Block ein Event bekommen hat
		 * 
		 * @param state
		 *            BlockState des Blockes
		 * @param world
		 *            Welt des Blockes
		 *
		 * @param pos
		 *            Position des Blockes
		 * @param eventID
		 *            ID des Events
		 * @param eventParam
		 *            Parametes des Event
		 * @return Gibt an ob Block Event bekommen hat
		 */
		@Override
		public boolean eventReceived(BlockState state, World world, BlockPos pos, int eventID, int eventParam) {
			super.eventReceived(state, world, pos, eventID, eventParam);
			TileEntity tileentity = world.getTileEntity(pos);
			return tileentity == null ? false : tileentity.receiveClientEvent(eventID, eventParam);
		}

		/**
		 * Abfrage ob Redstone sich an der Seite verbinden kann
		 * 
		 * @param state
		 *            Blockstate des Blockes
		 * @param world
		 *            Angabe welche Art der Block ist
		 * @param pos
		 *            Position des Blockes
		 * @param side
		 *            Seite die Abgefragt wird
		 * @return Gibt zur�ck ob der Redstone sich verbinden kann
		 */
		@Override
		public boolean canConnectRedstone(BlockState state, IBlockReader world, BlockPos pos, Direction side) {
			return side == null ? false : (((InputSide) state.get(OUTPUT)).GetDirection() == side || existInputDirections(state, side));
		}

		/**
		 * Abfrage ob der Block RedstonePower ausgeben kann
		 * 
		 * @param state
		 *            Blockstate des Blockes
		 * @return Gibt an ob der Block RedstonePower ausgeben kann
		 */
		@Override
		public boolean canProvidePower(BlockState state) {
			return true;
		}

		/**
		 * Abfrage ob es eine valide Position f�r den Block ist
		 * 
		 * @param state
		 *            Blockstate des Blockes
		 * @param worldIn
		 *            Teil der Welt des Blockes
		 * @param pos
		 *            Position des Blockes
		 * @return Gibt an ob der Platz des Blockes valide ist
		 */
		public boolean isValidPosition(BlockState state, IWorldReader worldIn, BlockPos pos) {
			return func_220064_c(worldIn, pos.down());
		}

		/**
		 * Abfrage ob Block fest ist
		 * 
		 * @param state
		 *            BlockState des Blockes
		 * @return Gibt an ob Block solid ist
		 */
		public boolean isSolid(BlockState state) {
			return true;
		}

		// ------------------------------Others
		@Override
		public TileEntity createTileEntity(BlockState state, IBlockReader world) {
			return new CustomTileEntity();
		}

		/**
		 * Initalisiert die Parameter
		 * 
		 * @param builder
		 *            Builder des Blockes
		 */
		@Override
		protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
			builder.add(POWER).add(INPUT1).add(INPUT2).add(INPUT3).add(OUTPUT).add(LOGIC);
		}

		/**
		 * Wechselt den Blockstate, fals
		 * 
		 * @param state
		 *            Blockstate des Blockes
		 * @param worldIn
		 *            Welt des Blockes
		 * @param pos
		 *            Position des Blockes, der den Tick ausf�hren soll
		 * @param random
		 *            Ein Java Random Element f�r Zuf�llige Ticks
		 */
		public void tick(BlockState state, World worldIn, BlockPos pos, Random random) {
			update(state, worldIn, pos, random, this.getPowerOnSides(worldIn, pos, state));
		}

		/**
		 * Wechselt den blockstate, fals
		 * 
		 * @param state
		 *            Blockstate des Blockes
		 * @param worldIn
		 *            Welt des Blockes
		 * @param pos
		 *            Position des Blockes, der geupdated werden soll
		 * @param random
		 *            Ein Java Random Element f�r Zuf�llige Ticks
		 * @param clalculatedOutput
		 *            Auf diesen Wert soll der Output des blockes gesetzt werden
		 * 
		 */
		public void update(BlockState state, World worldIn, BlockPos pos, Random random, int calculatedOutput) {
			if (state.get(POWER) > 0 && calculatedOutput == 0) {
				worldIn.setBlockState(pos, state.with(POWER, Integer.valueOf("0")), 3);
			} else if (calculatedOutput > 0 && state.get(POWER) != calculatedOutput) {
				worldIn.setBlockState(pos, state.with(POWER, calculatedOutput), 3);
			}
			worldIn.getPendingBlockTicks().scheduleTick(pos, this, this.tickRate(worldIn));
		}

		/**
		 * Input und Output �ndern
		 * 
		 * @param slot
		 *            SlotID des �ndernden Slot
		 * @param world
		 *            Welt des Blockes
		 * @param pos
		 *            Position des Blockes
		 * @param item
		 *            Item im GUI
		 */
		public boolean[] changeInput(int slot, BlockPos pos, World world, Item item) {
			BlockState blockstate = world.getBlockState(pos);
			Direction d = SlotIDtoDirection(slot).getOpposite();
			// System.out.println(item.toString());
			if (item.toString() == SetItem_) {
				System.out.println("Change SET in slot \'" + slot + "\'");
				clearSlot(pos, world, d, 0);
				world.setBlockState(pos, blockstate.with(INPUT1, InputSide.GetEnum(d)));
			} else if (item.toString() == ResetItem_) {
				System.out.println("Change RESET in slot \'" + slot + "\'");
				clearSlot(pos, world, d, 1);
				world.setBlockState(pos, blockstate.with(INPUT2, InputSide.GetEnum(d)));
			} else if (item.toString() == ClockItem_) {
				System.out.println("Change CLOCK in slot \'" + slot + "\'");
				clearSlot(pos, world, d, 2);
				world.setBlockState(pos, blockstate.with(INPUT3, InputSide.GetEnum(d)));
			} else if (item.toString() == OutputItem_) {
				System.out.println("Change OUTPUT in slot \'" + slot + "\'");
				clearSlot(pos, world, d, 3);
				world.setBlockState(pos, blockstate.with(OUTPUT, InputSide.GetEnum(d)));
			}
			return new boolean[]{((InputSide) blockstate.get(INPUT1)).isActive(), ((InputSide) blockstate.get(INPUT2)).isActive(),
					((InputSide) blockstate.get(INPUT3)).isActive(), ((InputSide) blockstate.get(OUTPUT)).isActive()};
		}

		// ----private----------------
		/**
		 *** private*** L�scht alle Directions d aus den Propertys au�er einer
		 * 
		 * @param pos
		 *            Position des Blockes
		 * @param world
		 *            Welt des Blockes
		 * @param d
		 *            Direction die gel�scht wird aus allem
		 * @param except
		 *            Die Exception die nicht ersetzt wird 0-2 INPUT1-3, 3-Output
		 */
		private void clearSlot(BlockPos pos, World world, Direction d, int except) {
			BlockState bs = world.getBlockState(pos);
			if (except != 0) {
				if (((InputSide) blockstate.get(INPUT1)).GetDirection() == d) {
					bs.with(INPUT1, InputSide.NONE);
				}
			}
			if (except != 1) {
				if (((InputSide) blockstate.get(INPUT2)).GetDirection() == d) {
					bs.with(INPUT2, InputSide.NONE);
				}
			}
			if (except != 2) {
				if (((InputSide) blockstate.get(INPUT3)).GetDirection() == d) {
					bs.with(INPUT3, InputSide.NONE);
				}
			}
			if (except != 3) {
				if (((InputSide) blockstate.get(OUTPUT)).GetDirection() == d) {
					bs.with(OUTPUT, InputSide.NONE);
				}
			}
		}

		/**
		 *** private*** Abfrage ob Input existiert
		 * 
		 * @param blockstate
		 *            Blockstate des Blockes
		 * @param d
		 *            Direction des Blockes
		 */
		private boolean existInputDirections(BlockState blockstate, Direction d) {
			if (blockstate.has(INPUT1) && d == ((InputSide) blockstate.get(INPUT1)).GetDirection())
				return true;
			if (blockstate.has(INPUT2) && d == ((InputSide) blockstate.get(INPUT2)).GetDirection())
				return true;
			if (blockstate.has(INPUT3) && d == ((InputSide) blockstate.get(INPUT3)).GetDirection())
				return true;
			return false;
		}

		/**
		 *** private*** Lie�t alle Redstone Inputs ein und gibt den neuen Output aus
		 * 
		 * @param world
		 *            Welt des Blockes
		 * @param pos
		 *            Position des Blockes
		 * @param state
		 *            Blockstate des Blockes
		 */
		private int getPowerOnSides(World world, BlockPos pos, BlockState blockstate) {
			ArrayList<Integer> inputs = new ArrayList();
			int out;
			if (blockstate.has(INPUT1) && blockstate.get(INPUT1) != InputSide.NONE)
				inputs.add(this.getPowerOnSide(world, pos, ((InputSide) blockstate.get(INPUT1)).GetDirection()));
			if (blockstate.has(INPUT2) && blockstate.get(INPUT2) != InputSide.NONE)
				inputs.add(this.getPowerOnSide(world, pos, ((InputSide) blockstate.get(INPUT2)).GetDirection()));
			if (blockstate.has(INPUT3) && blockstate.get(INPUT3) != InputSide.NONE)
				inputs.add(this.getPowerOnSide(world, pos, ((InputSide) blockstate.get(INPUT3)).GetDirection()));
			if (inputs.size() <= 0)
				return 0;
			else
				return logic(inputs, world, pos);
		}

		/**
		 *** private*** Lie�t den Redstone Input an einer Seite an
		 * 
		 * @param world
		 *            Welt des Blockes
		 * @param pos
		 *            Position des Blockes, der den Redstonewert bekommt
		 * @param side
		 *            Seite an der der Redstonewert eingegeben wird
		 */
		private int getPowerOnSide(World world, BlockPos pos, Direction side) {
			Direction direction = side.getOpposite();
			BlockPos blockpos = pos.offset(direction);
			int i = world.getRedstonePower(blockpos, direction);
			if (i >= 15) {
				return i;
			} else {
				BlockState blockstate = world.getBlockState(blockpos);
				return Math.max(i, blockstate.getBlock() == Blocks.REDSTONE_WIRE ? blockstate.get(RedstoneWireBlock.POWER) : 0);
			}
		}

		/**
		 *** private*** SlotId anhand der Direction
		 * 
		 * @param d
		 *            Seite des SlotIds
		 * @return SlotID
		 */
		private int DirectiontoSlotID(Direction d) {
			if (d == Direction.WEST)
				return 0;
			if (d == Direction.NORTH)
				return 1;
			if (d == Direction.EAST)
				return 2;
			if (d == Direction.SOUTH)
				return 3;
			return -1;
		}

		/**
		 *** private*** Direction anhand der SlotId
		 * 
		 * @param slot
		 *            ID des Slot
		 * @return Direction des SlotIDs
		 */
		private Direction SlotIDtoDirection(int slot) {
			switch (slot) {
				case 0 :
					return Direction.WEST;
				case 1 :
					return Direction.NORTH;
				case 2 :
					return Direction.EAST;
				case 3 :
					return Direction.SOUTH;
				default :
					return null;
			}
		}

		/**
		 *** private*** Gibt die Logik des Blockes an
		 * 
		 * @param inputs
		 *            Alle Redstone Input Values
		 * @return Neuer Output
		 */
		private int logic(List<Integer> inputs, World world, BlockPos pos) {
			// set,reset,clock
			BlockState bs = world.getBlockState(pos);
			if (bs.get(LOGIC) == FFSpecies.NONE)
				return;
			char[] table = ((FFSpecies) bs.get(LOGIC)).GetTable();
			char help = '-';
			switch (((FFSpecies) bs.get(LOGIC)).GetClockMode()) {
				case 0 :
					inputs.remove(2);
					help = table[(2 * inputs.get(0)) + inputs.get(1)];
					if (help == 'T')
						return Collections.max(inputs);
					if (help == 'F')
						return 0;
					if (help == 'D') {
						if (bs.get(POWER) == 0)
							return Collections.max(inputs);
						else {
							return 0;
						}
					}
					if (help == 'Q')
						return bs.get(POWER);
					// No Clock
				case 1 :
					if (inputs.remove(2) <= 0) {
						return bs.get(POWER);
					}
					help = table[(2 * inputs.get(0)) + inputs.get(1)];
					if (help == 'T')
						return Collections.max(inputs);
					if (help == 'F')
						return 0;
					if (help == 'D') {
						if (bs.get(POWER) == 0)
							return Collections.max(inputs);
						else {
							return 0;
						}
					}
					if (help == 'Q')
						return bs.get(POWER);
					// pegel Clock
				case 2 :
					if (inputs.remove(2) <= 0) {
						getTE(world, pos).SetHIGH(false);
						return bs.get(POWER);
					}
					if (getTE(world, pos).GetHIGH()) {
						return bs.get(POWER);
					}
					getTE(world, pos).SetHIGH(true);
					help = table[(2 * inputs.get(0)) + inputs.get(1)];
					if (help == 'T')
						return Collections.max(inputs);
					if (help == 'F')
						return 0;
					if (help == 'D') {
						if (bs.get(POWER) == 0)
							return Collections.max(inputs);
						else {
							return 0;
						}
					}
					if (help == 'Q')
						return bs.get(POWER);
					// HF Clock
				case 3 :
					if (inputs.remove(2) > 0) {
						getTE(world, pos).SetLOW(false);
						return bs.get(POWER);
					}
					if (getTE(world, pos).GetLOW()) {
						return bs.get(POWER);
					}
					getTE(world, pos).SetLOW(true);
					help = table[(2 * inputs.get(0)) + inputs.get(1)];
					if (help == 'T')
						return Collections.max(inputs);
					if (help == 'F')
						return 0;
					if (help == 'D') {
						if (bs.get(POWER) == 0)
							return Collections.max(inputs);
						else {
							return 0;
						}
					}
					if (help == 'Q')
						return bs.get(POWER);
					// LF Clock
				case 4 :
					if (inputs.remove(2) > 0) {
						getTE(world, pos).SetLOW(false);
						if (getTE(world, pos).GetHIGH()) {
							return;
						} else {
							getTE(world, pos).SetHIGH(true);
							help = table[(2 * inputs.get(0)) + inputs.get(1)];
							if (help == 'T') {
								getTE(world, pos).SetMS(Collections.max(inputs));
								return bs.get(POWER);
							}
							if (help == 'F') {
								getTE(world, pos).SetMS(0);
								return bs.get(POWER);
							}
							if (help == 'D') {
								if (bs.get(POWER) == 0) {
									getTE(world, pos).SetMS(Collections.max(inputs));
									return bs.get(POWER);
								} else {
									getTE(world, pos).SetMS(0);
									return bs.get(POWER);
								}
							}
						}
						if (help == 'Q') {
							getTE(world, pos).SetMS(bs.get(POWER));
							return bs.get(POWER);
						}
					} else {
						getTE(world, pos).SetHIGH(false);
						if (getTE(world, pos).GetLOW()) {
							return;
						} else {
							// Ausgabe
							return getTE(world, pos).GetMS();
						}
					} // MS Clock
				default :
					return 0;
			}
		}

		private CustomTileEntity getTE(World world, BlockPos pos) {
			return (CustomTileEntity) world.getTileEntity(pos);
		}
	}

	public static class CustomTileEntity extends LockableLootTileEntity {
		private NonNullList<ItemStack> stacks = NonNullList.<ItemStack>withSize(5, ItemStack.EMPTY);
		private boolean HIGH = false;
		private boolean LOW = false;
		private int MS = 0;
		protected CustomTileEntity() {
			super(tileEntityType);
		}

		@Override
		public void read(CompoundNBT compound) {
			super.read(compound);
			this.stacks = NonNullList.withSize(this.getSizeInventory(), ItemStack.EMPTY);
			HIGH = compound.getBoolean("HIGH");
			LOW = compound.getBoolean("LOW");
			MS = compound.getInt("MS");
			ItemStackHelper.loadAllItems(compound, this.stacks);
		}

		@Override
		public CompoundNBT write(CompoundNBT compound) {
			super.write(compound);
			ItemStackHelper.saveAllItems(compound, this.stacks);
			compound.putBoolean("HIGH", HIGH);
			compound.putBoolean("LOW", LOW);
			compound.putInt("MS", MS);
			return compound;
		}

		public boolean GetHIGH() {
			return HIGH;
		}

		public void SetHIGH(boolean set) {
			HIGH = set;
		}

		public boolean GetLOW() {
			return LOW;
		}

		public void SetLOW(boolean set) {
			LOW = set;
		}

		public int GetMS() {
			return MS;
		}

		public void SetMS(int set) {
			MS = set;
		}

		@Override
		public SUpdateTileEntityPacket getUpdatePacket() {
			return new SUpdateTileEntityPacket(this.pos, 0, this.getUpdateTag());
		}

		@Override
		public CompoundNBT getUpdateTag() {
			return this.write(new CompoundNBT());
		}

		@Override
		public void onDataPacket(NetworkManager net, SUpdateTileEntityPacket pkt) {
			this.read(pkt.getNbtCompound());
		}

		@Override
		public int getSizeInventory() {
			return 5;
		}

		@Override
		public boolean isEmpty() {
			for (ItemStack itemstack : this.stacks)
				if (!itemstack.isEmpty())
					return false;
			return true;
		}

		@Override
		public boolean isItemValidForSlot(int index, ItemStack stack) {
			return true;
		}

		@Override
		public ItemStack getStackInSlot(int slot) {
			return stacks.get(slot);
		}

		@Override
		public ITextComponent getDefaultName() {
			return new StringTextComponent("flipflopblock");
		}

		@Override
		public int getInventoryStackLimit() {
			return 1;
		}

		@Override
		public Container createMenu(int id, PlayerInventory player) {
			return new LogicBlockGUIGui.GuiContainerMod(id, player, new PacketBuffer(Unpooled.buffer()).writeBlockPos(this.getPos()));
		}

		@Override
		public ITextComponent getDisplayName() {
			return new StringTextComponent("FlipFlop Block");
		}

		@Override
		protected NonNullList<ItemStack> getItems() {
			return this.stacks;
		}

		@Override
		protected void setItems(NonNullList<ItemStack> stacks) {
			this.stacks = stacks;
		}
	}
}
