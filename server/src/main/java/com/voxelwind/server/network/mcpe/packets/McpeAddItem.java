package com.voxelwind.server.network.mcpe.packets;

import com.flowpowered.math.vector.Vector3f;
import com.voxelwind.api.game.item.ItemStack;
import com.voxelwind.server.network.NetworkPackage;
import com.voxelwind.server.network.mcpe.McpeUtil;
import io.netty.buffer.ByteBuf;
import lombok.Data;

@Data
public class McpeAddItem implements NetworkPackage {
    private long entityId;
    private ItemStack stack;
    private Vector3f position;
    private Vector3f velocity;

    @Override
    public void decode(ByteBuf buffer) {
        entityId = buffer.readLong();
        stack = McpeUtil.readItemStack(buffer);
        position = McpeUtil.readVector3f(buffer);
        velocity = McpeUtil.readVector3f(buffer);
    }

    @Override
    public void encode(ByteBuf buffer) {
        buffer.writeLong(entityId);
        McpeUtil.writeItemStack(buffer, stack);
        McpeUtil.writeVector3f(buffer, position);
        McpeUtil.writeVector3f(buffer, velocity);
    }
}
