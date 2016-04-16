---------------------------------------------------------------------------------------------------------------------
---------------------------------------------------------------------------------------------------------------------
-------------------------------------------------        CFG        -------------------------------------------------
---------------------------------------------------------------------------------------------------------------------

local function addScriptDir(path)
    package.path = package.path .. ";" .. path .. "/?.lua" .. ";" .. path .. "/?.dll"
end

addScriptDir(lfs.writedir() .. "/Scripts")
addScriptDir(lfs.writedir() .. "/LuaSocket")
addScriptDir(lfs.currentdir() .. "/Scripts")
addScriptDir(lfs.currentdir() .. "/LuaSocket")

local JSON = require 'dkjson'
local NETUTILS = require 'dcs_remote_net_utils'

dcsRemote_logFile = nil
dcsRemote_ServerSocket = nil
dcsRemote_clients = {}
dcsRemote_scriptCache = {}
dcsRemote_scriptCache_maxSize = 10

---------------------------------------------------------------------------------------------------------------------
---------------------------------------------------------------------------------------------------------------------
-----------------------------------------------------  HELPERS ------------------------------------------------------
---------------------------------------------------------------------------------------------------------------------

local function log(txt)
	dcsRemote_logFile:write(txt .. "\n")
    dcsRemote_logFile:flush()
end

local function log_err(txt)
    log("ERROR: " .. txt)
end

local function loadCachedScript(script)

    if #dcsRemote_scriptCache >= dcsRemote_scriptCache_maxSize then

        local minUseItem

        for _, v in pairs(dcsRemote_scriptCache) do
            if not minUseItem or v.executions < minUseItem.executions then
                minUseItem = v
            end
        end

        if minUseItem then
            dcsRemote_scriptCache[minUseItem.script] = nil
        end

    end

    local cached = dcsRemote_scriptCache[script]
    if cached then
        return cached
    else
        local runnable, loadErr = loadstring(script)
        if runnable then
            local newItem = { runnable = runnable, loadErr = loadErr, script = script, executions = 0 }
            dcsRemote_scriptCache[script] = newItem
            return newItem, nil
        else
            return nil, loadErr
        end
    end

end

local function disconnect(client)
    if client then
        client:close()
        dcsRemote_clients[client] = nil
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

        local json = ""

        local t0 = NETUTILS.getTime()
        for i=1,10 do
            json = JSON.encode(msg)
        end
        local t1 = NETUTILS.getTime()
        local dt = t1 - t0

        msg.t0 = t0
        msg.t1 = t1
        msg.dt = dt
        json = JSON.encode(msg)

        local ok, err = client:send(json .. "\n")
        if not ok and err ~= 'timeout' then
            log_err("send: " .. err)
            disconnect(client)
        end
    end
end

local function doExecute(client, cached, requestId)

    cached.verifiedOk = false

    local result, errRes = cached.runnable()

    if not errRes then
        cached.verifiedOk = true
        cached.executions = cached.executions + 1
        if result then
            send(client, result, requestId)
        else
            send(client, { message = "No Content", source = cached.script }, requestId)
        end
    else
        send(client, { err = errRes }, requestId)
        log_err("failed to execute script: " .. cached.script)
        log_err(errRes)
    end

    return result, errRes
end

local function processJson(client, msg)

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

    local cached, loadErr = loadCachedScript(script)
    if cached then

        if cached.verifiedOk then
            doExecute(client, cached, requestId)
        else
            local ok, pcallErr = pcall(doExecute, client, cached, requestId)
            if not ok then
                send(client, { err = pcallErr }, requestId)
                log_err("failed to execute script: " .. script)
                log_err(pcallErr)
            end
        end

    else
        log_err("failed to load script: " .. script)
        log_err(loadErr)
    end
end

local function handleIncoming(client)
    while client do
        local msg, err = client:receive()
        if msg then
            processJson(client, msg)
        elseif err ~= 'timeout' then
            log_err("handleIncoming: " .. err)
            disconnect(client)
            break
        else
            break
        end
    end
end

-----------------------------------------------------------------------------------------------------------------
-----------------------------------------------------------------------------------------------------------------
----------------------------------------------------  HOOKS -----------------------------------------------------
-----------------------------------------------------------------------------------------------------------------

local oldLuaExportAfterNextFrame = LuaExportAfterNextFrame
function LuaExportAfterNextFrame()

    if oldLuaExportAfterNextFrame then
        oldLuaExportAfterNextFrame()
    end

    if not dcsRemote_logFile then
        dcsRemote_logFile = io.open(lfs.writedir().."/Logs/dcs_remote.log", "w")
    end

    if not dcsRemote_ServerSocket then
        dcsRemote_ServerSocket = NETUTILS.enableTcpNoDelay(NETUTILS.setNonBlocking(NETUTILS.createServerSocket(13465)))
    end

    local newClient = dcsRemote_ServerSocket:accept()
    if newClient then
        dcsRemote_clients[newClient] = NETUTILS.enableTcpNoDelay(NETUTILS.setNonBlocking(newClient))
    end
    
    for client in pairs(shallowcopy(dcsRemote_clients)) do
        handleIncoming(client)
    end

end

local oldLuaExportStop = LuaExportStop
function LuaExportStop()
    if oldLuaExportStop then 
        oldLuaExportStop() 
    end
    
    for client in pairs(shallowcopy(dcsRemote_clients)) do
        disconnect(client)
    end

    if dcsRemote_ServerSocket then
        dcsRemote_ServerSocket:close()
        dcsRemote_ServerSocket = nil
    end

    if dcsRemote_logFile then
        dcsRemote_logFile:close()
        dcsRemote_logFile = nil
    end

end
