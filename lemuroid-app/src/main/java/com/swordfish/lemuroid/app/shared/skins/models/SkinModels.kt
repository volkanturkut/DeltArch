package com.swordfish.lemuroid.app.shared.skins.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

@Serializable
data class SkinInfo(
    val name: String,
    val identifier: String,
    val gameTypeIdentifier: String,
    val representations: Map<String, RepresentationInfo> = emptyMap()
)

@Serializable
data class RepresentationInfo(
    val standard: OrientationInfo? = null,
    val edgeToEdge: OrientationInfo? = null,
    val splitView: OrientationInfo? = null,
)

@Serializable
data class OrientationInfo(
    val portrait: LayoutInfo? = null,
    val landscape: LayoutInfo? = null,
)

@Serializable
data class LayoutInfo(
    val assets: AssetsInfo,
    val items: List<ItemInfo> = emptyList(),
    val screens: List<ScreenInfo> = emptyList(),
    val mappingSize: SizeInfo,
    val extendedEdges: EdgesInfo? = null,
    val translucent: Boolean = false,
    val gameScreenFrame: RectInfo? = null,
)

@Serializable
data class AssetsInfo(
    val resizable: String? = null,
    val small: String? = null,
    val medium: String? = null,
    val large: String? = null,
)

@Serializable
data class SizeInfo(
    val width: Float,
    val height: Float,
)

@Serializable
data class EdgesInfo(
    val top: Float = 0f,
    val bottom: Float = 0f,
    val left: Float = 0f,
    val right: Float = 0f,
)

@Serializable
data class ScreenInfo(
    val inputFrame: RectInfo? = null,
    val outputFrame: RectInfo? = null,
)

@Serializable
data class RectInfo(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

@Serializable
data class ItemInfo(
    val inputs: InputsInfo? = null,
    val frame: RectInfo,
    val extendedEdges: EdgesInfo? = null,
    val thumbstick: ThumbstickInfo? = null,
)

@Serializable
data class ThumbstickInfo(
    val name: String,
    val width: Float,
    val height: Float,
)

@Serializable(with = InputsInfoSerializer::class)
data class InputsInfo(
    val buttons: List<String> = emptyList(),
    val dpad: Map<String, String> = emptyMap(),
)

object InputsInfoSerializer : KSerializer<InputsInfo> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("InputsInfo")

    override fun deserialize(decoder: Decoder): InputsInfo {
        val input = decoder as? JsonDecoder ?: error("Can only deserialize JSON")
        return when (val element = input.decodeJsonElement()) {
            is JsonArray -> {
                InputsInfo(buttons = element.map { it.jsonPrimitive.content })
            }
            is JsonObject -> {
                val dpadMap = element.mapValues { it.value.jsonPrimitive.content }
                InputsInfo(dpad = dpadMap)
            }
            else -> InputsInfo()
        }
    }

    override fun serialize(encoder: Encoder, value: InputsInfo) {
        val output = encoder as? JsonEncoder ?: error("Can only serialize to JSON")
        if (value.dpad.isNotEmpty()) {
            output.encodeJsonElement(JsonObject(value.dpad.mapValues { JsonPrimitive(it.value) }))
        } else {
            output.encodeJsonElement(JsonArray(value.buttons.map { JsonPrimitive(it) }))
        }
    }
}
