package com.voxelwind.server.game.level.manager;

import com.flowpowered.math.vector.Vector3f;
import com.google.common.collect.ImmutableList;
import com.voxelwind.server.game.level.VoxelwindLevel;
import com.voxelwind.server.game.entities.BaseEntity;
import com.voxelwind.api.game.entities.Entity;
import com.voxelwind.server.game.level.util.BoundingBox;
import com.voxelwind.server.network.mcpe.packets.McpeMoveEntity;
import com.voxelwind.server.network.mcpe.packets.McpeSetEntityMotion;
import com.voxelwind.server.network.session.McpeSession;
import com.voxelwind.server.network.session.PlayerSession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This manager handles entities in levels. This class is safe for concurrent use.
 */
public class LevelEntityManager {
    private static final Logger LOGGER = LogManager.getLogger(LevelEntityManager.class);

    private final List<BaseEntity> entities = new ArrayList<>();
    private final Object entityLock = new Object();
    private final AtomicLong entityIdAllocator = new AtomicLong();
    private final AtomicBoolean entitiesAdded = new AtomicBoolean(false);
    private final VoxelwindLevel level;

    public LevelEntityManager(VoxelwindLevel level) {
        this.level = level;
    }

    public void register(BaseEntity entity) {
        synchronized (entityLock) {
            entities.add(entity);
        }
        entitiesAdded.set(true);
    }

    public void onTick() {
        List<BaseEntity> currentEntityList;
        synchronized (entityLock) {
            currentEntityList = ImmutableList.copyOf(entities);
        }

        List<BaseEntity> toRemove = new ArrayList<>();
        for (BaseEntity entity : currentEntityList) {
            try {
                if (!entity.onTick()) {
                    // Entity should be despawned
                    toRemove.add(entity);
                    continue;
                }

                if (entity.isStale()) {
                    // Need to send packets.
                    McpeMoveEntity moveEntityPacket = new McpeMoveEntity();
                    moveEntityPacket.setEntityId(entity.getEntityId());
                    moveEntityPacket.setPosition(entity.getGamePosition());
                    moveEntityPacket.setRotation(entity.getRotation());
                    level.getPacketManager().queuePacketForViewers(entity, moveEntityPacket);

                    McpeSetEntityMotion motionPacket = new McpeSetEntityMotion();
                    motionPacket.getMotionList().add(new McpeSetEntityMotion.EntityMotion(entity.getEntityId(), entity.getMotion()));
                    level.getPacketManager().queuePacketForViewers(entity, motionPacket);

                    entity.resetStale();
                }
            } catch (Exception e) {
                LOGGER.error("Unable to tick entity", e);
                toRemove.add(entity);
            }
        }

        synchronized (entityLock) {
            entities.removeAll(toRemove);
        }

        if (!toRemove.isEmpty()) {
            for (Entity entity : toRemove) {
                if (entity instanceof PlayerSession) {
                    // The player should already be disconnected.
                    continue;
                }

                // If the entity isn't already removed, do it now.
                if (!entity.isRemoved()) {
                    entity.remove();
                }
            }
        }

        // Perform a view check for all players.
        if (entitiesAdded.getAndSet(false) || !toRemove.isEmpty()) {
            for (PlayerSession session : getPlayers()) {
                session.updateViewableEntities();
            }
        }
    }

    public List<PlayerSession> getPlayers() {
        List<BaseEntity> currentEntityList;
        synchronized (entityLock) {
            currentEntityList = ImmutableList.copyOf(entities);
        }

        List<PlayerSession> sessions = new ArrayList<>();

        for (BaseEntity entity : currentEntityList) {
            if (entity instanceof PlayerSession) {
                PlayerSession session = (PlayerSession) entity;
                McpeSession mcpeSession = session.getMcpeSession();
                if (mcpeSession != null && !mcpeSession.isClosed()) {
                    sessions.add((PlayerSession) entity);
                }
            }
        }

        return sessions;
    }

    public List<Entity> getEntitiesInChunk(int x, int z) {
        List<BaseEntity> currentEntityList;
        synchronized (entityLock) {
            currentEntityList = ImmutableList.copyOf(entities);
        }

        List<Entity> entities = new ArrayList<>();

        for (BaseEntity entity : currentEntityList) {
            int entityChunkX = entity.getPosition().getFloorX() >> 4;
            int entityChunkZ = entity.getPosition().getFloorZ() >> 4;

            if (!entity.isRemoved() && entityChunkX == x && entityChunkZ == z) {
                entities.add(entity);
            }
        }

        return entities;
    }

    public long allocateEntityId() {
        return entityIdAllocator.incrementAndGet();
    }

    public Collection<BaseEntity> getAllEntities() {
        synchronized (entityLock) {
            return ImmutableList.copyOf(entities);
        }
    }

    public Optional<BaseEntity> findEntityById(long id) {
        synchronized (entityLock) {
            for (BaseEntity entity : entities) {
                if (entity.isRemoved()) {
                    continue;
                }

                if (entity.getEntityId() == id) {
                    return Optional.of(entity);
                }
            }
            return Optional.empty();
        }
    }

    public void unregister(BaseEntity entity) {
        synchronized (entityLock) {
            entities.remove(entity);
        }
    }

    public Collection<BaseEntity> getEntitiesInDistance(Vector3f origin, double distance) {
        Collection<BaseEntity> inDistance = new ArrayList<>();
        synchronized (entityLock) {
            for (BaseEntity entity : entities) {
                if (entity.isRemoved()) {
                    continue;
                }

                if (entity.getPosition().distance(origin) <= distance) {
                    inDistance.add(entity);
                }
            }
        }
        return inDistance;
    }

    public Collection<BaseEntity> getEntitiesInBounds(BoundingBox boundingBox) {
        Collection<BaseEntity> inDistance = new ArrayList<>();
        synchronized (entityLock) {
            for (BaseEntity entity : entities) {
                if (entity.isRemoved()) {
                    continue;
                }

                if (boundingBox.isWithin(entity.getPosition())) {
                    inDistance.add(entity);
                }
            }
        }
        return inDistance;
    }
}
