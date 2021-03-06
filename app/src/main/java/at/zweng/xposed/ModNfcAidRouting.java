package at.zweng.xposed;

import android.nfc.cardemulation.CardEmulation;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static de.robv.android.xposed.XposedHelpers.*;

/**
 * Module for the Xposed framework for rerouting all APDUs
 * (https://en.wikipedia.org/wiki/Application_protocol_data_unit) for all AIDs
 * (ISO 7816-4 application identifier) to a single one "catch-all" ApduService
 * (even those PDUs for AIDs which are not declared in the ApduService app
 * Manifest).
 *
 * Tested on Android 4.4.4 (cyanogenmod 11-20141022-NIGHTLY-hammerhead) on a
 * LG Nexus 5 and on Android 7.1.2 on a LeEco LEX720.
 *
 * For Clarification: This app here (which you are looking right now) is NOT a
 * ApduServie for Host-Card-Emulation. It just modifies the AID routing
 * mechanism of Android, to forward all APDUs to a single ApduService app (which
 * you have to develop yourself,
 * see https://developer.android.com/guide/topics/connectivity/nfc/hce).
 *
 * This will override ALL AID routing, so the "catch-all" app (the one which
 * declares the "magic" AID "F04E66E75C02D8" in their manifest) will receive
 * ALL SELECT APDUs even if there are other ApduService apps installed which
 * declare their own AIDs. They will never receive anything.
 *
 * Please note that this does NOT mean, that all APDUs can be received, as
 * Android HCE only works with ISO7816-4 like commands, which require that
 * the first NFC APDU must be a SELECT command.
 *
 * If the NFC reader doesn't start with sending a SELECT, you cannot receive
 * this in Android HCE.
 *
 * Licensed under GPL-3
 *
 * @author Johannes Zweng, john@zweng.at, 16.12.2013
 */
public class ModNfcAidRouting implements IXposedHookLoadPackage {

    /**
     * AID routing is done in com.android.nfc system package.
     */
    private static final String TARGET_PACKAGE = "com.android.nfc";

    /**
     * This (hardcoded) AID is the one you have-to use in your catch-all APDU
     * Service application manifest. Then all requests for unknown AIDS will be
     * re-routed to the application registered under this special AID.
     * (If you ask: I just created this AID randomly, it has no special meaning
     * whatsoever.)
     */
    private static final String SPECIAL_MAGIC_CATCH_ALL_SERVICE_AID = "F04E66E75C02D8";

    /**
     * Our method hook for the "resolveAid" method in
     * "RegisteredAidCache.java" (https://goo.gl/VUo8nt) on Android 7.1.2
     * and for the "resolveAidPrefix" method in "RegisteredAidCache.java"
     * in older Android versions (http://goo.gl/ACY97J) where the AID matching
     * and routing is performed.
     */
    private final XC_MethodHook resolveAidHook = new XC_MethodHook() {

        @SuppressWarnings({"rawtypes", "unchecked"})
        @Override
        protected void beforeHookedMethod(MethodHookParam param)
                throws Throwable {
            try {
                // use this classloader for loading objects
                ClassLoader cls = param.thisObject.getClass().getClassLoader();
                String aid;
                if (param.args.length > 0) {
                    aid = (String) param.args[0];
                    XposedBridge
                            .log("ModNfcAidRouting: resolveAid/Prefix(..) was called. aid = "
                                    + aid);
                } else {
                    XposedBridge
                            .log("ModNfcAidRouting: resolveAid/Prefix: parameter array seems to be 0-length! Bye.");
                    return;
                }
                if (aid.length() == 0) {
                    XposedBridge
                            .log("ModNfcAidRouting: resolveAid/Prefix: parameter 'aid' seems to be 0-length! Bye.");
                    return;
                }

                // get the "this" instance (=instance of
                // "com.android.nfc.cardemulation.RegisteredAidCache")
                Object registeredAidCacheInstance = param.thisObject;

                // get the "mAidCache" HashMap
                // (contains all registered APDU services)
                Field mAidCacheMapField = findField(
                        registeredAidCacheInstance.getClass(), "mAidCache");
                // this HashMap looks like: Map<String,
                // com.android.nfc.cardemulation.RegisteredAidCache$AidResolveInfo>
                Map mAidCachedMap = (Map) mAidCacheMapField
                        .get(registeredAidCacheInstance);

                if (mAidCachedMap == null) {
                    XposedBridge
                            .log("ModNfcAidRouting: ERROR: the 'mAidCache' Map in the 'RegisteredAidCache' instance was null. Bye.");
                    return;
                }
                if (mAidCachedMap.size() == 0) {
                    XposedBridge
                            .log("ModNfcAidRouting: WARNING: the 'mAidCache' Map contains 0 elements. Are you sure you have registered your HostApduService application for an AID?? Bye.");
                    return;
                }

                // ok, now try to get the entry for our 'special'
                // AID out of the mAidCache HashMap (the entry will
                // be an instance of the inner class
                // "RegisteredAidCache$AidResolveInfo")
                Object aidResolveInfoObjectOfCatchAllApp = mAidCachedMap
                        .get(SPECIAL_MAGIC_CATCH_ALL_SERVICE_AID);

                if (aidResolveInfoObjectOfCatchAllApp == null) {
                    XposedBridge
                            .log("ModNfcAidRouting: WARNING: There was no application registered for the 'special magic wildcard' AID "
                                    + SPECIAL_MAGIC_CATCH_ALL_SERVICE_AID
                                    + ". Will not perform any AID re-routing. Bye.");
                    return;
                }

                // the ApduServiceInfo instance of the ApduService which
                // registered the *magic* AID
                Object apduServiceInfoOfCatchAllApp;

                // first check if the field "ApduServiceInfo defaultService" of
                // the returned "AidResolveInfo" object is != null (so that we
                // would return the default service for the AID if there are
                // more than 1 registered ApduServices for the magic AID)
                Field resolveInfoDefaultServiceField = findField(
                        aidResolveInfoObjectOfCatchAllApp.getClass(),
                        "defaultService");
                Object apduServiceInfoDefaultService = resolveInfoDefaultServiceField
                        .get(aidResolveInfoObjectOfCatchAllApp);
                if (apduServiceInfoDefaultService != null) {
                    apduServiceInfoOfCatchAllApp = apduServiceInfoDefaultService;
                }
                // else: if the "defaultService" field was null, then look into
                // the "services" list and return the first element
                else {
                    // try to get the field 'services' of this
                    // AidResolveInfo instance and simply grab the
                    // first entry in the list (which is hopefully our 'special
                    // magic catch-all' service.
                    Field resolveInfoservicesField = findField(
                            aidResolveInfoObjectOfCatchAllApp.getClass(),
                            "services");
                    List aidResolveInfoServicesList = (List) resolveInfoservicesField
                            .get(aidResolveInfoObjectOfCatchAllApp);
                    if (aidResolveInfoServicesList == null) {
                        XposedBridge
                                .log("ModNfcAidRouting: ERROR: The field 'services' of the AidResolveInfo instance we got from 'mAidCache' seemed was null. This is not as expected! Can do nothing. Bye.");
                        return;
                    }
                    if (aidResolveInfoServicesList.size() == 0) {
                        XposedBridge
                                .log("ModNfcAidRouting: ERROR: The List<ApduServiceInfo> 'services' of the AidResolveInfo instance we got from 'mAidCache' contains 0 entries. Can do nothing. Bye.");
                        return;
                    }

                    // get the first object out of the list:
                    // The should be now an instance of
                    // android.nfc.cardemulation.ApduServiceInfo
                    // describing our catch-all service (which we
                    // will then return). :-)
                    apduServiceInfoOfCatchAllApp = aidResolveInfoServicesList
                            .get(0);
                }

                // Ok, now we try to create an instance of the
                // method's response type object:
                // com.android.nfc.cardemulation.RegisteredAidCache$AidResolveInfo
                //
                // As this is an inner class of 'RegisteredAidCache' this is
                // done a little different than with normal classes (we need
                // first to get a Constructor object for this inner class).
                Class innerClassType;
                innerClassType = cls.loadClass(
                        "com.android.nfc.cardemulation.RegisteredAidCache$AidResolveInfo");
                Constructor<?> ctor = innerClassType
                        .getDeclaredConstructor(registeredAidCacheInstance.getClass());
                ctor.setAccessible(true);
                Object resultInstanceAidResolveInfo = ctor
                        .newInstance(registeredAidCacheInstance);

                // set the field "services" in the response object (and let
                // it contain only the catch-all app)
                List ourServicesList = new ArrayList();
                ourServicesList.add(apduServiceInfoOfCatchAllApp);
                Field servicesField = findField(
                        resultInstanceAidResolveInfo.getClass(), "services");
                servicesField
                        .set(resultInstanceAidResolveInfo, ourServicesList);

                // set field "defaultService" (to the catch-all app)
                Field defaultServiceField = findField(
                        resultInstanceAidResolveInfo.getClass(),
                        "defaultService");
                defaultServiceField.set(resultInstanceAidResolveInfo,
                        apduServiceInfoOfCatchAllApp);

                // and set the field "aid" to the AID which was
                // asked for (if it exists, as this doesn't exist on older platforms)
                Field aidField = findFieldIfExists(
                        resultInstanceAidResolveInfo.getClass(), "aid");
                if (aidField != null) {
                    aidField.set(resultInstanceAidResolveInfo, aid);
                }

                // and set the field "category" to CATEGORY_PAYMENT
                // (if it exists, as this doesn't exist on older platforms)
                Field categoryField = findFieldIfExists(
                        resultInstanceAidResolveInfo.getClass(), "category");
                if (categoryField != null) {
                    categoryField.set(resultInstanceAidResolveInfo, CardEmulation.CATEGORY_PAYMENT);
                }

                // and replace the method's result with our object
                param.setResult(resultInstanceAidResolveInfo);
                XposedBridge
                        .log("ModNfcAidRouting: resolveAid/Prefix() SUCCESS! Rerouted the AID "
                                + aid + " to OUR catch-all service! :-)");
            } catch (Exception e) {
                XposedBridge
                        .log("ModNfcAidRouting: resolveAid/Prefix() error in beforeHookedMethod: \n"
                                + e + "\n" + e.getMessage());
                XposedBridge.log(e);
            }
        }
    };

    /**
     * Loading package at device boot time.
     */
    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam)
            throws Throwable {
        //
        // we just place the method hook here
        //
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }
        XposedBridge
                .log("ModNfcAidRouting: we are in com.android.nfc " +
                        "application. :-) Will place method hooks.");

        //
        // Try to hook "resolveAidPrefix" or "resolveAid" method, whatever exists
        //

        // try "resolveAidPrefix"
        try {
            Method resolveAidPrefixMethodToHook = findMethodExactIfExists(
                    "com.android.nfc.cardemulation.RegisteredAidCache",
                    lpparam.classLoader, "resolveAidPrefix", String.class);
            if (resolveAidPrefixMethodToHook != null) {
                findAndHookMethod(
                        "com.android.nfc.cardemulation.RegisteredAidCache",
                        lpparam.classLoader, "resolveAidPrefix", String.class, // <-- resolveAidPrefix
                        resolveAidHook);
                // log succesful hooking! :-)
                XposedBridge
                        .log("ModNfcAidRouting: resolveAidPrefix() method hook in place! Let the fun begin! :-)");
            } else {
                XposedBridge
                        .log("ModNfcAidRouting: resolveAidPrefix() method doesn't seem to exist.");
            }
        } catch (Exception e) {
            XposedBridge
                    .log("ModNfcAidRouting: could not hook resolveAidPrefix(...). Exception: "
                            + e + ", " + e.getMessage());
            XposedBridge.log(e);
        }

        // now try "resolveAid"
        try {
            Method resolveAidMethodToHook = findMethodExactIfExists(
                    "com.android.nfc.cardemulation.RegisteredAidCache",
                    lpparam.classLoader, "resolveAid", String.class);
            if (resolveAidMethodToHook != null) {
                findAndHookMethod(
                        "com.android.nfc.cardemulation.RegisteredAidCache",
                        lpparam.classLoader, "resolveAid", String.class, // <-- resolveAid
                        resolveAidHook);
                // log succesful hooking! :-)
                XposedBridge
                        .log("ModNfcAidRouting: resolveAid() method hook in place! Let the fun begin! :-)");
            } else {
                XposedBridge
                        .log("ModNfcAidRouting: resolveAid() method doesn't seem to exist.");
            }
        } catch (Exception e) {
            XposedBridge
                    .log("ModNfcAidRouting: could not hook resolveAid(...). Exception: "
                            + e + ", " + e.getMessage());
            XposedBridge.log(e);
        }
    }
}
