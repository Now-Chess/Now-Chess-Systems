package de.nowchess.bot.bots.nnue

/** Descriptor for a single dense layer stored in a .nbai file. */
case class LayerDescriptor(activation: String, inputSize: Int, outputSize: Int)

/** Training metadata embedded in every .nbai file. */
case class NbaiMetadata(
    trainedBy: String,
    trainedAt: String,
    trainingDataCount: Long,
    valLoss: Double,
    trainLoss: Double,
):
  def toJson: String =
    s"""{
       |  "trainedBy": "$trainedBy",
       |  "trainedAt": "$trainedAt",
       |  "trainingDataCount": $trainingDataCount,
       |  "valLoss": $valLoss,
       |  "trainLoss": $trainLoss
       |}""".stripMargin

object NbaiMetadata:
  def fromJson(json: String): NbaiMetadata =
    def str(key: String) = raw""""$key"\s*:\s*"([^"]*)"""".r.findFirstMatchIn(json).map(_.group(1)).getOrElse("")
    def num(key: String) = raw""""$key"\s*:\s*([0-9.eE+\-]+)""".r.findFirstMatchIn(json).map(_.group(1)).getOrElse("0")
    NbaiMetadata(
      str("trainedBy"),
      str("trainedAt"),
      num("trainingDataCount").toLong,
      num("valLoss").toDouble,
      num("trainLoss").toDouble,
    )

/** Weights and biases for a single layer. Weights are row-major: (outputSize × inputSize). */
case class LayerWeights(weights: Array[Float], bias: Array[Float])

/** A fully deserialized .nbai model ready to initialize NNUE. */
case class NbaiModel(
    metadata: NbaiMetadata,
    layers: Array[LayerDescriptor],
    weights: Array[LayerWeights],
):
  require(layers.length == weights.length, "Layer count must match weight count")
  require(layers.length >= 2, "Model must have at least 2 layers")
