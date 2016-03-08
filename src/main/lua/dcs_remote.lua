---------------------------------------------------------------------------------------------------------------------
---------------------------------------------------------------------------------------------------------------------
-------------------------------------------------  CFG PACKAGE PATH -------------------------------------------------
---------------------------------------------------------------------------------------------------------------------

local function addScriptDir(path)
    package.path = package.path .. ";" .. path .. "/?.lua" .. ";" .. path .. "/?.dll"
end

addScriptDir(lfs.writedir() .. "/Scripts")
addScriptDir(lfs.writedir() .. "/LuaSocket")
addScriptDir(lfs.currentdir() .. "/Scripts")
addScriptDir(lfs.currentdir() .. "/LuaSocket")

---------------------------------------------------------------------------------------------------------------------
---------------------------------------------------------------------------------------------------------------------
-----------------------------------------------------  IMPORTS ------------------------------------------------------
---------------------------------------------------------------------------------------------------------------------

local VECTOR = require 'Vector'
local SOCKET = require 'socket'
local JSON = require 'dkjson'
local NETUTILS = require 'dcs_remote_net_utils'

---------------------------------------------------------------------------------------------------------------------
---------------------------------------------------------------------------------------------------------------------
----------------------------------------------------  DCS_REMOTE ----------------------------------------------------
---------------------------------------------------------------------------------------------------------------------

local logFile = io.open(lfs.writedir().."/Logs/dcs_remote.log", "w")
local serverSocket = NETUTILS.enableTcpNoDelay(NETUTILS.setNonBlocking(NETUTILS.createServerSocket(13465)))
local clients = {}

---------------------------------------------------------------------------------------------------------------------
---------------------------------------------------------------------------------------------------------------------
------------------------------------------------------  FCNS --------------------------------------------------------
---------------------------------------------------------------------------------------------------------------------

local function log(txt)
	logFile:write(txt .. "\n")
    logFile:flush()
end

local function log_err(txt)
	log("ERROR: " .. txt)
end

local function disconnect(client)
    if client then
        client:close()
        clients[client] = nil
    end
end

local function shallowcopy(orig)
    local orig_type = type(orig)
    local copy
    if orig_type == 'table' then
        copy = {}
        for orig_key, orig_value in pairs(orig) do
            copy[orig_key] = orig_value
        end
    else -- number, string, boolean, etc
        copy = orig
    end
    return copy
end

local function send(client, msg, requestId)
    if client and msg then
        
        if requestId then 
            msg.requestId = requestId 
        end
        
        local json = JSON.encode(msg) 
        
        local ok, err = client:send(json .. "\n")
        if not ok and err ~= 'timeout' then
            log_err("send: " .. err)
            disconnect(client)
        end
    end
end

local function handleIncoming(client)
    
    local function processJson(msg)
        
        local lua, decodeErr = JSON.decode(msg)
        if not lua then
            log_err("failed to decode json from incoming message: " .. msg)
            log_err(decodeErr)
            return
        end
            
        local script = lua["script"]
        local requestId = lua["requestId"]
        if not script then
            log_err("No key named 'script' in incoming message: " .. msg)
            return
        end

        local runnable, loadErr = loadstring(script)
        if runnable then
            local result, errRes = runnable()
            if result then
                send(client, result, requestId)
            else
                send(client, { returnValue = "nil" }, requestId)
                log_err("failed to execute script: " .. script)
                log_err(errRes)
            end
        else
            log_err("failed to load script: " .. script)
            log_err(loadErr)
        end
    end
    
    while client do
        local msg, err = client:receive()
        if msg then
            processJson(msg)
        elseif err ~= 'timeout' then
            log_err("handleIncoming: " .. err)
            disconnect(client)
            break
        else
            break
        end
    end
end

---------------------------------------------------------------------------------------------------------------------
---------------------------------------------------------------------------------------------------------------------
----------------------------------------------------  OVERLOADS -----------------------------------------------------
---------------------------------------------------------------------------------------------------------------------
    
local oldLuaExportAfterNextFrame = LuaExportAfterNextFrame
function LuaExportAfterNextFrame()
    if oldLuaExportAfterNextFrame then 
        oldLuaExportAfterNextFrame() 
    end
    
    local newClient = serverSocket:accept()
    if newClient then
        clients[newClient] = NETUTILS.enableTcpNoDelay(NETUTILS.setNonBlocking(newClient))
    end
    
    for client in pairs(shallowcopy(clients)) do
        handleIncoming(client)
    end

end

local oldLuaExportStop = LuaExportStop
function LuaExportStop()
    if oldLuaExportStop then 
        oldLuaExportStop() 
    end
    
    for client in pairs(shallowcopy(clients)) do
        disconnect(client)
    end

	serverSocket:close()
end
