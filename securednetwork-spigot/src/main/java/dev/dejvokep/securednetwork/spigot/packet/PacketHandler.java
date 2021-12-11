/*
 * Copyright 2021 https://dejvokep.dev/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.dejvokep.securednetwork.spigot.packet;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.injector.server.TemporaryPlayerFactory;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import dev.dejvokep.securednetwork.core.authenticator.AuthenticationResult;
import dev.dejvokep.securednetwork.core.authenticator.Authenticator;
import dev.dejvokep.securednetwork.core.connection.ConnectionLogger;
import dev.dejvokep.securednetwork.core.log.Log;
import dev.dejvokep.securednetwork.spigot.SecuredNetworkSpigot;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.logging.Level;

/**
 * Listens for the {@link PacketType.Handshake.Client#SET_PROTOCOL} packet. This packet is then used to read the
 * <code>host</code> string and extract the property passed by the proxy server from it. If there is the correct
 * property and value, connection is allowed. The property is then removed from the packet to hide it from unwanted
 * exposures. This is also why this packet was chosen - it is the first packet sent between the server and client,
 * so we can remove the property as soon as possible.
 * <p>
 * Inspired by project BungeeGuard (https://github.com/lucko/BungeeGuard).
 */
public class PacketHandler {

    // Protocol manager
    private final ProtocolManager protocolManager;
    // The plugin instance
    private final SecuredNetworkSpigot plugin;

    // If to block pings
    private boolean blockPings;

    // Connection logger
    private final ConnectionLogger connectionLogger;

    /**
     * Registers the packet listener and handles the incoming connections.
     *
     * @param protocolManager the protocol manager used to register the listener and send packets
     * @param plugin          the main class
     */
    public PacketHandler(@NotNull ProtocolManager protocolManager, @NotNull SecuredNetworkSpigot plugin) {
        // Set
        this.protocolManager = protocolManager;
        this.plugin = plugin;
        this.connectionLogger = new ConnectionLogger(plugin.getLogger()::info);
        // Authenticator
        final Authenticator authenticator = plugin.getAuthenticator();

        // Listen to the handshake packet
        protocolManager.addPacketListener(new PacketAdapter(plugin.getPlugin(), PacketType.Handshake.Client.SET_PROTOCOL) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                // If pinging and it is allowed
                if (event.getPacket().getProtocols().read(0) == PacketType.Protocol.STATUS && !blockPings)
                    return;
                // The strings
                StructureModifier<String> strings = event.getPacket().getStrings();
                // Authenticate
                AuthenticationResult result = authenticator.authenticate(strings.read(0));
                // Log the result
                logResult(result.getPlayerId(), result.isPassed(), strings.read(0));
                // If failed
                if (!result.isPassed()) {
                    // Disconnect
                    if (!disconnect(event.getPlayer())) {
                        // Mess up the hostname so the server will disconnect the player
                        strings.write(0, "");
                        return;
                    }
                }
                // Set the host
                strings.write(0, result.getHost());
            }
        });
    }

    /**
     * Disconnects the given player. Returns if disconnection was successful.
     *
     * @param player the player to disconnect
     * @return if the player was disconnected
     */
    private boolean disconnect(@NotNull Player player) {
        try {
            // Create the disconnect packet
            PacketContainer disconnectPacket = new PacketContainer(PacketType.Login.Server.DISCONNECT);
            // Message
            String message = ChatColor.translateAlternateColorCodes('&', plugin.getConfiguration().getString("disconnect-failed-authentication"));
            // Write defaults
            disconnectPacket.getModifier().writeDefaults();
            BaseComponent[] textComponent = TextComponent.fromLegacyText(message);
            String serialized = ComponentSerializer.toString(textComponent);
            WrappedChatComponent wrappedChatComponent = WrappedChatComponent.fromJson(serialized);
            // Set the message
            disconnectPacket.getChatComponents().write(0, wrappedChatComponent);
            // Send
            protocolManager.sendServerPacket(player, disconnectPacket);

            // Disconnect the player
            TemporaryPlayerFactory.getInjectorFromPlayer(player).disconnect(message);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    /**
     * Reloads the internal configuration.
     */
    public void reload() {
        // If to block ping packets
        blockPings = plugin.getConfiguration().getBoolean("block-pings");
    }

    /**
     * Logs the result of a connection request determined by the authentication result. The cause is always
     * <code>failed_authentication</code> if the connection was rejected.
     *
     * @param playerId UUID of the player connecting, or <code>?</code> if unknown
     * @param accepted if the connection was accepted
     */
    private void logResult(@Nullable String playerId, boolean accepted, @NotNull String propertyDump) {
        // Message
        String message = "uuid=" + playerId + " result=" + (accepted ? "accepted" : "rejected") +
                (accepted ? "" : " cause=failed_authentication") + (accepted ? "" : "dump=" + propertyDump);
        // Log
        connectionLogger.handle(playerId, message);
        plugin.getLog().log(Level.INFO, Log.Source.CONNECTOR, message);
    }

    /**
     * Returns the connection logger.
     *
     * @return the connection logger
     */
    public ConnectionLogger getConnectionLogger() {
        return connectionLogger;
    }
}