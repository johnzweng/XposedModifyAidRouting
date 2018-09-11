# Xposed Module NFC AID Re-Routing

### Module for NFC Host-Card-Emulation (HCE) Catch-All Routing ##

Allows NFC HCE apps to receive APDUs for **every** AID and not only their own.

- Tested with Android 4.4.1
- Tested with Android 4.4.4 *(cyanogenmod 11-20141022-NIGHTLY-hammerhead on a LG Nexus 5)*
- Tested with Android 7.1.2
- *Should* work with Android 8.0.0 (not tested, just deduced by looking at Android sourcecode)
- *Should* work with Android 8.1.0 (not tested, just deduced by looking at Android sourcecode)
- *Should* work with Android 8.1.0 (not tested, just deduced by looking at Android sourcecode)
- *Should* work with Android 9.0.0 (not tested, just deduced by looking at Android sourcecode)
<br/>


### What's this ##

- This is a module for the [Xposed Framework](http://repo.xposed.info/).
  You need to have installed the Xposed framework (which is not made by me)
  on your phone. You need to have root to be able to install the Xposed
  framework (but no root is needed for this Xposed module itself).

  This module requires **Xposed API 82 or higher**.

### NFC Card-Emulation Background

- Starting with Android 4.4 (KitKat) Google introduced NFC Host-Card-Emulation ("HCE"),
  which allows your phone to act as a NFC-smartcard. "Host" in this context means
  the chip on which your Android operating system and all the apps are running
  (in contrast to an embedded Secure Element which could also be used for Card Emulation).

  In other words: App developers now can create apps which can handle some incoming
  NFC data packets and response like a ISO 7816-4 NFC smartcard would do it
  (hence "card emulation").

  Android HCE is limited to ISO 7816-4 NFC applications. This means each
  communication with a NFC reader always has to start with the reader first sending a
  `SELECT` command with an **AID** (application identifier) parameter as argument.
  This "SELECT" command selects the NFC application to use and all further sent NFC
  packets (these are called [APDUs](https://en.wikipedia.org/wiki/Smart_card_application_protocol_data_unit) -
  application protocal data units in this context)
  will then be routed to this selected application.

  This is the same how it works on ISO 7816-4 NFC smartcards, like payment cards
  (Visa, Mastercard, etc..) and others.


- To emulate such a card within an Android application the app developer has to declare
  explicitly which smartcard (meaning: which AIDs) it wants to simulate in the app's Manifest file.

  On a real NFC smartcard several applications could be installed (i.e. different payment schemes on
  a single card) and each application is identified by a unique AID (application identifier).
  A card terminal first sends a `SELECT xxxxxxxxx` message to the smartcard where the `xxxxxxx` stands
  for the AID, and the smartcard responds accordingly if it can handle this AID or not.

  On Android the operating system takes care of checking the AID value. So if the card terminal
  sends a  `SELECT xxxxxxxxx` message, Android checks if there is any Card-Emulation app installed
  which has declared this AID in its manifest and only if so it forwards this and all following APDUs
  packets to this app.

  Otherwise (if no app has registered this AID) it simply responds with `Application not found` to the
  card terminal and no app will ever be informed about the incoming APDU message.

  So as an app developer there is no way to register for all possible AID values (which might be useful
  for debugging NFC systems, or building a proxy).


### What this module can do for you:

- If this module is enabled, it completly **OVERRIDES** the AID routing mechanism of Android.
  So **ALL incoming `SELECT` APDUs for ALL application identifiers (AIDs) will be routed** to
  the app which has registered the "super special magic" AID "**F04E66E75C02D8**".

- Even if there are other Card-Emulation apps installed which explicitly register for a specific AID
  they never will get any APDU message.

- **All your APDU are belong to us! :-)**


### Where to look for further info:

- This module alone does nothing. You need to implement a `HostApduService` and register it for
  the AID `F04E66E75C02D8` in the AndroidManifest.xml.
  You will find all infos how this is done and lots of other useful infos on the Android developer
  documentation pages: [Host-based Card Emulation](https://developer.android.com/guide/topics/connectivity/nfc/hce.html)

- This module can not help you to work with non ISO 7816-4 NFC systems
  (which do not send a SELECT as first command).

- Wikipedia on [ISO 7816-4 Smart Card APDU](https://en.wikipedia.org/wiki/Smart_card_application_protocol_data_unit)

- Webpage with lot of interesting [infos on some standardized APDU commands](http://www.cardwerk.com/smartcards/smartcard_standard_ISO7816-4_5_basic_organizations.aspx)
