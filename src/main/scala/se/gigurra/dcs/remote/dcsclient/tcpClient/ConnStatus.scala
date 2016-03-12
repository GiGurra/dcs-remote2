package se.gigurra.dcs.remote.tcpClient

abstract class ConnStatus
case object CONNECTED extends ConnStatus
case object DISCONNECTED extends ConnStatus
