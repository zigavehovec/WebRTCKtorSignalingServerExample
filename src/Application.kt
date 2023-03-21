import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import java.time.Duration
import java.util.*

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

private val roomIdToRooms = mutableMapOf<String, SessionManager>()

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {

    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    routing {

        get("/") {
            call.respond("Hello from WebRTC signaling server")
        }
        get("/create-room") {
            val roomId = UUID.randomUUID().toString().take(4)
            roomIdToRooms[roomId] = SessionManager(roomId)
            call.respond(roomId)
        }
        webSocket("/rtc/{roomId}") {
            val roomId = call.parameters["roomId"] ?: error("RoomId parameter not passed!")
            val sessionID = UUID.randomUUID()
            val roomSession = roomIdToRooms[roomId] ?: error("Room not created yet!")

            try {
                roomSession.onSessionStarted(sessionID, this)

                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            roomSession.onMessage(sessionID, frame.readText())
                        }

                        else -> Unit
                    }
                }
                println("Exiting incoming loop, closing session: $sessionID")
                closeSession(sessionID, roomSession)
            } catch (e: ClosedReceiveChannelException) {
                println("onClose $sessionID")
                closeSession(sessionID, roomSession)
            } catch (e: Throwable) {
                println("onError $sessionID $e")
                closeSession(sessionID, roomSession)
            }
        }
    }
}

private fun closeSession(sessionID: UUID, roomSession: SessionManager) {
    roomSession.onSessionClose(sessionID)
    roomIdToRooms.remove(roomSession.roomId)
}
