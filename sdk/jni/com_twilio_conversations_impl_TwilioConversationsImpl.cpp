/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
#include <string.h>

#include "TSCoreSDKTypes.h"
#include "TSCoreConstants.h"
#include "TSCoreSDK.h"
#include "TSCEndpoint.h"
#include "TSCEndpointObserver.h"
#include "TSCConfiguration.h"
#include "TSCLogger.h"
#include "AccessManager/AccessManager.h"
#include "webrtc/voice_engine/include/voe_base.h"
#include "webrtc/modules/video_capture/video_capture_internal.h"
#include "webrtc/modules/video_render/video_render_internal.h"
#include "talk/app/webrtc/java/jni/androidvideocapturer_jni.h"
#include "webrtc/modules/audio_device/android/audio_manager.h"
#include "webrtc/modules/audio_device/android/opensles_player.h"

#include "com_twilio_conversations_impl_TwilioConversationsImpl.h"

#include "talk/app/webrtc/java/jni/jni_helpers.h"
#include "talk/app/webrtc/java/jni/classreferenceholder.h"
#include "talk/app/webrtc/java/jni/androidnetworkmonitor_jni.h"

#define TAG  "TwilioSDK(native)"

using namespace webrtc_jni;
using namespace twiliosdk;

static bool media_jvm_set = false;

extern "C" jint JNIEXPORT JNICALL JNI_OnLoad(JavaVM *jvm, void *reserved) {
    TS_CORE_LOG_MODULE(kTSCoreLogModuleSignalSDK, kTSCoreLogLevelDebug, "JNI_OnLoad");
    jint ret = InitGlobalJniVariables(jvm);
    if (ret < 0) {
        TS_CORE_LOG_MODULE(kTSCoreLogModuleSignalSDK, kTSCoreLogLevelError, "InitGlobalJniVariables() failed");
        return -1;
    }
    LoadGlobalClassReferenceHolder();

    return ret;
}

extern "C" void JNIEXPORT JNICALL JNI_OnUnLoad(JavaVM *jvm, void *reserved) {
    TS_CORE_LOG_MODULE(kTSCoreLogModuleSignalSDK, kTSCoreLogLevelDebug, "JNI_OnUnload");
    FreeGlobalClassReferenceHolder();
}

static TwilioCommon::AccessManager* getNativeAccessMgrFromJava(JNIEnv* jni, jobject j_accessMgr) {
    jclass j_accessManagerClass = GetObjectClass(jni, j_accessMgr);
    jmethodID getNativeHandleId = GetMethodID(jni, j_accessManagerClass, "getNativeHandle", "()J");

    jlong j_am = jni->CallLongMethod(j_accessMgr, getNativeHandleId);
    return reinterpret_cast<TwilioCommon::AccessManager*>(j_am);
}

/*
 * Class:     com_twilio_conversations_impl_TwilioConversationsImpl
 * Method:    initCore
 * Signature: (Landroid/content/Context;)Z
 */
JNIEXPORT jboolean JNICALL Java_com_twilio_conversations_impl_TwilioConversationsImpl_initCore(JNIEnv *env, jobject obj, jobject context) {
    TS_CORE_LOG_MODULE(kTSCoreLogModuleSignalSDK, kTSCoreLogLevelDebug, "initCore");
    bool failure = false;
    TSCSDK* tscSdk = TSCSDK::instance();

    // TODO investigate relocating some of these calls to more timely locations
    if (!media_jvm_set) {
        failure |= webrtc::OpenSLESPlayer::SetAndroidAudioDeviceObjects(GetJVM(), context);
        failure |= webrtc::VoiceEngine::SetAndroidObjects(GetJVM(), context);
        failure |= webrtc::SetRenderAndroidVM(GetJVM());
        failure |= webrtc_jni::AndroidVideoCapturerJni::SetAndroidObjects(env, context);
        media_jvm_set = true;
    }

    if (tscSdk != NULL &&
            tscSdk->isInitialized() &&
            !failure) {
        return JNI_TRUE;
    }

    return JNI_FALSE;
}

/*
 * Class:     com_twilio_conversations_impl_TwilioConversationsImpl
 * Method:    destroyCore
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_com_twilio_conversations_impl_TwilioConversationsImpl_destroyCore(JNIEnv *env, jobject obj) {
    TS_CORE_LOG_MODULE(kTSCoreLogModuleSignalSDK, kTSCoreLogLevelDebug, "destroyCore");
    TSCSDK::destroy();

    return JNI_TRUE;
}

JNIEXPORT jlong JNICALL Java_com_twilio_conversations_impl_TwilioConversationsImpl_createEndpoint
(JNIEnv *env, jobject obj, jobject j_accessMgr, jlong nativeEndpointObserver) {
    TS_CORE_LOG_MODULE(kTSCoreLogModuleSignalSDK, kTSCoreLogLevelDebug, "createEndpoint");

    TSCOptions options;

    if (!nativeEndpointObserver) {
        TS_CORE_LOG_MODULE(kTSCoreLogModuleSignalSDK, kTSCoreLogLevelError, "nativeEndpointObserver is null");
        return 0;
    }

    TSCEndpointObserverPtr *endpointObserver = reinterpret_cast<TSCEndpointObserverPtr *>(nativeEndpointObserver);
    TwilioCommon::AccessManager* accessManager = getNativeAccessMgrFromJava(env, j_accessMgr);

    if (accessManager == NULL) {
        TS_CORE_LOG_MODULE(kTSCoreLogModuleSignalSDK, kTSCoreLogLevelError, "AccessManager is null");
        return 0;
    }

    if (accessManager->getToken().empty()) {
        TS_CORE_LOG_MODULE(kTSCoreLogModuleSignalSDK, kTSCoreLogLevelError, "token is null");
        return 0;
    }

    TS_CORE_LOG_DEBUG("access token is:%s", accessManager->getToken().c_str());

    TSCEndpointPtr *endpoint = new TSCEndpointPtr();
    *endpoint = TSCSDK::instance()->createEndpoint(options, accessManager, *endpointObserver);

    return jlongFromPointer(endpoint);
}


JNIEXPORT void JNICALL Java_com_twilio_conversations_impl_TwilioConversationsImpl_setCoreLogLevel
(JNIEnv *env, jobject obj, jint level) {
    TS_CORE_LOG_MODULE(kTSCoreLogModuleSignalSDK, kTSCoreLogLevelDebug, "setCoreLogLevel");
    TSCoreLogLevel coreLogLevel = static_cast<TSCoreLogLevel>(level);
    TSCLogger::instance()->setLogLevel(coreLogLevel);
}

JNIEXPORT jint JNICALL Java_com_twilio_conversations_impl_TwilioConversationsImpl_getCoreLogLevel
(JNIEnv *env, jobject obj) {
    TS_CORE_LOG_MODULE(kTSCoreLogModuleSignalSDK, kTSCoreLogLevelDebug, "getCoreLogLevel");
    return TSCLogger::instance()->getLogLevel();
}

JNIEXPORT void JNICALL Java_com_twilio_conversations_impl_TwilioConversationsImpl_refreshRegistrations
  (JNIEnv *, jobject) {
	TSCSDK::instance()->refreshRegistrations();
}
