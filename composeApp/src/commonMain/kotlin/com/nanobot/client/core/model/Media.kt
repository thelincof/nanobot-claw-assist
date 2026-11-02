package com.nanobot.client.core.model

/** A file selected locally and not yet uploaded. */
data class LocalAttachment(
    val name: String,
    val mime: String,
    val bytes: ByteArray,
)

@kotlinx.serialization.Serializable
data class OutboundAttachment(
    val name: String,
    @kotlinx.serialization.SerialName("data_url") val dataUrl: String,
)
