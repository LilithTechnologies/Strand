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

package re.lilith.strand.client.gui

import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout
import net.minecraft.client.gui.layouts.LinearLayout
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.options.WorldOptionsScreen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.server.IntegratedServer
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentUtils
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer.MultiplayerScope
import net.minecraft.util.HttpUtil
import net.minecraft.world.level.GameType

private val TITLE = Component.literal("Host to Strand")
private val ALLOW_COMMANDS_LABEL = Component.translatable("selectWorld.allowCommands")
private val GAME_MODE_LABEL = Component.translatable("selectWorld.gameMode")

private val OTHER_PLAYERS_HEADER = Component.translatable("menu.multiplayerOptions.otherPlayers.header").withStyle(ChatFormatting.UNDERLINE, ChatFormatting.BOLD)
private val HOST_WORLD = Component.literal("Host World")

private val INWORLD_MENU_LIST_BACKGROUND = Identifier.withDefaultNamespace("textures/gui/inworld_menu_list_background.png")

// Based on vanilla MultiplayerOptionsScreen which is why this class has shit code due to kt conversion
class StrandMultiplayerOptionsScreen(
    private val lastScreen: Screen?,
    private val onHosted: Runnable? = null
) : Screen(
    TITLE
) {
    private val layout = HeaderAndFooterLayout(this)

    private var gameMode = GameType.SURVIVAL
    private var commands = false

    private var initialGameMode = GameType.SURVIVAL
    private var initialCommands = false

    private lateinit var applyChanges: Button

    override fun init() {
        val singleplayerServer = this.minecraft.singleplayerServer
        if (singleplayerServer == null) {
            this.onClose()
        } else {
            this.layout.addTitleHeader(this.title, this.font)
            val content = this.layout.addToContents<LinearLayout>(LinearLayout.vertical().spacing(8))
            content.defaultCellSetting().alignHorizontallyCenter()

            this.applyChanges = Button.builder(HOST_WORLD) { _ ->
                this.minecraft.gui.setScreen(null as Screen?)
                if (this.gameMode != this.initialGameMode) {
                    singleplayerServer.gameTypeForOtherPlayers = this.gameMode
                }

                if (this.commands != this.initialCommands) {
                    singleplayerServer.setCommandsAllowedForOtherPlayers(this.commands)
                }
                this.hostWorld(singleplayerServer)
            }.build()
            this.applyChanges.active = false

            content.addChild(StringWidget(OTHER_PLAYERS_HEADER, this.font))

            val otherPlayerSettings = content.addChild(LinearLayout.horizontal().spacing(8))
            otherPlayerSettings.defaultCellSetting().alignHorizontallyCenter()

            this.gameMode = singleplayerServer.gameTypeForOtherPlayers
            this.initialGameMode = this.gameMode
            val gameModeButton = otherPlayerSettings.addChild<CycleButton<GameType>>(
                CycleButton.builder<GameType>(
                    { obj -> obj.shortDisplayName }, this.gameMode
                ).withValues(
                    *arrayOf(
                        GameType.SURVIVAL,
                        GameType.SPECTATOR,
                        GameType.CREATIVE,
                        GameType.ADVENTURE
                    )
                ).create(
                    GAME_MODE_LABEL
                ) { _, value ->
                    this.gameMode = value
                    this.updateApplyChangesActiveState()
                }
            )

            this.commands = singleplayerServer.commandsAllowedForOtherPlayers()
            this.initialCommands = this.commands
            val allowCommandsButton = otherPlayerSettings.addChild<CycleButton<Boolean>>(
                CycleButton.onOffBuilder(this.commands).create(
                    ALLOW_COMMANDS_LABEL
                ) { _, value ->
                    this.commands = value
                    this.updateApplyChangesActiveState()
                }
            )

            if (singleplayerServer.isHardcore) {
                gameModeButton.active = false
                gameModeButton.setTooltip(WorldOptionsScreen.GAME_MODE_DISABLED_HARDCORE_TOOLTIP)
                allowCommandsButton.active = false
                allowCommandsButton.setTooltip(WorldOptionsScreen.ALLOW_COMMANDS_DISABLED_TOOLTIP)
            }

            val footer = this.layout.addToFooter<LinearLayout>(LinearLayout.horizontal().spacing(8))
            footer.addChild(this.applyChanges)
            footer.addChild(
                Button.builder(
                    CommonComponents.GUI_CANCEL
                ) { _ -> this.onClose() }.build()
            )
            this.layout.visitWidgets { widget -> this.addRenderableWidget(widget) }
            this.repositionElements()
            this.updateApplyChangesActiveState()
        }
    }

    private fun hostWorld(singleplayerServer: IntegratedServer) {
        if (singleplayerServer.unpublishServer()) {
            this.sendPublishMessage(Component.translatable("menu.multiplayerOptions.publish.stopped"))
        }

        val freshPort = HttpUtil.getAvailablePort()

        singleplayerServer.publishServer(MultiplayerScope.LAN, freshPort)
        this.minecraft.playerSocialManager.presenceHandler.tryUpdatePresence()

        this.onHosted?.run()
    }

    private fun updateApplyChangesActiveState() {
        this.applyChanges.active = true
    }

    private fun sendPublishMessage(message: Component) {
        this.minecraft.gui.hud.chat.addClientSystemMessage(message)
        this.minecraft.narrator.saySystemQueued(message)
        this.minecraft.updateTitle()
    }

    override fun repositionElements() {
        this.layout.arrangeElements()
    }

    override fun onClose() {
        this.minecraft.gui.setScreen(this.lastScreen)
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        super.extractBackground(graphics, mouseX, mouseY, a)
        val headerSeparator = if (this.minecraft.level == null) HEADER_SEPARATOR else INWORLD_HEADER_SEPARATOR
        val footerSeparator = if (this.minecraft.level == null) FOOTER_SEPARATOR else INWORLD_FOOTER_SEPARATOR
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            headerSeparator,
            0,
            this.layout.headerHeight - 2,
            0.0f,
            0.0f,
            this.width,
            2,
            32,
            2
        )
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            footerSeparator,
            0,
            this.height - this.layout.footerHeight - 2,
            0.0f,
            0.0f,
            this.width,
            2,
            32,
            2
        )
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            INWORLD_MENU_LIST_BACKGROUND,
            0,
            this.layout.headerHeight,
            this.width.toFloat(),
            (this.height - this.layout.footerHeight).toFloat(),
            this.width,
            this.layout.contentHeight - 2,
            32,
            32
        )
    }

    init {
        this.gameMode
        this.initialGameMode
    }
}