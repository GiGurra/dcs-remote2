local SOCKET = require 'socket'

local utils = {

    enableTcpNoDelay = function(socket)
        socket:setoption("tcp-nodelay", true)
        return socket
    end,

    setNonBlocking = function(socket)
        socket:settimeout(0.0)
        return socket
    end,

    createServerSocket = function(port)
        return assert(SOCKET.bind("*", port))
    end
    
}

return utils
