/*
 * Copyright (C) 2017 Twilio, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef VIDEO_ANDROID_COM_TWILIO_VIDEO_LOCALAUDIOTRACK_H_
#define VIDEO_ANDROID_COM_TWILIO_VIDEO_LOCALAUDIOTRACK_H_

#include <jni.h>
#include "twilio/media/track.h"
#include "com_twilio_video_AudioTrack.h"

namespace twilio_video_jni {

class LocalAudioTrackContext : public AudioTrackContext {
public:
    LocalAudioTrackContext(std::shared_ptr<twilio::media::LocalAudioTrack> local_audio_track)
            : AudioTrackContext(local_audio_track), local_audio_track_(local_audio_track) { }

    virtual ~LocalAudioTrackContext() = default;

    std::shared_ptr<twilio::media::LocalAudioTrack> getLocalAudioTrack() {
        return local_audio_track_;
    }
private:
    std::shared_ptr<twilio::media::LocalAudioTrack> local_audio_track_;
};

std::shared_ptr<twilio::media::LocalAudioTrack> getLocalAudioTrack(jlong);

std::string getLocalAudioTrackHash(std::shared_ptr<twilio::media::LocalAudioTrack> local_audio_track);

jobject createJavaLocalAudioTrack(jobject j_context,
                                  std::shared_ptr<twilio::media::LocalAudioTrack> local_audio_track);

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jboolean JNICALL Java_com_twilio_video_LocalAudioTrack_nativeIsEnabled(JNIEnv *,
                                                                                 jobject,
                                                                                 jlong);

JNIEXPORT void JNICALL Java_com_twilio_video_LocalAudioTrack_nativeEnable(JNIEnv *,
                                                                          jobject,
                                                                          jlong,
                                                                          jboolean);

JNIEXPORT void JNICALL Java_com_twilio_video_LocalAudioTrack_nativeRelease(JNIEnv *,
                                                                           jobject,
                                                                           jlong);

}

#ifdef __cplusplus
}
#endif

#endif // VIDEO_ANDROID_COM_TWILIO_VIDEO_LOCALAUDIOTRACK_H_
