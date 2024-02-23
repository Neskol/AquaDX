package icu.samnyan.aqua.sega.aimedb

import icu.samnyan.aqua.sega.general.model.Card
import icu.samnyan.aqua.sega.general.service.CardService
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import kotlin.jvm.optionals.getOrNull

/**
 * @author samnyan (privateamusement@protonmail.com)
 */
@Component
@Scope("prototype")
class AimeDbRequestHandler(
    val cardService: CardService
): ChannelInboundHandlerAdapter() {
    val logger: Logger = LoggerFactory.getLogger(AimeDbRequestHandler::class.java)

    data class AimeBaseInfo(val gameId: String, val keychipId: String)

    fun getBaseInfo(input: ByteBuf) = AimeBaseInfo(
        gameId = input.toString(0x0a, 0x0e - 0x0a, StandardCharsets.US_ASCII),
        keychipId = input.toString(0x14, 0x1f - 0x14, StandardCharsets.US_ASCII)
    )

    final val handlers = mapOf<Int, (ByteBuf) -> ByteBuf?>(
        0x01 to ::doFelicaLookup,
        0x04 to ::doLookup,
        0x05 to ::doRegister,
        0x09 to ::doLog,
        0x0b to ::doCampaign,
        0x0d to ::doTouch,
        0x0f to ::doLookup2,
        0x11 to ::doFelicaLookup2,
        0x13 to ::doUnknown19,
        0x64 to ::doHello,
        0x66 to ::doGoodbye
    )

    /**
     * Handle the incoming request
     */
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (msg is Map<*, *>) {
            val type = msg["type"] as Int
            val data = msg["data"] as ByteBuf
            val base = getBaseInfo(data)
            val handler = handlers[type] ?: let {
                logger.error("AimeDB: Unknown request type: ${type.toString(16)}")
                ctx.flush()
                return
            }

            logger.info("AimeDB: Request $handler for game ${base.gameId}, from keychip ${base.keychipId}")

            val result = handler(data)
            if (result != null) ctx.writeAndFlush(result)
            else ctx.flush()
        }
    }

    @Deprecated("Deprecated in Netty 5") // TODO: Move this to ChannelInboundHandler
    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        logger.error("AimeDB: Error", cause)
        ctx.close()
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        super.channelInactive(ctx)
        logger.debug("AimeDB: Connection closed")
    }

    /**
     * Felica Lookup v1: Return the Felica IDm as-is
     */
    fun doFelicaLookup(msg: ByteBuf): ByteBuf {
        val idm = msg.slice(0x20, 0x28 - 0x20).getLong(0)
        val pmm = msg.slice(0x28, 0x30 - 0x28).getLong(0)
        logger.info("> Felica Lookup v1 ($idm, $pmm)")

        // Get the decimal represent of the hex value, same from minime
        val accessCode = idm.toString().replace("-", "").padStart(20, '0')

        logger.info("> Response: $accessCode")
        return Unpooled.copiedBuffer(ByteArray(0x30)).apply {
            setShortLE(0x04, 0x03)
            setShortLE(0x08, 1)
            setBytes(0x24, ByteBufUtil.decodeHexDump(accessCode))
        }
    }

    /**
     * Felica Lookup v2: Look up the card in the card repository, return the External ID
     */
    fun doFelicaLookup2(msg: ByteBuf): ByteBuf {
        val idm = msg.slice(0x20, 0x28 - 0x20).getLong(0)
        val pmm = msg.slice(0x28, 0x30 - 0x28).getLong(0)
        logger.info("> Felica Lookup v2 ($idm, $pmm)")

        // Get the decimal represent of the hex value, same from minime
        val accessCode = idm.toString().replace("-", "").padStart(20, '0')
        val aimeId = cardService.getCardByAccessCode(accessCode).getOrNull()?.extId ?: -1

        logger.info("Response: $accessCode, $aimeId")
        return Unpooled.copiedBuffer(ByteArray(0x0140)).apply {
            setShortLE(0x04, 0x12)
            setShortLE(0x08, 1)
            setLongLE(0x20, aimeId)
            setIntLE(0x24, -0x1) // 0xFFFFFFFF
            setIntLE(0x28, -0x1) // 0xFFFFFFFF
            setBytes(0x2c, ByteBufUtil.decodeHexDump(accessCode))
            setShortLE(0x37, 0x01)
        }
    }

    /**
     * Lookup v1: Find the LUID in the database and return the External ID
     */
    fun doLookup(msg: ByteBuf): ByteBuf {
        val luid = ByteBufUtil.hexDump(msg.slice(0x20, 0x2a - 0x20))
        logger.info("> Lookup v1 ($luid)")

        val aimeId = cardService.getCardByAccessCode(luid).getOrNull()?.extId ?: -1

        logger.info("> Response: $aimeId")
        return Unpooled.copiedBuffer(ByteArray(0x0130)).apply {
            setShortLE(0x04, 0x06)
            setShortLE(0x08, 1)
            setLongLE(0x20, aimeId)
            setByte(0x24, 0)
        }
    }

    fun doLookup2(msg: ByteBuf): ByteBuf {
        val luid = ByteBufUtil.hexDump(msg.slice(0x20, 0x2a - 0x20))
        logger.info("> Lookup v2 ($luid)")

        val aimeId = cardService.getCardByAccessCode(luid).getOrNull()?.extId ?: -1

        logger.info("Response: $aimeId")
        return Unpooled.copiedBuffer(ByteArray(0x0130)).apply {
            setShortLE(0x04, 0x10)
            setShortLE(0x08, 1)
            setLongLE(0x20, aimeId)
            setByte(0x24, 0)
        }
    }

    /**
     * Register: Register a new card by access code
     */
    fun doRegister(msg: ByteBuf): ByteBuf {
        val luid = ByteBufUtil.hexDump(msg.slice(0x20, 0x2a - 0x20))
        logger.info("> Register ($luid)")

        var status = 0
        var aimeId = 0L

        if (cardService.getCardByAccessCode(luid).isEmpty) {
            val card: Card = cardService.registerByAccessCode(luid)

            status = 1
            aimeId = card.extId
        }
        else logger.warn("> Duplicated Aime Card Register detected, access code: $luid")

        logger.info("> Response: $status, $aimeId")
        return Unpooled.copiedBuffer(ByteArray(0x30)).apply {
            setShortLE(0x04, 0x06)
            setShortLE(0x08, status)
            setLongLE(0x20, aimeId)
        }
    }

    /**
     * Log: Just log the request and return a status 1
     */
    fun doLog(msg: ByteBuf) = Unpooled.copiedBuffer(ByteArray(0x20)).apply {
        setShortLE(0x04, 0x0a)
        setShortLE(0x08, 1)
    }

    /**
     * Campaign: Just return a status 1
     */
    fun doCampaign(msg: ByteBuf) = Unpooled.copiedBuffer(ByteArray(0x0200)).apply {
        setShortLE(0x04, 0x0c)
        setShortLE(0x08, 1)
    }

    /**
     * Touch: Just return a status 1
     */
    fun doTouch(msg: ByteBuf): ByteBuf {
        val aimeId = msg.getUnsignedIntLE(0x20)
        logger.info("> Touch ($aimeId)")

        return Unpooled.copiedBuffer(ByteArray(0x50)).apply {
            setShortLE(0x04, 0x0e)
            setShortLE(0x08, 1)
            setShortLE(0x20, 0x6f)
            setShortLE(0x24, 0x01)
        }
    }

    /**
     * We don't know what this is, just return a status 1
     */
    fun doUnknown19(msg: ByteBuf) = Unpooled.copiedBuffer(ByteArray(0x40)).apply {
        setShortLE(0x04, 0x14)
        setShortLE(0x08, 1)
    }

    /**
     * Ping: Just return a status 1
     */
    fun doHello(msg: ByteBuf) = Unpooled.copiedBuffer(ByteArray(0x20)).apply {
        setShortLE(0x04, 0x65)
        setShortLE(0x08, 1)
    }
    
    fun doGoodbye(msg: ByteBuf) = null
}
