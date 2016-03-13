function dcs_remote_export_data()

    -- only export if we're alive
    if (LoGetPlayerPlaneId()) then

        local t = LoGetModelTime()
        if t ~= dcs_remote_last_export_model_time then
            dcs_remote_last_export_model_time = t
                
            local _pitch, _roll, _heading = LoGetADIPitchBankYaw()
            dcs_remote_export_cache = {

                metaData = {
                    version = LoGetVersionInfo(),
                    missionStartTime = LoGetMissionStartTime(),
                    modelTime = LoGetModelTime(),
                    pilotName = LoGetPilotName(),
                    playerPlaneId = LoGetPlayerPlaneId(),
                    camPos = LoGetCameraPosition(),
                    selfData = LoGetSelfData()
                },

                flightModel = {
                    altitudeAboveSeaLevel = LoGetAltitudeAboveSeaLevel(),
                    altitudeAboveGroundLevel = LoGetAltitudeAboveGroundLevel(),
                    pitch = _pitch,
                    roll = _roll,
                    heading = _heading,
                    IAS = LoGetIndicatedAirSpeed(),
                    TAS = LoGetTrueAirSpeed(),
                    AOA = LoGetAngleOfAttack(),
                    acc = LoGetAccelerationUnits(),
                    vv = LoGetVerticalVelocity(),
                    mach = LoGetMachNumber(),
                    magneticYaw = LoGetMagneticYaw(),
                    glideDeviation = LoGetGlideDeviation(),
                    sideDeviation = LoGetSideDeviation(),
                    slipBallPosition = LoGetSlipBallPosition(),
                    atmospherePressure = LoGetBasicAtmospherePressure(),
                    angularVelocity = LoGetAngularVelocity(),
                    vectorVelocity = LoGetVectorVelocity(),
                    windVectorVelocity = LoGetVectorWindVelocity()
                },

                indicators = {
                    engines  = LoGetEngineInfo(),
                    HSI      = LoGetControlPanel_HSI(),
                    nav      = LoGetNavigationInfo(),
                    failures = LoGetMCPState(),
                    mech     = LoGetMechInfo(),
                    beacon   = LoGetRadioBeaconsStatus()
                },

                sensor = {
                    status = LoGetSightingSystemInfo(),
                    targets = {
                        detected = LoGetTargetInformation(),
                        locked = LoGetLockedTargetInformation(),
                        tws = LoGetF15_TWS_Contacts()
                    }
                },

                payload = LoGetPayloadInfo(),

                route = LoGetRoute(),

                electronicWarfare = {
                    rwr = LoGetTWSInfo(),
                    counterMeasures = LoGetSnares()
                },

                wingMen = LoGetWingInfo(),
                wingTargets = LoGetWingTargets()

            }
        end

        return dcs_remote_export_cache
    else
        return dcs_remote_export_cache
    end
end
