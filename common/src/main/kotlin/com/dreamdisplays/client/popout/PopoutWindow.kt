package com.dreamdisplays.client.popout

import com.dreamdisplays.media.api.VideoFrameSink

interface PopoutWindow : AutoCloseable {
    val isOpen: Boolean
    val width: Int
    val height: Int
    val backend: WindowBackend

    fun open(config: WindowConfig): VideoFrameSink
    override fun close()
    fun on(listener: (PopoutEvent) -> Unit): AutoCloseable
}
