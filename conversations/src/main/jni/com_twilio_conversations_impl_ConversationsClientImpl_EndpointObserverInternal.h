/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class com_twilio_conversations_impl_ConversationsClientImpl_EndpointObserverInternal */

#ifndef _Included_com_twilio_conversations_impl_ConversationsClientImpl_EndpointObserverInternal
#define _Included_com_twilio_conversations_impl_ConversationsClientImpl_EndpointObserverInternal
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     com_twilio_conversations_impl_ConversationsClientImpl_EndpointObserverInternal
 * Method:    wrapNativeObserver
 * Signature: (Lcom/twilio/conversations/impl/core/EndpointObserver;Lcom/twilio/conversations/Endpoint;)J
 */
JNIEXPORT jlong JNICALL Java_com_twilio_conversations_impl_ConversationsClientImpl_00024EndpointObserverInternal_wrapNativeObserver
  (JNIEnv *, jobject, jobject, jobject);

/*
 * Class:     com_twilio_conversations_impl_ConversationsClientImpl_EndpointObserverInternal
 * Method:    freeNativeObserver
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_twilio_conversations_impl_ConversationsClientImpl_00024EndpointObserverInternal_freeNativeObserver
  (JNIEnv *, jobject, jlong);

JNIEXPORT void JNICALL Java_com_twilio_conversations_impl_ConversationsClientImpl_00024EndpointObserverInternal_markForDeletion
        (JNIEnv *, jobject, jlong);


#ifdef __cplusplus
}
#endif
#endif
