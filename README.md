# dcs-remote2

* Provides a REST API with full script access to lua environments in DCS!
* Consists of a super thin lua script communicating with a REST Proxy written in Scala/Java.
* RESTful: Easily accessible  from any programming language or tool 
* Fast: Includes a built in cache (size configurable) in the REST proxy
    * Specify http parameter max_cached_age=<millis> to allow read from cache (default value 40 ms)
* NOTE: Don't export the REST Proxy or the thin lua layer ports online
   * ANY script runnable by DCS can be injected :).


##### Installation

* Put the lua scripts from this repository into your .../Saved Games/DCS/Scripts/ folder
* Run dcs-remote2.jar (you will see a system tray icon if it starts properly)


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


