#include <jni.h>
#include <pthread.h>
#include <string.h>
#include "flite.h"

extern cst_voice *register_cmu_us_slt(const char *);
extern cst_voice *register_cmu_us_rms(const char *);
extern cst_voice *register_cmu_us_awb(const char *);

static pthread_once_t init_once = PTHREAD_ONCE_INIT;
static pthread_mutex_t synthesis_lock = PTHREAD_MUTEX_INITIALIZER;
static void initialize(void) { flite_init(); }

JNIEXPORT jboolean JNICALL
Java_com_phonebox_jarvis_core_voice_Flite_synthesize(JNIEnv *env, jobject self,
        jstring voice, jstring text, jstring path) {
    (void)self;
    const char *name = (*env)->GetStringUTFChars(env, voice, NULL);
    if (!name) return JNI_FALSE;
    const char *input = (*env)->GetStringUTFChars(env, text, NULL);
    const char *output = input ? (*env)->GetStringUTFChars(env, path, NULL) : NULL;
    int ok = 0;
    if (output) {
        pthread_once(&init_once, initialize);
        pthread_mutex_lock(&synthesis_lock);
        cst_voice *speaker = strcmp(name, "rms") == 0 ? register_cmu_us_rms(NULL)
                           : strcmp(name, "awb") == 0 ? register_cmu_us_awb(NULL)
                           : register_cmu_us_slt(NULL);
        cst_wave *wave = flite_text_to_wave(input, speaker);
        if (wave) {
            ok = wave->num_samples > 0 && cst_wave_save_riff(wave, output) == CST_OK_FORMAT;
            delete_wave(wave);
        }
        pthread_mutex_unlock(&synthesis_lock);
        (*env)->ReleaseStringUTFChars(env, path, output);
    }
    if (input) (*env)->ReleaseStringUTFChars(env, text, input);
    (*env)->ReleaseStringUTFChars(env, voice, name);
    return ok ? JNI_TRUE : JNI_FALSE;
}
