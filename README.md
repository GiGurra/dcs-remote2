# dcs-remote2

* DCS Remote (v2) is a REST API giving you full script access to different lua environments in DCS through http (rest) calls!
* DCS Remote (v2) consists of a super thin lua script communicating with a REST Proxy written in Scala/Java.
* RESTful: Easily accessible  from any programming language or tool 
* Fast: Includes a built in resource cache (size configurable) in the REST proxy
    * Specify http parameter max_cached_age=<millis> to allow read from cache (default value 40 ms)


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


