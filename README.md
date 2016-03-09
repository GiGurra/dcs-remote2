# dcs-remote2

A REST API giving you access to different lua environments in DCS.
DCS Remote (v2) consists of a super thin lua script communicating with a REST Proxy in Scala/Java.

##### Installation

* Put the lua scripts from this repository into your .../Saved Games/DCS/Scripts/ folder
* Run dcs-remote.jar (you will see a system tray icon if it starts properly)


##### Getting data from an environment

    GET http://127.0.0.1:12340/export/LoGetWorldObjects()
    GET http://127.0.0.1:12340/export/LoGetSelfData()


##### Inject custom script

    POST http://127.0.0.1:12340/export
    function change_hud_color() 
        LoSetCommand(156)
    end


##### Run custom injected script

    GET http://127.0.0.1:12340/export/change_hud_color() 


##### Delete resource

    DELETE http://127.0.0.1:12340/export/change_hud_color


##### Store JSON

    PUT http://127.0.0.1:12340/export/junk
    {
        "id": "Junk",
        "lalala": 123
    }


##### GET stored JSON back

    http://127.0.0.1:12340/export/junk?max_cached_age=30000


