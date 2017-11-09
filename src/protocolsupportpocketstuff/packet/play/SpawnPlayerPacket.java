package protocolsupportpocketstuff.packet.play;

import io.netty.buffer.ByteBuf;
import protocolsupport.api.Connection;
import protocolsupport.protocol.packet.middleimpl.clientbound.play.v_pe.EntityMetadata;
import protocolsupport.protocol.serializer.MiscSerializer;
import protocolsupport.protocol.serializer.StringSerializer;
import protocolsupport.protocol.serializer.VarNumberSerializer;
import protocolsupport.protocol.typeremapper.pe.PEPacketIDs;
import protocolsupport.protocol.utils.datawatcher.DataWatcherObject;
import protocolsupport.protocol.utils.i18n.I18NData;
import protocolsupport.utils.CollectionsUtils;
import protocolsupportpocketstuff.packet.PEPacket;

import java.util.UUID;

public class SpawnPlayerPacket extends PEPacket {
	private UUID uuid;
	private String name;
	private long entityId;
	private float x;
	private float y;
	private float z;
	private float motionX;
	private float motionY;
	private float motionZ;
	private float pitch;
	private float headYaw;
	private float yaw;
	private CollectionsUtils.ArrayMap<DataWatcherObject<?>> metadata;

	public SpawnPlayerPacket(UUID uuid, String name, long entityId, float x, float y, float z, float motionX, float motionY, float motionZ, float pitch, float headYaw, float yaw, CollectionsUtils.ArrayMap<DataWatcherObject<?>> metadata) {
		this.uuid = uuid;
		this.name = name;
		this.entityId = entityId;
		this.x = x;
		this.y = y;
		this.z = z;
		this.motionX = motionX;
		this.motionY = motionY;
		this.motionZ = motionZ;
		this.pitch = pitch;
		this.headYaw = headYaw;
		this.yaw = yaw;
		this.metadata = metadata;
	}

	@Override
	public int getPacketId() {
		return PEPacketIDs.SPAWN_PLAYER;
	}

	@Override
	public void toData(Connection connection, ByteBuf serializer) {
		MiscSerializer.writeUUID(serializer, connection.getVersion(), uuid);
		StringSerializer.writeString(serializer, connection.getVersion(), name);
		VarNumberSerializer.writeSVarLong(serializer, entityId); // entity ID
		VarNumberSerializer.writeVarLong(serializer, entityId); // runtime ID
		MiscSerializer.writeLFloat(serializer, x); // x
		MiscSerializer.writeLFloat(serializer, y); // y
		MiscSerializer.writeLFloat(serializer, z); // z
		MiscSerializer.writeLFloat(serializer, motionX); // motx
		MiscSerializer.writeLFloat(serializer, motionY); // moty
		MiscSerializer.writeLFloat(serializer, motionZ); // motz
		MiscSerializer.writeLFloat(serializer, pitch); // pitch
		MiscSerializer.writeLFloat(serializer, headYaw); // yaw
		MiscSerializer.writeLFloat(serializer, yaw); // yaw

		VarNumberSerializer.writeSVarInt(serializer, 0); // held item stack

		EntityMetadata.encodeMeta(serializer, connection.getVersion(), I18NData.DEFAULT_LOCALE, metadata);

		//adventure settings
		VarNumberSerializer.writeVarInt(serializer, 0);
		VarNumberSerializer.writeVarInt(serializer, 0);
		VarNumberSerializer.writeVarInt(serializer, 0);
		VarNumberSerializer.writeVarInt(serializer, 0);
		VarNumberSerializer.writeVarInt(serializer, 0);

		serializer.writeLongLE(0); //?

		VarNumberSerializer.writeSVarInt(serializer, 0); //links, not used
	}

	@Override
	public void readFromClientData(Connection connection, ByteBuf clientData) { }
}