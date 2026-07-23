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

package re.lilith.strand.platform.neoforge

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.brigadier.builder.RequiredArgumentBuilder.argument
import re.lilith.strand.client.StrandChat.PREFIX
import re.lilith.strand.client.gui.StrandHubScreen
import re.lilith.strand.session.SessionController
import net.minecraft.client.Minecraft
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent
import net.neoforged.neoforge.common.NeoForge

object StrandNeoForgeCommands {

    fun register(controller: SessionController) {
        NeoForge.EVENT_BUS.addListener { event: RegisterClientCommandsEvent ->
            event.dispatcher.register(
                literal<CommandSourceStack>("strand")
                    .executes {
                        openHub(); 1
                    }
                    .then(literal<CommandSourceStack>("host").executes {
                        controller.host(); 1
                    })
                    .then(literal<CommandSourceStack>("stop").executes {
                        controller.unhost(); 1
                    })
                    .then(literal<CommandSourceStack>("code").executes { ctx ->
                        val code = controller.currentCode()
                        feedback(ctx.source, if (code != null) "Invite code: $code" else "You are not hosting.")
                        1
                    })
                    .then(literal<CommandSourceStack>("invite")
                        .then(argument<CommandSourceStack, String>("name", StringArgumentType.word()).executes { ctx ->
                            controller.invite(StringArgumentType.getString(ctx, "name")); 1
                        }))
                    .then(literal<CommandSourceStack>("join")
                        .then(argument<CommandSourceStack, String>("code", StringArgumentType.word()).executes { ctx ->
                            controller.joinByCode(StringArgumentType.getString(ctx, "code")); 1
                        }))
                    .then(literal<CommandSourceStack>("accept").executes { ctx ->
                        if (!controller.acceptInvite()) feedback(ctx.source, "No pending invites.")
                        1
                    })
                    .then(literal<CommandSourceStack>("decline").executes { ctx ->
                        if (!controller.declineInvite()) feedback(ctx.source, "No pending invites.")
                        1
                    })
                    .then(literal<CommandSourceStack>("invites")
                        .then(literal<CommandSourceStack>("allow").executes {
                            controller.setInvitesBlocked(false); 1
                        })
                        .then(literal<CommandSourceStack>("block").executes {
                            controller.setInvitesBlocked(true); 1
                        }))
                    .then(literal<CommandSourceStack>("status").executes { ctx ->
                        val hosting = if (controller.isHosting) "hosting (code ${controller.currentCode()})" else "not hosting"
                        val blocked = if (controller.invitesBlocked()) "blocked" else "allowed"
                        val pending = controller.pendingInvites().size
                        feedback(ctx.source, "Status: $hosting, invites $blocked, $pending pending.")
                        1
                    })
            )
        }
    }

    private fun feedback(source: CommandSourceStack, message: String) {
        source.sendSystemMessage(Component.literal("").append(PREFIX).append(" $message"))
    }

    private fun openHub() {
        val mc = Minecraft.getInstance()
        mc.execute { mc.setScreenAndShow(StrandHubScreen(null)) }
    }
}
