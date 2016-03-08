package se.gigurra.dcs.remote.dcsclient

abstract class ConnStatus
case object CONNECTED extends ConnStatus
case object DISCONNECTED extends ConnStatus
