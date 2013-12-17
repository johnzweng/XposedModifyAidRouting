# Xposed Module NFC AID Re-Routing

### Module for NFC Host-Card-Emulation (HCE) Catch-All Routing ##

Tested with Android 4.4.1
<br/>


### What's this ##

- This is a module for the [Xposed Framework](http://repo.xposed.info/). You need to have installed the Xposed framework (which is not made by me) on your phone. You need to have root for the Xposed framework (but not for this module).

### NFC Card-Emulation Background
- With Android 4.4 (KitKat) Google introduced NFC Host-Card-Emulation, which allows your phone to act as a NFC-SmartCard. App developers now can create special apps which handle all the incoming NFC data packets and response like a ISO 7816-4 SmartCard would do it.

- However, the app developer has to declare explicitly which SmartCard it wants to simulate in the app's Manifest file. 
 	>In detail: each SmartCard application on a SmartCard is identified by a unique AID (application identifier). Usually a card terminal first sends a `SELECT xxxxxxxxx` message to the SmartCard where the `xxxxxxx` stands for the AID, and the SmartCard responds accordingly if it can handle this AID or not.
	>
	> On your KitKat phone Android takes care of checking the AID value. So if the card terminal sends a  `SELECT xxxxxxxxx` message, Android checks if there is any Card-Emulation app installed which has registered for this AID and if so it forwards this and all folowing APDU (ISO 7816-4 application protocol data unit) packets to this app.
	> 
	> Otherwise (if no app has registered this AID) it simply responds with `Application not found` to the card terminal and no app will ever be informed about the incoming APDU message.


### What this module can do for you:

- If this module is enabled, it completly **OVERRIDES** the AID routing mechanism of Android. So **ALL incoming APDUs for ALL application identifier will be routed** to the app which has registered the "super special magic" AID "**F04E66E75C02D8**".

- Even if there are other Card-Emulation apps installed which explicitly register for a specific AID they never will get any APDU message. 
- **All your APDU are belong to us! :-)**


### Where to look for further info:

- This module alone does nothing. You need to implement a `HostApduService` and register it for the AID `F04E66E75C02D8` in the AndroidManifest.xml.

- You will find all infos how this is done and lots of other useful infos on the Android developer documentation pages: [Host-based Card Emulation](https://developer.android.com/guide/topics/connectivity/nfc/hce.html) 

- Wikipedia on [ISO 7816-4 Smart Card APDU](https://en.wikipedia.org/wiki/Smart_card_application_protocol_data_unit)

- Webpage with lot of interesting [infos on some standardized APDU commands](http://www.cardwerk.com/smartcards/smartcard_standard_ISO7816-4_5_basic_organizations.aspx)

