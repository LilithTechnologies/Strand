/*
 * Strand - Open your Minecraft world to anyone, anywhere.
 * Copyright (C) 2026 Lilith Technologies LLC <hello@lilith.re>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package re.lilith.strand.platform.fabric

import com.mojang.brigadier.arguments.StringArgumentType
import re.lilith.strand.client.StrandChat.PREFIX
import re.lilith.strand.client.gui.StrandHubScreen
import re.lilith.strand.session.SessionController
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object StrandCommands {
    fun register(controller: SessionController) {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommands.literal("strand")
                    .executes {
                        openHub(); 1
                    }
                    .then(ClientCommands.literal("host").executes {
                        controller.host(); 1
                    })
                    .then(ClientCommands.literal("stop").executes {
                        controller.unhost(); 1
                    })
                    .then(ClientCommands.literal("code").executes { ctx ->
                        val code = controller.currentCode()
                        feedback(ctx.source, if (code != null) "Invite code: $code" else "You are not hosting.")
                        1
                    })
                    .then(ClientCommands.literal("invite")
                        .then(ClientCommands.argument("name", StringArgumentType.word()).executes { ctx ->
                            controller.invite(StringArgumentType.getString(ctx, "name")); 1
                        }))
                    .then(ClientCommands.literal("join")
                        .then(ClientCommands.argument("code", StringArgumentType.word()).executes { ctx ->
                            controller.joinByCode(StringArgumentType.getString(ctx, "code")); 1
                        }))
                    .then(ClientCommands.literal("accept").executes { ctx ->
                        if (!controller.acceptInvite()) feedback(ctx.source, "No pending invites.")
                        1
                    })
                    .then(ClientCommands.literal("decline").executes { ctx ->
                        if (!controller.declineInvite()) feedback(ctx.source, "No pending invites.")
                        1
                    })
                    .then(ClientCommands.literal("invites")
                        .then(ClientCommands.literal("allow").executes {
                            controller.setInvitesBlocked(false); 1
                        })
                        .then(ClientCommands.literal("block").executes {
                            controller.setInvitesBlocked(true); 1
                        }))
                    .then(ClientCommands.literal("status").executes { ctx ->
                        val hosting = if (controller.isHosting) "hosting (code ${controller.currentCode()})" else "not hosting"
                        val blocked = if (controller.invitesBlocked()) "blocked" else "allowed"
                        val pending = controller.pendingInvites().size
                        feedback(ctx.source, "Status: $hosting, invites $blocked, $pending pending.")
                        1
                    })
            )
        }
    }

    private fun feedback(source: FabricClientCommandSource, message: String) {
        source.sendFeedback(Component.literal("").append(PREFIX).append(" $message"))
    }

    private fun openHub() {
        val mc = Minecraft.getInstance()
        mc.execute { mc.setScreenAndShow(StrandHubScreen(null)) }
    }
}
