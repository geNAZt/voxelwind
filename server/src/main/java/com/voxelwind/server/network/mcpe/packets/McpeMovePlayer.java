package com.voxelwind.server.network.mcpe.packets;

import com.flowpowered.math.vector.Vector3f;
import com.voxelwind.server.network.mcpe.McpeUtil;
import com.voxelwind.server.network.NetworkPackage;
import com.voxelwind.api.util.Rotation;
import io.netty.buffer.ByteBuf;
import lombok.Data;

@Data
public class McpeMovePlayer implements NetworkPackage {
    private long entityId;
    private Vector3f position;
    private Rotation rotation;
    private boolean mode;
    private boolean onGround;

    @Override
    public void decode(ByteBuf buffer) {
        entityId = McpeUtil.readSignedVarInt(buffer);
        position = McpeUtil.readVector3f(buffer);
        rotation = McpeUtil.readRotation(buffer);
        mode = buffer.readBoolean();
        // TODO: Byte here?!?
        onGround = buffer.readBoolean();
    }

    @Override
    public void encode(ByteBuf buffer) {
        McpeUtil.writeSignedVarInt(buffer, (int) entityId);
        McpeUtil.writeVector3f(buffer, position);
        McpeUtil.writeRotation(buffer, rotation);
        buffer.writeBoolean(mode);
        buffer.writeBoolean(onGround);
    }
}
