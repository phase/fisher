package xyz.jadonfowler.fisher

import com.github.steveice10.mc.auth.exception.request.InvalidCredentialsException
import com.github.steveice10.mc.protocol.MinecraftConstants
import com.github.steveice10.mc.protocol.MinecraftProtocol
import com.github.steveice10.mc.protocol.data.game.entity.player.Hand
import com.github.steveice10.mc.protocol.data.game.entity.type.`object`.ObjectType
import com.github.steveice10.mc.protocol.data.message.TranslationMessage
import com.github.steveice10.mc.protocol.packet.ingame.client.ClientChatPacket
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerUseItemPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerChatPacket
import com.github.steveice10.packetlib.Client
import com.github.steveice10.packetlib.event.session.*
import com.github.steveice10.packetlib.packet.Packet
import com.github.steveice10.packetlib.tcp.TcpSessionFactory
import java.net.Proxy
import javax.swing.*
import com.github.steveice10.packetlib.Session
import com.github.steveice10.mc.protocol.data.status.handler.ServerPingTimeHandler
import com.sun.corba.se.impl.protocol.giopmsgheaders.MessageBase.setFlag
import java.util.Arrays
import com.sun.jmx.snmp.SnmpMsg.getProtocolVersion
import com.github.steveice10.mc.protocol.data.status.ServerStatusInfo
import com.github.steveice10.mc.protocol.data.status.handler.ServerInfoHandler
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerEntityEffectPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerEntityMovementPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.spawn.ServerSpawnMobPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.spawn.ServerSpawnObjectPacket


//fun <T> T?.or(default: T): T = if (this == null) default else this
fun String?.or(default: String) = if (this.isNullOrEmpty()) default else this!!

fun main(args: Array<String>) {
    println("Phase's Fisher")

    val frame = JFrame("Phase's Fisher")
    val usernameField = JTextField()
    val passwordField = JPasswordField()
    passwordField.echoChar = '*'
    val serverField = JTextField("127.0.0.1")

    val startButton = JButton("Start")
    startButton.addActionListener({
        val username = usernameField.text
        val password = passwordField.password
        val server = serverField.text
        try {
            Bot(username, String(password), server)
            frame.isVisible = false
        } catch(e: InvalidCredentialsException) {
            println("Failed to login!")
        }
    })

    val container = JPanel()
    container.layout = BoxLayout(container, BoxLayout.Y_AXIS)
    container.add(usernameField)
    container.add(passwordField)
    container.add(serverField)
    container.add(startButton)
    frame.add(container)

    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.isVisible = true

    /*
    print("Username: ")
    val username = readLine().or("fisher")
    print("Server: ")
    val server = readLine().or("127.0.0.1")
    Bot(username, server)*/
}

class Bot(username: String, password: String, server: String) {

    val client: Client
    var running = false
    var rodOut = false
    val hooks = mutableListOf<Int>()

    init {
        println("$username -> $server")
        val protocol = MinecraftProtocol(username, password)
        client = Client(server, 25565, protocol, TcpSessionFactory(Proxy.NO_PROXY))
        client.session.setFlag(MinecraftConstants.AUTH_PROXY_KEY, Proxy.NO_PROXY)
        client.session.addListener(PacketHandler(this))

        client.session.setFlag(MinecraftConstants.SERVER_INFO_HANDLER_KEY, ServerInfoHandler { _, info ->
            println("Version: " + info.versionInfo.versionName + ", " + info.versionInfo.protocolVersion)
            println("Player Count: " + info.playerInfo.onlinePlayers + " / " + info.playerInfo.maxPlayers)
            println("Players: " + Arrays.toString(info.playerInfo.players))
            println("Description: " + info.description.fullText)
            println("Icon: " + info.icon)
        })

        client.session.setFlag(MinecraftConstants.SERVER_PING_TIME_HANDLER_KEY, ServerPingTimeHandler { _, pingTime -> println("Server ping took " + pingTime + "ms") })
        client.session.connect()

        Thread {
            while (true) {
                if (running) {
                    if (!rodOut) {
                        client.session.send(ClientPlayerUseItemPacket(Hand.MAIN_HAND))
                        rodOut = true
                    }
                }
                Thread.sleep(10)
            }
        }.start()
    }
}

class PacketHandler(val bot: Bot) : SessionListener {
    override fun connected(p0: ConnectedEvent?) {
        println("Connected to ${p0!!.session.host}.")
    }

    override fun disconnected(p0: DisconnectedEvent?) {
        println("Disconnected from ${p0!!.session.host} because ${p0.cause.message}.")
    }

    override fun disconnecting(p0: DisconnectingEvent?) {}
    override fun packetSent(p0: PacketSentEvent?) {}

    override fun packetReceived(p0: PacketReceivedEvent?) {
        val packet: Packet = p0!!.getPacket()
        when (packet) {
            is ServerChatPacket -> {
                val message = packet.message
                val text = if (message is TranslationMessage) {
                    message.translationParams[1].text
                } else packet.message.fullText
                println(text)
                if (text.contains("fish.start")) {
                    bot.running = true
                } else if (text.contains("fish.stop")) {
                    bot.running = false
                    if (bot.rodOut) {
                        bot.client.session.send(ClientPlayerUseItemPacket(Hand.MAIN_HAND))
                        bot.rodOut = false
                    }
                } else if (text.contains("fish.do ")) {
                    val command = text.split("fish.do ")[1]
                    bot.client.session.send(ClientChatPacket(command))
                }
            }
            is ServerSpawnObjectPacket -> {
                if (packet.type == ObjectType.FISH_HOOK) {
                    println(packet.toString())
                    bot.hooks.add(packet.entityId)
                }
            }
            is ServerEntityMovementPacket -> {
                if (bot.hooks.contains(packet.entityId)) {
                    if (packet.movementY < -0.5) {
                        println(packet.movementY)
                        println("Gotcha!")
                        bot.client.session.send(ClientPlayerUseItemPacket(Hand.MAIN_HAND))
                        bot.rodOut = false
                    }
                }
            }
        }
    }
}
