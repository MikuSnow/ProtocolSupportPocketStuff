package protocolsupportpocketstuff.hacks.playerheads;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.commons.lang3.Validate;
import protocolsupport.api.Connection;
import protocolsupport.api.ProtocolVersion;
import protocolsupport.libs.com.google.gson.JsonObject;
import protocolsupport.protocol.serializer.ItemStackSerializer;
import protocolsupport.protocol.serializer.MiscSerializer;
import protocolsupport.protocol.serializer.PositionSerializer;
import protocolsupport.protocol.serializer.StringSerializer;
import protocolsupport.protocol.serializer.VarNumberSerializer;
import protocolsupport.protocol.typeremapper.pe.PEInventory;
import protocolsupport.protocol.typeremapper.pe.PEPacketIDs;
import protocolsupport.protocol.typeremapper.pe.PESkinModel;
import protocolsupport.protocol.utils.NBTTagCompoundSerializer;
import protocolsupport.protocol.utils.datawatcher.DataWatcherObject;
import protocolsupport.protocol.utils.datawatcher.objects.DataWatcherObjectFloatLe;
import protocolsupport.protocol.utils.i18n.I18NData;
import protocolsupport.protocol.utils.types.Position;
import protocolsupport.utils.CollectionsUtils;
import protocolsupport.zplatform.ServerPlatform;
import protocolsupport.zplatform.itemstack.ItemStackWrapper;
import protocolsupport.zplatform.itemstack.NBTTagCompoundWrapper;
import protocolsupport.zplatform.itemstack.NBTTagType;
import protocolsupportpocketstuff.ProtocolSupportPocketStuff;
import protocolsupportpocketstuff.api.util.PocketCon;
import protocolsupportpocketstuff.packet.TileDataUpdatePacket;
import protocolsupportpocketstuff.packet.play.EntityDestroyPacket;
import protocolsupportpocketstuff.packet.play.SkinPacket;
import protocolsupportpocketstuff.packet.play.SpawnPlayerPacket;
import protocolsupportpocketstuff.storage.Skins;
import protocolsupportpocketstuff.util.StuffUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.SplittableRandom;
import java.util.UUID;

public class PlayerHeadsPacketListener extends Connection.PacketListener {
	private ProtocolSupportPocketStuff plugin;
	private Connection con;
	private boolean isSpawned = false;
	private final HashMap<Long, CachedSkullBlock> cachedSkullBlocks = new HashMap<>();
	private final HashMap<Long, UUID> entityIdToUuid = new HashMap<>();
	private final HashSet<Long> hasCustomSkull = new HashSet<>();
	private boolean selfEquippedCustomSkull = false;
	private static final String SKULL_MODEL = StuffUtils.getResourceAsString("models/fake_skull_block.json");

	// Constants
	private static final int SKULL_BLOCK_ID = 144;
	private static final int SKULL_ITEM_ID = 397;
	private static final int ARMOR_WINDOW_ID = PEInventory.PESource.POCKET_ARMOR_EQUIPMENT;

	public PlayerHeadsPacketListener(ProtocolSupportPocketStuff plugin, Connection con) {
		this.plugin = plugin;
		this.con = con;
	}

	@Override
	public void onRawPacketReceiving(RawPacketEvent event) {
		ByteBuf data = event.getData();
		int packetId = VarNumberSerializer.readVarInt(data);

		data.readByte();
		data.readByte();

		if (packetId == PEPacketIDs.PLAYER_MOVE) {
			if (isSpawned)
				return;

			isSpawned = true;

			for (CachedSkullBlock cachedSkullBlock : cachedSkullBlocks.values()) {
				if (cachedSkullBlock.isCustomSkull()) {
					cachedSkullBlock.spawn(this);
				}
			}
			return;
		}
	}

	@Override
	public void onRawPacketSending(RawPacketEvent event) {
		ByteBuf data = event.getData();
		int packetId = VarNumberSerializer.readVarInt(data);

		data.readByte();
		data.readByte();

		if (packetId == PEPacketIDs.SPAWN_PLAYER) {
			UUID uuid = MiscSerializer.readUUID(data);
			StringSerializer.readString(data, ProtocolVersion.MINECRAFT_PE); // player username
			long entityId = VarNumberSerializer.readSVarLong(data);
			entityIdToUuid.put(entityId, uuid);
			return;
		}
		if (packetId == PEPacketIDs.CHANGE_DIMENSION) {
			cachedSkullBlocks.clear();
			return;
		}
		if (packetId == PEPacketIDs.INVENTORY_CONTENT) {
			int windowId = VarNumberSerializer.readVarInt(data);
			int length = VarNumberSerializer.readVarInt(data); // length

			if (windowId != ARMOR_WINDOW_ID) {
				return;
			}

			ItemStackWrapper itemStack = ItemStackSerializer.readItemStack(data, con.getVersion(), I18NData.DEFAULT_LOCALE, false); // Yes, it is from the client, but we don't want it to remap our stuff

			if (itemStack.isNull() || itemStack.getTypeId() != SKULL_ITEM_ID) {
				if (selfEquippedCustomSkull) {
					selfEquippedCustomSkull = false;
					sendOriginalSkin(con.getPlayer().getUniqueId(), true);
				}
				return;
			}


			NBTTagCompoundWrapper tag = itemStack.getTag();

			String url = getUrlFromSkull(tag, true);

			selfEquippedCustomSkull = true;

			ByteBuf serializer = Unpooled.buffer();
			VarNumberSerializer.writeVarInt(serializer, PEPacketIDs.INVENTORY_CONTENT);
			serializer.writeByte(0);
			serializer.writeByte(0);
			VarNumberSerializer.writeVarInt(serializer, windowId);
			VarNumberSerializer.writeVarInt(serializer, length);
			ItemStackSerializer.writeItemStack(serializer, con.getVersion(), I18NData.DEFAULT_LOCALE, ServerPlatform.get().getWrapperFactory().createItemStack(421), false);
			serializer.writeBytes(MiscSerializer.readBytes(serializer, serializer.readableBytes()));
			event.setData(serializer);
			new Thread() {
				public void run() {
					if (Skins.INSTANCE.hasPeSkin(url)) {
						sendSkullSkin(Skins.INSTANCE.getPeSkin(url), con.getPlayer().getUniqueId(), true);
					} else {
						try {
							BufferedImage image = ImageIO.read(new URL(url));
							byte[] data = toData(image);
							sendSkullSkin(data, con.getPlayer().getUniqueId(), true);
							Skins.INSTANCE.cachePeSkin(url, data);
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
			}.start();
			return;
		}
		if (packetId == PEPacketIDs.MOB_ARMOR_EQUIPMENT) {
			long entityId = VarNumberSerializer.readVarLong(data);

			if (!entityIdToUuid.containsKey(entityId))
				return;

			ItemStackWrapper itemStack = ItemStackSerializer.readItemStack(data, con.getVersion(), I18NData.DEFAULT_LOCALE, false); // Yes, it is from the client, but we don't want it to remap our stuff

			if (itemStack.isNull() || itemStack.getTypeId() != SKULL_ITEM_ID) {
				if (hasCustomSkull.contains(entityId)) {
					hasCustomSkull.remove(entityId);
					sendOriginalSkin(entityIdToUuid.get(entityId), false);
				}
				return;
			}

			NBTTagCompoundWrapper tag = itemStack.getTag();

			String url = getUrlFromSkull(tag, true);

			hasCustomSkull.add(entityId);
			new Thread() {
				public void run() {
					UUID uuid = entityIdToUuid.get(entityId);
					if (Skins.INSTANCE.hasPeSkin(url)) {
						sendSkullSkin(Skins.INSTANCE.getPeSkin(url), uuid, false);
					} else {
						try {
							BufferedImage image = ImageIO.read(new URL(url));
							byte[] data = toData(image);
							sendSkullSkin(data, uuid, false);
							Skins.INSTANCE.cachePeSkin(url, data);
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
			}.start();
			return;
		}
		if (packetId == PEPacketIDs.TILE_DATA_UPDATE) {
			Position position = PositionSerializer.readPEPosition(data);
			try {
				NBTTagCompoundWrapper tag = NBTTagCompoundSerializer.readPeTag(data, true);

				if (!isSkull(tag))
					return;

				handleSkull(position, tag, -1);

				tag.remove("Owner"); // remove the owner tag, if we don't remove it, the skull stays with the default parameters (skeleton with rot 0)
				event.setData(new TileDataUpdatePacket(position.getX(), position.getY(), position.getZ(), tag).encode(con));
			} catch (Exception e) {
				e.printStackTrace();
			}
			return;
		}
		if (packetId == PEPacketIDs.UPDATE_BLOCK) {
			Position position = PositionSerializer.readPEPosition(data);
			int id = VarNumberSerializer.readVarInt(data);
			int flagsAndDataValue = VarNumberSerializer.readVarInt(data);

			long asLong = position.asLong();

			int flags = 0;

			if ((flagsAndDataValue & 0x01) != 0) {
				flags += 0x01;
			}
			if ((flagsAndDataValue & 0x02) != 0) {
				flags += 0x02;
			}
			if ((flagsAndDataValue & 0x04) != 0) {
				flags += 0x04;
			}
			if ((flagsAndDataValue & 0x08) != 0) {
				flags += 0x08;
			}

			int dataValue = flags & flagsAndDataValue;

			if (id == SKULL_BLOCK_ID) {
				if (!cachedSkullBlocks.containsKey(asLong)) {
					CachedSkullBlock cachedSkullBlock = new CachedSkullBlock(position);
					cachedSkullBlock.dataValue = dataValue; // Store the new dataValue from this block
					cachedSkullBlocks.put(asLong, cachedSkullBlock);
					return;
				}

				CachedSkullBlock cachedSkullBlock = cachedSkullBlocks.get(asLong);
				if (cachedSkullBlock.dataValue != dataValue) { // We only need to respawn the block if the data value has changed
					cachedSkullBlock.dataValue = dataValue;
					cachedSkullBlock.destroy(this);
					cachedSkullBlock.spawn(this);
				}
				return;
			}

			if (cachedSkullBlocks.containsKey(asLong)) {
				cachedSkullBlocks.get(asLong).destroy(this);
				cachedSkullBlocks.remove(asLong);
			}
			return;
		}
		if (packetId == PEPacketIDs.CHUNK_DATA) {
			int chunkX = VarNumberSerializer.readSVarInt(data); // chunk X
			int chunkZ = VarNumberSerializer.readSVarInt(data); // chunk Z
			VarNumberSerializer.readVarInt(data); // length
			int sectionLength = data.readByte();

			int chunkXStart = chunkX << 4;
			int chunkZStart = chunkZ << 4;

			HashMap<Long, Integer> skulls = new HashMap<>();

			for (int idx = 0; sectionLength > idx; idx++) {
				data.readByte(); // storage type
				for (int x = 0; x < 16; x++) {
					for (int z = 0; z < 16; z++) {
						for (int y = 0; y < 16; y++) {
							int id = data.readUnsignedByte();

							if (id == SKULL_BLOCK_ID) {
								skulls.put(StuffUtils.convertCoordinatesToLong(chunkXStart + x, (idx * 16) + y, chunkZStart + z), -1);
							}
						}
					}
				}
				for (int x = 0; x < 16; x++) {
					for (int z = 0; z < 16; z++) {
						for (int y = 0; y < 16; y += 2) {
							long position = StuffUtils.convertCoordinatesToLong(chunkXStart + x, (idx * 16) + y, chunkZStart + z);
							long positionAbove = StuffUtils.convertCoordinatesToLong(chunkXStart + x, (idx * 16) + y + 1, chunkZStart + z);
							int state = data.readUnsignedByte();

							int dataValueAbove = state >> 4;
							int dataValue = state & 0x0F;

							if (skulls.containsKey(position)) {
								skulls.put(position, dataValue);
							}
							if (skulls.containsKey(positionAbove)) {
								skulls.put(positionAbove, dataValueAbove);
							}
						}
					}
				}
			}

			data.skipBytes(512); // heights
			data.skipBytes(256); // biomes
			data.readByte(); // borders
			VarNumberSerializer.readSVarInt(data); // extra data
			while (data.readableBytes() != 0) {
				NBTTagCompoundWrapper tag = ItemStackSerializer.readTag(data, true, con.getVersion());

				if (!isSkull(tag))
					continue;

				int x = tag.getIntNumber("x");
				int y = tag.getIntNumber("y");
				int z = tag.getIntNumber("z");

				Position position = new Position(x, y, z);

				// Is there's any possibility of an skull being on the chunk nbt tags but not really in the world? idk
				// So that's why getOrDefault is used
				handleSkull(position, tag, skulls.getOrDefault(position.asLong(), 1));
			}
		}
	}

	public void sendSkullSkin(byte[] skullSkin, UUID uuid, boolean self) {
		byte[] originalSkin = Skins.INSTANCE.getSkinFromUUID(uuid).clone();
		System.arraycopy(skullSkin, 0, originalSkin, 0, 1024 * 4);

		PESkinModel model = PESkinModel.getSkinModel(false);

		PocketCon.sendPocketPacket(con, new SkinPacket(
				self ? PocketCon.getClientUniqueId(con) : uuid,
				model.getSkinId(),
				model.getSkinName(),
				"Steve",
				originalSkin,
				new byte[0],
				model.getGeometryId(),
				model.getGeometryData()
		));

	}

	public void sendOriginalSkin(UUID uuid, boolean self) {
		PESkinModel model = PESkinModel.getSkinModel(false);

		byte[] originalSkin = Skins.INSTANCE.getSkinFromUUID(uuid).clone();

		PocketCon.sendPocketPacket(con, new SkinPacket(
				self ? PocketCon.getClientUniqueId(con) : uuid,
				model.getSkinId(),
				model.getSkinName(),
				"Steve",
				originalSkin,
				new byte[0],
				model.getGeometryId(),
				model.getGeometryData()
		));
	}

	public boolean isSkull(NBTTagCompoundWrapper tag) {
		return tag.getString("id").equals("Skull");
	}

	public String getUrlFromSkull(NBTTagCompoundWrapper tag, boolean isItem) {
		if (!tag.hasKeyOfType(isItem ? "SkullOwner" : "Owner", NBTTagType.COMPOUND))
			return null;

		NBTTagCompoundWrapper owner = tag.getCompound(isItem ? "SkullOwner" : "Owner");

		if (!owner.hasKeyOfType("Properties", NBTTagType.COMPOUND))
			return null;

		NBTTagCompoundWrapper properties = owner.getCompound("Properties");

		if (!properties.hasKeyOfType("textures", NBTTagType.LIST))
			return null;

		String value = properties.getList("textures").getCompound(0).getString("Value");

		String _json = new String(Base64.getDecoder().decode(value));

		JsonObject json = StuffUtils.JSON_PARSER.parse(_json).getAsJsonObject();

		return json.get("textures").getAsJsonObject().get("SKIN").getAsJsonObject().get("url").getAsString();
	}

	public void handleSkull(Position position, NBTTagCompoundWrapper tag, int dataValue) {
		if (position == null && tag == null) {
			throw new RuntimeException("Both Position and NBTTagCompoundWrapper are null!");
		}
		if (position == null) {
			int x = tag.getIntNumber("x");
			int y = tag.getIntNumber("y");
			int z = tag.getIntNumber("z");

			position = new Position(x, y, z);
		}

		CachedSkullBlock cachedSkullBlock = cachedSkullBlocks.getOrDefault(position.asLong(), new CachedSkullBlock(position));
		if (dataValue != -1)
			cachedSkullBlock.dataValue = dataValue;

		if (tag != null) {
			String url = getUrlFromSkull(tag, false);

			if (url != null) {
				cachedSkullBlock.url = url;
				cachedSkullBlock.tag = tag;
			}
		}

		cachedSkullBlocks.put(position.asLong(), cachedSkullBlock);

		if (!isSpawned)
			return;

		if (!cachedSkullBlock.isCustomSkull())
			return;

		if (cachedSkullBlock.isSpawned) {
			cachedSkullBlock.destroy(this);
		}

		cachedSkullBlock.spawn(this);
	}

	protected static byte[] toData(BufferedImage skin) {
		Validate.isTrue(skin.getWidth() == 64, "Must be 64 pixels wide");
		Validate.isTrue((skin.getHeight() == 64) || (skin.getHeight() == 32), "Must be 64 or 32 pixels high");
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		for (int y = 0; y < skin.getHeight(); y++) {
			for (int x = 0; x < skin.getWidth(); x++) {
				Color color = new Color(skin.getRGB(x, y), true);
				stream.write(color.getRed());
				stream.write(color.getGreen());
				stream.write(color.getBlue());
				stream.write(color.getAlpha());
			}
		}
		return stream.toByteArray();
	}

	static class CachedSkullBlock {
		private long entityId = new SplittableRandom().nextLong(Integer.MAX_VALUE, Long.MAX_VALUE);
		private Position position;
		private NBTTagCompoundWrapper tag;
		private String url;
		private int dataValue = 1;
		private boolean isSpawned = false;

		public CachedSkullBlock(Position position) {
			this.position = position;
		}

		public boolean isCustomSkull() {
			return tag != null && url != null;
		}

		public long getEntityId() {
			return entityId;
		}

		public void spawn(PlayerHeadsPacketListener listener) {
			isSpawned = true;

			if (Skins.INSTANCE.hasPeSkin(url)) {
				sendFakePlayer(listener, url, Skins.INSTANCE.getPeSkin(url));
			} else {
				new Thread() {
					public void run() {
						try {
							BufferedImage image = ImageIO.read(new URL(url));
							byte[] data = toData(image);
							sendFakePlayer(listener, url, data);
							Skins.INSTANCE.cachePeSkin(url, data);
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}.start();
			}
		}

		private void sendFakePlayer(PlayerHeadsPacketListener listener, String url, byte[] data) {
			int x = tag.getIntNumber("x");
			int y = tag.getIntNumber("y");
			int z = tag.getIntNumber("z");

			CollectionsUtils.ArrayMap<DataWatcherObject<?>> metadata = new CollectionsUtils.ArrayMap<>(76);

			metadata.put(39, new DataWatcherObjectFloatLe(1.05f)); // scale, needs to be a *bit* bigger than the original skull
			metadata.put(54, new DataWatcherObjectFloatLe(0.001f)); // bb width
			metadata.put(55, new DataWatcherObjectFloatLe(0.001f)); // bb height

			UUID uuid = UUID.randomUUID();

			float yaw = 0;
			float xOffset = 0.5F;
			float yOffset = 0.0F;
			float zOffset = 0.5F;

			if (dataValue == 1) { // on ground
				int rot = tag.getByteNumber("Rot");
				switch (rot) {
					case 0:
						yaw = 180F;
						break;
					case 1:
						yaw = 202.5F;
						break;
					case 2:
						yaw = 225F;
						break;
					case 3:
						yaw = 247.5F;
						break;
					case 4:
						yaw = 270F;
						break;
					case 5:
						yaw = 292.5F;
						break;
					case 6:
						yaw = 315F;
						break;
					case 7:
						yaw = 337.5F;
						break;
					case 8:
						yaw = 0F;
						break;
					case 9:
						yaw = 22.5F;
						break;
					case 10:
						yaw = 45F;
						break;
					case 11:
						yaw = 67.5F;
						break;
					case 12:
						yaw = 90F;
						break;
					case 13:
						yaw = 112.5F;
						break;
					case 14:
						yaw = 135F;
						break;
					case 15:
						yaw = 157.5F;
						break;
				}
			} else { // on walls
				yOffset = 0.25F;

				if (dataValue == 2) {
					zOffset = 0.75F;
					yaw = 180;
				}
				if (dataValue == 3) {
					zOffset = 0.25F;
					yaw = 0;
				}
				if (dataValue == 4) {
					xOffset = 0.75F;
					yaw = 90;
				}
				if (dataValue == 5) {
					xOffset = 0.25F;
					yaw = 270;
				}
			}
			SpawnPlayerPacket packet = new SpawnPlayerPacket(
					uuid,
					"",
					entityId,
					x + xOffset, y + yOffset, (float) z + zOffset, // coordinates
					0, 0, 0, // motion
					0, 0, yaw, // pitch, head yaw & yaw
					metadata
			);

			PocketCon.sendPocketPacket(listener.con, packet);
			PocketCon.sendPocketPacket(listener.con, new SkinPacket(
					uuid,
					"d5c91b67-3d30-4aee-b99f-859542f02ea9_Skull",
					"geometry.Tiles.PSPEFakeSkull",
					"Steve",
					data,
					new byte[0],
					"geometry.Tiles.PSPEFakeSkull",
					SKULL_MODEL
			));

			TileDataUpdatePacket tileDataUpdatePacket = new TileDataUpdatePacket(x, y, z, tag);
			PocketCon.sendPocketPacket(listener.con, tileDataUpdatePacket);
		}

		public void destroy(PlayerHeadsPacketListener listener) {
			isSpawned = false;
			PocketCon.sendPocketPacket(listener.con, new EntityDestroyPacket(entityId));
		}
	}
}
