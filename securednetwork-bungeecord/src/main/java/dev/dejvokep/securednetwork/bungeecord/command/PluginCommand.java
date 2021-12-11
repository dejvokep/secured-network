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
package dev.dejvokep.securednetwork.bungeecord.command;

import dev.dejvokep.securednetwork.bungeecord.SecuredNetworkBungeeCord;
import dev.dejvokep.securednetwork.bungeecord.util.message.Messenger;
import dev.dejvokep.securednetwork.core.authenticator.Authenticator;
import dev.dejvokep.securednetwork.core.config.Config;
import dev.dejvokep.securednetwork.core.log.Log;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.command.ConsoleCommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Level;

/**
 * Command executor for the main plugin command, which can reload the plugin or generate a new passphrase.
 */
public class PluginCommand extends Command {

    // The configuration file
    private final Config config;
    // The messenger
    private final Messenger messenger;
    // The plugin instance
    private final SecuredNetworkBungeeCord plugin;

    /**
     * Constructor used to register the command.
     *
     * @param command the command name to register
     */
    public PluginCommand(@NotNull SecuredNetworkBungeeCord plugin, @NotNull String command) {
        // Call the superclass constructor
        super(command);
        // Set
        this.plugin = plugin;
        this.config = plugin.getConfiguration();
        this.messenger = plugin.getMessenger();
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        // Check the sender
        if (!(sender instanceof ConsoleCommandSender)) {
            // Console only
            messenger.sendMessage(sender, config.getString("command.console-only"));
            return;
        }

        // If less than 1 argument
        if (args.length < 1) {
            // Invalid format
            messenger.sendMessage(sender, config.getString("command.invalid-format"));
            return;
        }

        // If to reload
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            // Reloading
            plugin.getLog().log(Level.INFO, Log.Source.GENERAL, "Reloading...");

            // Config
            config.load();
            // Authenticator
            plugin.getAuthenticator().reload();
            // IP whitelist
            plugin.getIpWhitelist().reload();
            // Updater
            plugin.getUpdater().reload();
            // Login listener
            plugin.getListener().reload();

            // Reloaded
            plugin.getLog().log(Level.INFO, Log.Source.GENERAL, "Reloaded.");
            messenger.sendMessage(sender, config.getString("command.reload"));
            return;
        }

        // If to generate
        if (args.length <= 2 && args[0].equalsIgnoreCase("generate")) {
            // Generate
            plugin.getAuthenticator().generatePassphrase(args.length == 1 ? Authenticator.RECOMMENDED_PASSPHRASE_LENGTH : toPassphraseLength(args[1]));
            // Generated
            messenger.sendMessage(sender, config.getString("command.generate"));
            return;
        }

        // If to manage the connection logger
        if (args.length >= 2 && args[0].equalsIgnoreCase("connection-logger")) {
            // If to detach
            if (args.length == 2 && args[1].equalsIgnoreCase("detach")) {
                // Detach
                plugin.getListener().getConnectionLogger().detach();
                // Log
                plugin.getLog().log(Level.INFO, Log.Source.CONNECTOR, "Connection logger detached.");
                messenger.sendMessage(sender, config.getString("command.connection-logger.detached"));
                return;
            } else if (args.length == 3 && args[1].equalsIgnoreCase("attach")) {
                // Attach
                plugin.getListener().getConnectionLogger().attach(args[2]);
                // Log
                plugin.getLog().log(Level.INFO, Log.Source.CONNECTOR, "Connection logger attached to name \"" + args[2] + "\".");
                messenger.sendMessage(sender, config.getString("command.connection-logger.attached").replace("{name}", args[2]));
                return;
            }
        }

        // Invalid format
        messenger.sendMessage(sender, config.getString("command.invalid-format"));
    }

    /**
     * Parses the given value to an integer and returns it. The given string is considered suitable if:
     * <ul>
     *     <li>it is an integer,</li>
     *     <li>it is not less than <code>1</code>.</li>
     * </ul>
     * If the string value does not meet the conditions listed above, the returned value is <code>-1</code>, representing
     * an invalid string value.
     *
     * @param value the string value of the integer
     * @return the integer parsed from the given value
     */
    private int toPassphraseLength(@NotNull String value) {
        // Parsed integer
        int parsed;

        // Try to parse
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return -1;
        }

        // If less than 1
        if (parsed < 1)
            return -1;

        return parsed;
    }
}
