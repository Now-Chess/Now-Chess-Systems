package de.nowchess.bot.bots.nnue

import java.io.InputStream
import java.nio.{ByteBuffer, ByteOrder}
import java.nio.charset.StandardCharsets

object NbaiLoader:

  /** Little-endian encoding of ASCII bytes 'N','B','A','I'. */
  val MAGIC: Int = 0x4942_414e

  def load(stream: InputStream): NbaiModel =
    val buf = ByteBuffer.wrap(stream.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN)
    checkHeader(buf)
    val metadata = readMetadata(buf)
    val descs    = readLayerDescriptors(buf)
    val weights  = descs.map(_ => readLayerWeights(buf))
    NbaiModel(metadata, descs, weights)

  /** Tries /nnue_weights.nbai on the classpath; falls back to migrating /nnue_weights.bin. */
  def loadDefault(): NbaiModel =
    Option(getClass.getResourceAsStream("/nnue_weights.nbai")) match
      case Some(s) =>
        try load(s)
        finally s.close()
      case None => NbaiMigrator.migrateFromBin()

  private def checkHeader(buf: ByteBuffer): Unit =
    val magic = buf.getInt()
    if magic != MAGIC then sys.error(s"Invalid NBAI magic: 0x${magic.toHexString}")
    val version = buf.getShort() & 0xffff
    if version != 1 then sys.error(s"Unsupported NBAI version: $version")

  private def readMetadata(buf: ByteBuffer): NbaiMetadata =
    val bytes = new Array[Byte](buf.getInt())
    buf.get(bytes)
    NbaiMetadata.fromJson(new String(bytes, StandardCharsets.UTF_8))

  private def readLayerDescriptors(buf: ByteBuffer): Array[LayerDescriptor] =
    Array.tabulate(buf.getShort() & 0xffff) { _ =>
      val nameBytes = new Array[Byte](buf.get() & 0xff)
      buf.get(nameBytes)
      LayerDescriptor(new String(nameBytes, StandardCharsets.US_ASCII), buf.getInt(), buf.getInt())
    }

  private def readLayerWeights(buf: ByteBuffer): LayerWeights =
    LayerWeights(readFloats(buf), readFloats(buf))

  private def readFloats(buf: ByteBuffer): Array[Float] =
    val arr = new Array[Float](buf.getInt())
    for i <- arr.indices do arr(i) = buf.getFloat()
    arr
