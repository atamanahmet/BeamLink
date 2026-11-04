package com.beamlink.peer.send

import java.io.IOException

/**
 * Connect-phase SocketTimeoutException from a peer OkHttp call.
 */
class ConnectTimeoutException(cause: IOException) : IOException(cause)
