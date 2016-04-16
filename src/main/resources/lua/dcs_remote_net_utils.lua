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

    getTime = function()
        return SOCKET.gettime()
    end,

    createServerSocket = function(port)
        return assert(SOCKET.bind("127.0.0.1", port))
    end
    
}

return utils
