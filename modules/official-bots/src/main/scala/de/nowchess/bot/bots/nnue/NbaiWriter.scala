package de.nowchess.bot.bots.nnue

import java.io.{ByteArrayOutputStream, OutputStream}
import java.nio.{ByteBuffer, ByteOrder}
import java.nio.charset.StandardCharsets

object NbaiWriter:

  def write(model: NbaiModel, out: OutputStream): Unit =
    val acc = new ByteArrayOutputStream()
    writeHeader(acc)
    writeMetadata(acc, model.metadata)
    writeLayerDescriptors(acc, model.layers)
    model.weights.foreach(lw => writeLayerWeights(acc, lw))
    out.write(acc.toByteArray)

  private def writeHeader(out: ByteArrayOutputStream): Unit =
    val buf = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
    buf.putInt(NbaiLoader.MAGIC)
    buf.putShort(1.toShort)
    out.write(buf.array())

  private def writeMetadata(out: ByteArrayOutputStream, meta: NbaiMetadata): Unit =
    val json = meta.toJson.getBytes(StandardCharsets.UTF_8)
    val buf  = ByteBuffer.allocate(4 + json.length).order(ByteOrder.LITTLE_ENDIAN)
    buf.putInt(json.length)
    buf.put(json)
    out.write(buf.array())

  private def writeLayerDescriptors(out: ByteArrayOutputStream, layers: Array[LayerDescriptor]): Unit =
    val nameBytes = layers.map(_.activation.getBytes(StandardCharsets.US_ASCII))
    val capacity  = 2 + layers.indices.map(i => 1 + nameBytes(i).length + 8).sum
    val buf       = ByteBuffer.allocate(capacity).order(ByteOrder.LITTLE_ENDIAN)
    buf.putShort(layers.length.toShort)
    layers.zip(nameBytes).foreach { (l, nb) =>
      buf.put(nb.length.toByte)
      buf.put(nb)
      buf.putInt(l.inputSize)
      buf.putInt(l.outputSize)
    }
    out.write(buf.array())

  private def writeLayerWeights(out: ByteArrayOutputStream, lw: LayerWeights): Unit =
    writeFloats(out, lw.weights)
    writeFloats(out, lw.bias)

  private def writeFloats(out: ByteArrayOutputStream, floats: Array[Float]): Unit =
    val buf = ByteBuffer.allocate(4 + floats.length * 4).order(ByteOrder.LITTLE_ENDIAN)
    buf.putInt(floats.length)
    floats.foreach(buf.putFloat)
    out.write(buf.array())
