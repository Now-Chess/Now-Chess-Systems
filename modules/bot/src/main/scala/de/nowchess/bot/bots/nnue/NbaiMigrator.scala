package de.nowchess.bot.bots.nnue

import java.nio.{ByteBuffer, ByteOrder}

/** Converts the legacy nnue_weights.bin resource into an NbaiModel. Used as fallback when no .nbai file exists. */
object NbaiMigrator:

  private val BinMagic   = 0x4555_4e4e
  private val BinVersion = 1

  private val DefaultLayers: Array[LayerDescriptor] = Array(
    LayerDescriptor("relu", 768, 1536),
    LayerDescriptor("relu", 1536, 1024),
    LayerDescriptor("relu", 1024, 512),
    LayerDescriptor("relu", 512, 256),
    LayerDescriptor("linear", 256, 1),
  )

  private val UnknownMetadata: NbaiMetadata =
    NbaiMetadata(trainedBy = "unknown", trainedAt = "unknown", trainingDataCount = 0L, valLoss = 0.0, trainLoss = 0.0)

  def migrateFromBin(): NbaiModel =
    val stream = Option(getClass.getResourceAsStream("/nnue_weights.bin"))
      .getOrElse(sys.error("Neither nnue_weights.nbai nor nnue_weights.bin found in resources"))
    try
      val buf = ByteBuffer.wrap(stream.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN)
      checkBinHeader(buf)
      val weights = DefaultLayers.map(_ => readBinLayerWeights(buf))
      NbaiModel(UnknownMetadata, DefaultLayers, weights)
    finally stream.close()

  private def checkBinHeader(buf: ByteBuffer): Unit =
    val magic = buf.getInt()
    if magic != BinMagic then sys.error(s"Invalid bin magic: 0x${magic.toHexString}")
    val version = buf.getInt()
    if version != BinVersion then sys.error(s"Unsupported bin version: $version")

  private def readBinLayerWeights(buf: ByteBuffer): LayerWeights =
    LayerWeights(readBinTensor(buf), readBinTensor(buf))

  private def readBinTensor(buf: ByteBuffer): Array[Float] =
    val shape = Array.tabulate(buf.getInt())(_ => buf.getInt())
    Array.tabulate(shape.product)(_ => buf.getFloat())
