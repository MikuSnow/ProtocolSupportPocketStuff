package protocolsupportpocketstuff.packet.play;

import io.netty.buffer.ByteBuf;
import protocolsupport.api.Connection;
import protocolsupport.protocol.packet.middleimpl.clientbound.play.v_pe.EntityMetadata;
import protocolsupport.protocol.serializer.MiscSerializer;
import protocolsupport.protocol.serializer.VarNumberSerializer;
import protocolsupport.protocol.typeremapper.pe.PEPacketIDs;
import protocolsupport.protocol.utils.datawatcher.DataWatcherObject;
import protocolsupport.protocol.utils.i18n.I18NData;
import protocolsupport.utils.CollectionsUtils;
import protocolsupportpocketstuff.packet.PEPacket;

import java.util.List;

public class SpawnEntityPacket extends PEPacket {
	private long entityId;
	private int entityType;
	private float x;
	private float y;
	private float z;
	private float motionX;
	private float motionY;
	private float motionZ;
	private float pitch;
	private float yaw;
	private List<SetAttributesPacket.Attribute> attributes;
	private CollectionsUtils.ArrayMap<DataWatcherObject<?>> metadata;

	public SpawnEntityPacket(long entityId, int entityType, float x, float y, float z, float motionX, float motionY, float motionZ, float pitch, float yaw, List<SetAttributesPacket.Attribute> attributes, CollectionsUtils.ArrayMap<DataWatcherObject<?>> metadata) {
		this.entityId = entityId;
		this.entityType = entityType;
		this.x = x;
		this.y = y;
		this.z = z;
		this.motionX = motionX;
		this.motionY = motionY;
		this.motionZ = motionZ;
		this.pitch = pitch;
		this.yaw = yaw;
		this.attributes = attributes;
		this.metadata = metadata;
	}

	@Override
	public int getPacketId() {
		return PEPacketIDs.SPAWN_ENTITY;
	}

	@Override
	public void toData(Connection connection, ByteBuf serializer) {
		VarNumberSerializer.writeSVarLong(serializer, entityId); // entity ID
		VarNumberSerializer.writeVarLong(serializer, entityId); // runtime ID
		VarNumberSerializer.writeVarInt(serializer, entityType); // boss bar entity id
		MiscSerializer.writeLFloat(serializer, x); // x
		MiscSerializer.writeLFloat(serializer, y); // y
		MiscSerializer.writeLFloat(serializer, z); // z
		MiscSerializer.writeLFloat(serializer, motionX); // motx
		MiscSerializer.writeLFloat(serializer, motionY); // moty
		MiscSerializer.writeLFloat(serializer, motionZ); // motz
		MiscSerializer.writeLFloat(serializer, pitch); // pitch
		MiscSerializer.writeLFloat(serializer, yaw); // yaw

		SetAttributesPacket.encodeAttributes(connection, serializer, attributes);

		EntityMetadata.encodeMeta(serializer, connection.getVersion(), I18NData.DEFAULT_LOCALE, metadata);

		VarNumberSerializer.writeVarInt(serializer, 0); //links, not used
	}

	@Override
	public void readFromClientData(Connection connection, ByteBuf clientData) { }
}