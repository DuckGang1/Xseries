package com.cryptomorin.xseries;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;

import static com.cryptomorin.xseries.ReflectionUtils.*;

/**
 * Send different <a href="https://minecraft.fandom.com/wiki/World_border">World Border</a> data to each player.
 *
 * @version 1.0.0
 */
public class XWorldBorder implements Cloneable {
    private static final MethodHandle WORLD_HANDLE, WORLDBORDER, WORLDBORDER_WORLD, CENTER, WARNING_DISTANCE, WARNING_TIME, SIZE, TRANSITION;
    private static final MethodHandle PACKET_WARNING_DISTANCE, PACKET_WARNING_DELAY, PACKET_LERP_SIZE, PACKET_INIT, PACKET_CENTER, PACKET_SIZE;
    private static final Object INITIALIZE;
    public static final double MAX_SIZE = 5.9999968E7D;
    public static final double MAX_CENTER_COORDINATE = 2.9999984E7D;
    private static final boolean SUPPORTS_SEPARATE_PACKETS;
    private static final Map<UUID, XWorldBorder> WORLD_BORDERS = new HashMap<>();

    private Object handle;
    public int absoluteMaxSize = 29999984;
    private double damagePerBlock = 0.2D;
    private double damageSafeZone = 5.0D;
    private double size = 100;
    private double sizeLerpTarget = 0;
    private BorderBounds borderBounds;
    private Duration warningTime = Duration.ofSeconds(15);
    private Duration sizeLerpTime = Duration.ZERO;
    private int warningBlocks = 5;
    private World world;
    private double centerX, centerZ;
    private final Set<Component> updateRequired = EnumSet.noneOf(Component.class);
    private UUID player;
    private boolean init = true;

    private XWorldBorder() {
    }

    @SuppressWarnings("unused")
    public static final class Events implements Listener {
        @EventHandler
        public void onJoin(PlayerMoveEvent event) {
            XWorldBorder wb = get(event.getPlayer());
            if (wb == null) return;
            Player p = event.getPlayer();
            Vector loc = p.getLocation().toVector();
            if (wb.isWithinBorder(loc)) return;

            double distance = wb.getDistanceToBorder(loc);
            if (distance < wb.damageSafeZone) return;
            p.damage(wb.damagePerBlock * distance); // Should be per second.
        }

        @EventHandler
        public void onJoin(PlayerJoinEvent event) {
            XWorldBorder wb = get(event.getPlayer());
            if (wb == null) return;
            wb.send(true);
        }

        @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
        public void onWorldChange(PlayerChangedWorldEvent event) {
            XWorldBorder wb = get(event.getPlayer());
            if (wb == null) return;
            wb.send(true);
        }
    }

    public static XWorldBorder getOrCreate(Player player) {
        XWorldBorder wb = get(player);
        if (wb != null) return wb;
        return XWorldBorder.of(player.getLocation()).setPlayer(player);
    }

    public static XWorldBorder get(Player player) {
        return WORLD_BORDERS.get(player.getUniqueId());
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    @Override
    public XWorldBorder clone() {
        XWorldBorder wb = new XWorldBorder();
        wb.world = world;
        wb.centerX = centerX;
        wb.centerZ = centerZ;
        wb.size = size;
        wb.sizeLerpTime = sizeLerpTime;
        wb.damagePerBlock = damagePerBlock;
        wb.damageSafeZone = damageSafeZone;
        wb.warningTime = warningTime;
        wb.warningBlocks = warningBlocks;
        wb.handle = wb.createHandle();
        wb.player = player;
        return wb;
    }

    public static XWorldBorder from(WorldBorder bukkitWb) {
        XWorldBorder wb = new XWorldBorder();
        wb.world = bukkitWb.getWorld();
        wb.centerX = bukkitWb.getCenter().getX();
        wb.centerZ = bukkitWb.getCenter().getZ();
        wb.size = bukkitWb.getSize();
        wb.sizeLerpTime = Duration.ZERO;
        wb.damagePerBlock = bukkitWb.getDamageAmount();
        wb.damageSafeZone = bukkitWb.getDamageBuffer();
        wb.warningTime = Duration.ofSeconds(bukkitWb.getWarningTime());
        wb.warningBlocks = bukkitWb.getWarningDistance();
        wb.handle = wb.createHandle();
        return wb;
    }

    @Nullable
    public UUID getPlayerId() {
        return player;
    }

    @Nullable
    public Player getPlayer() {
        return Bukkit.getPlayer(Objects.requireNonNull(player, "No player provided"));
    }

    public static XWorldBorder of(Location center) {
        XWorldBorder wb = new XWorldBorder();
        wb.world = Objects.requireNonNull(center.getWorld());
        wb.centerX = center.getX();
        wb.centerZ = center.getZ();
        wb.handle = wb.createHandle();
        wb.update(Component.CENTER);
        return wb;
    }

    public XWorldBorder setDamageAmount(double damage) {
        damagePerBlock = damage;
        return this;
    }

    public double getDamageAmount() {
        return damagePerBlock;
    }

    public XWorldBorder setDamageBuffer(double blocks) {
        damageSafeZone = blocks;
        return this;
    }

    public double getDamageBuffer() {
        return damageSafeZone;
    }

    public XWorldBorder setWarningTime(Duration time) {
        if (this.warningTime == time) return this;
        warningTime = time;
        update(Component.WARNING_DELAY);
        return this;
    }

    public Duration getWarningTime() {
        return warningTime;
    }

    public XWorldBorder setWarningDistance(int blocks) {
        if (warningBlocks == blocks) return this;
        warningBlocks = blocks;
        update(Component.WARNING_DISTANCE);
        return this;
    }

    public double getSizeLerpTarget() {
        return sizeLerpTarget;
    }

    public XWorldBorder setSizeLerpTarget(double sizeLerpTarget) {
        if (this.sizeLerpTarget == sizeLerpTarget) return this;
        this.sizeLerpTarget = sizeLerpTarget;
        update(Component.SIZE_LERP);
        return this;
    }

    public int getWarningDistance() {
        return warningBlocks;
    }

    public XWorldBorder setCenter(double x, double z) {
        if (centerX == x && centerZ == z) return this;
        centerX = x;
        centerZ = z;

        updateBorderBounds();
        update(Component.CENTER);

        return this;
    }

    public Vector getCenter() {
        return new Vector(centerX, 0, centerZ);
    }

    public XWorldBorder setSize(double newSize, @Nonnull Duration duration) {
        if (this.size == newSize && sizeLerpTime == duration) return this;
        size = newSize;
        sizeLerpTime = duration;

        updateBorderBounds();
        update(Component.SIZE);
        if (Duration.ZERO != duration) update(Component.SIZE_LERP);

        return this;
    }

    private void updateBorderBounds() {
        if (size <= 0) this.borderBounds = null;
        else this.borderBounds = new BorderBounds();
    }

    private void update(Component comp) {
        if (SUPPORTS_SEPARATE_PACKETS) updateRequired.add(comp);
    }

    public boolean isWithinBorder(Vector location) {
        if (this.borderBounds == null) return false;
        return (location.getX() + 1) > borderBounds.minX &&
                location.getX() < borderBounds.maxX &&
                (location.getZ() + 1) > borderBounds.minZ &&
                location.getZ() < borderBounds.maxZ;
    }

    public double getDistanceToBorder(Vector location) {
        if (this.borderBounds == null) {
            return getCenter().distanceSquared(location);
        }
        double x = location.getX();
        double z = location.getZ();

        double d2 = z - borderBounds.minZ;
        double d3 = borderBounds.maxZ - z;
        double d4 = x - borderBounds.minX;
        double d5 = borderBounds.maxX - x;
        double d6 = Math.min(d4, d5);

        d6 = Math.min(d6, d2);
        return Math.min(d6, d3);
    }

    private final class BorderBounds {
        public final double minX, minZ, maxX, maxZ;

        private double clamp(double var0, double var2, double var4) {
            return var0 < var2 ? var2 : Math.min(var0, var4);
        }

        public BorderBounds() {
            this.minX = clamp(centerX - size / 2.0D, -absoluteMaxSize, absoluteMaxSize);
            this.minZ = clamp(centerZ - size / 2.0D, -absoluteMaxSize, absoluteMaxSize);
            this.maxX = clamp(centerX + size / 2.0D, -absoluteMaxSize, absoluteMaxSize);
            this.maxZ = clamp(centerZ + size / 2.0D, -absoluteMaxSize, absoluteMaxSize);
        }
    }

    static {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Object initialize = null;
        MethodHandle world = null, worldborder = null, worldborderWorld = null,
                center = null, distance = null, warnTime = null, size = null, transition = null;

        MethodHandle packetInit = null, packetWarnDist = null, packetWarnDelay = null,
                packetLerpSize = null, packetCenter = null, packetSize = null;

        boolean supportsSeperatePackets;

        Class<?> wb = getNMSClass("world.level.border", "WorldBorder");
        Class<?> worldServer = getNMSClass("server.level", "WorldServer");
        Class<?> craftWorld = getCraftClass("CraftWorld");

        try {
            if (!supports(17)) {
                Class<?> wbType;
                try {
                    wbType = Class.forName("EnumWorldBorderAction");
                } catch (ClassNotFoundException e) {
                    wbType = getNMSClass("PacketPlayOutWorldBorder$EnumWorldBorderAction");
                }

                packetInit = lookup.findConstructor(getNMSClass("PacketPlayOutWorldBorder"), MethodType.methodType(void.class, wb, wbType));

                for (Object type : wbType.getEnumConstants()) {
                    if (type.toString().equals("INITIALIZE")) {
                        initialize = type;
                        break;
                    }
                }
            }

            world = lookup.findVirtual(craftWorld, "getHandle", MethodType.methodType(getNMSClass("server.level", "WorldServer")));
            worldborder = lookup.findConstructor(wb, MethodType.methodType(void.class));
            worldborderWorld = lookup.findSetter(wb, "world", worldServer); // name not obfuscated since it's added by craftbukkit
            center = lookup.findVirtual(wb, v(18, "c").orElse("setCenter"), MethodType.methodType(void.class, double.class, double.class));
            warnTime = lookup.findVirtual(wb, v(18, "b").orElse("setWarningTime"), MethodType.methodType(void.class, int.class));

            // or setWarningBlocks
            distance = lookup.findVirtual(wb, v(20, "c").v(18, "b").orElse("setWarningDistance"), MethodType.methodType(void.class, int.class));
            size = lookup.findVirtual(wb, v(18, "a").orElse("setSize"), MethodType.methodType(void.class, double.class));

            // Renamed to lerpSizeBetween(double d0, double d1, long i)
            transition = lookup.findVirtual(wb, v(18, "a").orElse("transitionSizeBetween"), MethodType.methodType(void.class, double.class, double.class, long.class));
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        try {
            // Individual packets were added in 1.17
            Function<String, MethodHandle> getPacket = (packet) ->
            {
                try {
                    return lookup.findConstructor(getNMSClass("network.protocol.game", packet), MethodType.methodType(void.class, wb));
                } catch (NoSuchMethodException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            };

            packetWarnDist = getPacket.apply("ClientboundSetBorderWarningDistancePacket");
            packetWarnDelay = getPacket.apply("ClientboundSetBorderWarningDelayPacket");
            packetLerpSize = getPacket.apply("ClientboundSetBorderLerpSizePacket");
            packetInit = getPacket.apply("ClientboundInitializeBorderPacket");
            packetCenter = getPacket.apply("ClientboundSetBorderCenterPacket");
            packetSize = getPacket.apply("ClientboundSetBorderSizePacket");
            supportsSeperatePackets = true;
        } catch (Throwable ignored) {
            supportsSeperatePackets = false;
        }

        WORLD_HANDLE = world;
        PACKET_INIT = packetInit;
        PACKET_SIZE = packetSize;
        PACKET_CENTER = packetCenter;
        PACKET_LERP_SIZE = packetLerpSize;
        PACKET_WARNING_DELAY = packetWarnDelay;
        PACKET_WARNING_DISTANCE = packetWarnDist;

        SUPPORTS_SEPARATE_PACKETS = supportsSeperatePackets;

        WORLDBORDER = worldborder;
        WORLDBORDER_WORLD = worldborderWorld;
        CENTER = center;
        SIZE = size;
        WARNING_TIME = warnTime;
        WARNING_DISTANCE = distance;
        TRANSITION = transition;
        INITIALIZE = initialize;
    }

    /**
     * Remove the world border.
     *
     * @since 1.0.0
     */
    public void remove() {
        WORLD_BORDERS.remove(player);

        Player player = getPlayer();
        if (player == null) return;

        WorldBorder wb = player.getWorld().getWorldBorder();
        XWorldBorder.from(wb).setPlayer(player).send(true);
    }

    public static void remove(Player player) {
        XWorldBorder wb = get(player);
        if (wb == null) return;
        wb.remove();
    }

    public XWorldBorder setPlayer(Player player) {
        if (player.getUniqueId().equals(this.player)) return this;

        WORLD_BORDERS.remove(this.player, this);
        WORLD_BORDERS.put(player.getUniqueId(), this);

        this.player = player.getUniqueId();
        this.init = true;
        return this;
    }

    public XWorldBorder send() {
        return send(false);
    }

    public XWorldBorder send(boolean forceInit) {
        Player player = getPlayer();
        if (player == null) return this;
        boolean init = forceInit || this.init;
        this.init = false;

        try {
            if (SUPPORTS_SEPARATE_PACKETS && !init) {
                Object[] packets = new Object[updateRequired.size()];
                int i = 0;
                for (Component component : updateRequired) {
                    component.setHandle(this);
                    packets[i++] = component.createPacket(this);
                }
                sendPacketSync(player, packets);
            } else {
                for (Component component : updateRequired) {
                    component.setHandle(this);
                }
                Object packet = supports(17) ?
                        PACKET_INIT.invoke(handle) :
                        PACKET_INIT.invoke(handle, INITIALIZE);
                sendPacketSync(player, packet);
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        } finally {
            updateRequired.clear();
        }
        return this;
    }

    /**
     * Create a new world border object, set its world and center location to the player.
     */
    private Object createHandle() {
        Objects.requireNonNull(world, "No world specified");
        try {
            Object worldBorder = WORLDBORDER.invoke();
            Object world = WORLD_HANDLE.invoke(this.world);
            WORLDBORDER_WORLD.invoke(worldBorder, world);
            return worldBorder;
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            return null;
        }
    }

    private enum Component {
        SIZE {
            @Override
            protected void setHandle(XWorldBorder wb) throws Throwable {
                XWorldBorder.SIZE.invoke(wb.handle, wb.size);
            }

            @Override
            protected Object createPacket(XWorldBorder wb) throws Throwable {
                return PACKET_SIZE.invoke(wb.handle);
            }
        }, SIZE_LERP {
            @Override
            protected void setHandle(XWorldBorder wb) throws Throwable {
                XWorldBorder.TRANSITION.invoke(wb.handle, wb.sizeLerpTarget, wb.size, wb.sizeLerpTime.toMillis());
            }

            @Override
            protected Object createPacket(XWorldBorder wb) throws Throwable {
                return PACKET_LERP_SIZE.invoke(wb.handle);
            }
        }, WARNING_DISTANCE {
            @Override
            protected void setHandle(XWorldBorder wb) throws Throwable {
                XWorldBorder.WARNING_DISTANCE.invoke(wb.handle, wb.warningBlocks);
            }

            @Override
            protected Object createPacket(XWorldBorder wb) throws Throwable {
                return PACKET_WARNING_DISTANCE.invoke(wb.handle);
            }
        }, WARNING_DELAY {
            @Override
            protected void setHandle(XWorldBorder wb) throws Throwable {
                XWorldBorder.WARNING_TIME.invoke(wb.handle, wb.warningBlocks);
            }

            @Override
            protected Object createPacket(XWorldBorder wb) throws Throwable {
                return PACKET_WARNING_DELAY.invoke(wb.handle);
            }
        }, CENTER {
            @Override
            protected void setHandle(XWorldBorder wb) throws Throwable {
                XWorldBorder.CENTER.invoke(wb.handle, wb.centerX, wb.centerZ);
            }

            @Override
            protected Object createPacket(XWorldBorder wb) throws Throwable {
                return PACKET_CENTER.invoke(wb.handle);
            }
        };

        protected abstract void setHandle(XWorldBorder wb) throws Throwable;

        protected abstract Object createPacket(XWorldBorder wb) throws Throwable;
    }
}