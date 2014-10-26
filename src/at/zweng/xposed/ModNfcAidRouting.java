package at.zweng.xposed;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findField;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * Module for the Xposed framework for rerouting all APDUs
 * (https://en.wikipedia.org/wiki/Application_protocol_data_unit) for all AIDs
 * (ISO 7816-4 application identifier) to a single one "catch-all" ApduService
 * (even those PDUs for AIDs which are not declared in the ApduService app
 * Manifest).<br>
 * <br>
 * <b>Tested on Android 4.4.4 (cyanogenmod 11-20141022-NIGHTLY-hammerhead) on a
 * LG Nexus 5</b> <br>
 * <br>
 * For Clarification: This app here (which you are looking right now) is NOT a
 * ApduServie for Host-Card-Emulation. It just modifies the AID routing
 * mechanism of Android, to forward all APDUs to a single ApduService app (which
 * you have to develop yourself).<br>
 * <br>
 * <b>This will override ALL AID routing</b>, so the "catch-all" app will
 * receive ALL APDUs even if there are other ApduService apps installed which
 * declare the AID in their manifest. <br>
 * <br>
 * Licensed under GPL-3
 * 
 * @author Johannes Zweng, <john@zweng.at>, 16.12.2013
 */
public class ModNfcAidRouting implements IXposedHookLoadPackage {
	private static final String TARGET_PACKAGE = "com.android.nfc";

	/**
	 * This (hardcoded) AID is the one you have-to use in your catch-all APDU
	 * Service application manifest. Then all requests for unknwon AIDS will be
	 * re-routed to the application registered under this special AID.<br>
	 * (If you ask: I just created this AID randomly, it has no special meaning
	 * whatsover.)
	 */
	private static final String SPECIAL_MAGIC_CATCH_ALL_SERVICE_AID = "F04E66E75C02D8";

	/**
	 * Our method hook for the "resolveAidPrefix" method in
	 * "RegisteredAidCache.java" (http://goo.gl/ACY97J) where the AID matching
	 * and routing is performed.
	 */
	private final XC_MethodHook resolveAidPrefixHook = new XC_MethodHook() {

		@SuppressWarnings({ "rawtypes", "unchecked" })
		@Override
		protected void beforeHookedMethod(MethodHookParam param)
				throws Throwable {
			try {
				// use this classloader for loading objects
				ClassLoader cls = param.thisObject.getClass().getClassLoader();
				String aidArg;
				if (param.args.length > 0) {
					aidArg = (String) param.args[0];
					XposedBridge
							.log("ModNfcAidRouting: resolveAidPrefix(..) was called. aid = "
									+ aidArg);
				} else {
					XposedBridge
							.log("ModNfcAidRouting: resolveAidPrefix: parameter array seems to be 0-length! Bye.");
					return;
				}
				if (aidArg.length() == 0) {
					XposedBridge
							.log("ModNfcAidRouting: resolveAidPrefix: parameter 'aid' seems to be 0-length! Bye.");
					return;
				}

				// get the "this" instance (=instance of
				// "com.android.nfc.cardemulation.RegisteredServicesCache")
				Object registeredAidCacheInstance = param.thisObject;

				// get the "mAidCache" HashMap
				// (contains all registered APDU services)
				Field mAidCacheHashMapField = findField(
						registeredAidCacheInstance.getClass(), "mAidCache");
				// this HashMap looks like: HashMap<String,
				// com.android.nfc.cardemulation.RegisteredAidCache$AidResolveInfo>
				HashMap mAidCachedHashMap = (HashMap) mAidCacheHashMapField
						.get(registeredAidCacheInstance);

				if (mAidCachedHashMap == null) {
					XposedBridge
							.log("ModNfcAidRouting: ERROR: the 'mAidCache' HashMap in the 'RegisteredAidCache' instance was null. Bye.");
					return;
				}
				if (mAidCachedHashMap.size() == 0) {
					XposedBridge
							.log("ModNfcAidRouting: WARNING: the 'mAidCache' HashMap contains 0 elements. Are you sure you have registered your HostApduService application for an AID?? Bye.");
					return;
				}

				// ok, now try to get the entry for our 'special'
				// AID out of the mAidCache HashMap (the entry will
				// be an instance of the inner class
				// "RegisteredAidCache$AidResolveInfo")
				Object aidResolveInfoObjectOfOurService = mAidCachedHashMap
						.get(SPECIAL_MAGIC_CATCH_ALL_SERVICE_AID);

				if (aidResolveInfoObjectOfOurService == null) {
					XposedBridge
							.log("ModNfcAidRouting: WARNING: There was no application registered for the 'special magic wildcard' AID "
									+ SPECIAL_MAGIC_CATCH_ALL_SERVICE_AID
									+ ". Will not perform any AID re-routing. Bye.");
					return;
				}

				// the ApduServiceInfo instance of the ApduService which
				// registered the *magic* AID
				Object apduServiceInfoOfOurService;

				// first check if the field "ApduServiceInfo defaultService" of
				// the returned "AidResolveInfo" object is != null (so that we
				// would return the default service for the AID if there are
				// more than 1 registered ApduServices for the magic AID)
				Field resolveInfoDefaultServiceField = findField(
						aidResolveInfoObjectOfOurService.getClass(),
						"defaultService");
				Object apduServiceInfoDefaultService = resolveInfoDefaultServiceField
						.get(aidResolveInfoObjectOfOurService);
				if (apduServiceInfoDefaultService != null) {
					apduServiceInfoOfOurService = apduServiceInfoDefaultService;
				}
				// else: if the "defaultService" field was null, then look into
				// the "services" list and return the first element
				else {
					// try to get the field 'services' of this
					// AidResolveInfo instance and simply grab the
					// first entry in the list (which is hopefully our 'special
					// magic catch-all' service.
					Field resolveInfoservicesField = findField(
							aidResolveInfoObjectOfOurService.getClass(),
							"services");
					List aidResolveInfoServicesList = (List) resolveInfoservicesField
							.get(aidResolveInfoObjectOfOurService);
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
					apduServiceInfoOfOurService = aidResolveInfoServicesList
							.get(0);
				}

				// Ok, now we try to create an instance of the
				// method's response type object:
				// com.android.nfc.cardemulation.RegisteredAidCache$AidResolveInfo
				//
				// As this is an inner class of
				// 'RegisteredAidCache' this is done a little
				// different than with normal classes (we need
				// first to get a Constructor object for this
				// inner class)
				Class innerClassType;
				innerClassType = cls
						.loadClass("com.android.nfc.cardemulation.RegisteredAidCache$AidResolveInfo");
				Constructor<?> ctor = innerClassType
						.getDeclaredConstructor(registeredAidCacheInstance
								.getClass());
				ctor.setAccessible(true);
				Object resultInstanceAidResolveInfo = ctor
						.newInstance(registeredAidCacheInstance);

				// set the field "services" in the response
				// object (and let it contain only our catch-all
				// service)
				List ourServicesList = new ArrayList();
				ourServicesList.add(apduServiceInfoOfOurService);
				Field servicesField = findField(
						resultInstanceAidResolveInfo.getClass(), "services");
				servicesField
						.set(resultInstanceAidResolveInfo, ourServicesList);

				// set field "defaultService" (to our catch-all
				// service)
				Field defaultServiceField = findField(
						resultInstanceAidResolveInfo.getClass(),
						"defaultService");
				defaultServiceField.set(resultInstanceAidResolveInfo,
						apduServiceInfoOfOurService);

				// and set the field "aid" to the AID which was
				// asked for
				Field aidField = findField(
						resultInstanceAidResolveInfo.getClass(), "aid");
				aidField.set(resultInstanceAidResolveInfo, aidArg);
				param.setResult(resultInstanceAidResolveInfo);
				// Commented out this log statement as Xposed logging is mainly
				// meant for errors (and this could get logged a lot)
				// XposedBridge
				// .log("ModNfcAidRouting: resolveAidPrefix() SUCCESS! Rerouted the AID "
				// + aidArg + " to OUR catch-all service! :-)");
				return;
			} catch (Exception e) {
				XposedBridge
						.log("ModNfcAidRouting: resolveAidPrefix() error in beforeHookedMethod: \n"
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
			// XposedBridge.log("ModNfcAidRouting: ignoring package: "
			// + lpparam.packageName);
			return;
		}
		XposedBridge
				.log("ModNfcAidRouting: we are in com.android.nfc application. :-) Will place method hooks.");
		try {
			findAndHookMethod(
					"com.android.nfc.cardemulation.RegisteredAidCache",
					lpparam.classLoader, "resolveAidPrefix", String.class,
					resolveAidPrefixHook);
			// log succesful hooking! :-)
			XposedBridge
					.log("ModNfcAidRouting: resolveAidPrefix() method hook in place! Let the fun begin! :-)");
		} catch (Exception e) {
			XposedBridge
					.log("ModNfcAidRouting: could not hook resolveAidPrefix(...). Exception: "
							+ e + ", " + e.getMessage());
			XposedBridge.log(e);
		}
	}
}
