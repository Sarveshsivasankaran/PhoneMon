cmake_minimum_required(VERSION 3.22)
project(phonebox_speech C)

# The archive is identical on the upstream and Debian mirrors. The three ABI
# builds share a verified source tree, including the original voice databases.
set(cache "${CMAKE_CURRENT_SOURCE_DIR}/../../../build/flite-source")
file(MAKE_DIRECTORY "${cache}")
file(LOCK "${cache}/prepare.lock" GUARD FILE)
set(flite "${cache}/flite-2.2")
if(NOT EXISTS "${cache}/ready-v2.2")
    file(DOWNLOAD "https://codeload.github.com/festvox/flite/tar.gz/refs/tags/v2.2"
         "${cache}/source.tar.gz" TLS_VERIFY ON
         EXPECTED_HASH SHA256=ab1555fe5adc3f99f1d4a1a0eb1596d329fd6d74f1464a0097c81f53c0cf9e5c)
    file(ARCHIVE_EXTRACT INPUT "${cache}/source.tar.gz" DESTINATION "${cache}")
    file(TOUCH "${cache}/ready-v2.2")
endif()

add_library(phonebox_speech SHARED speech.c)
target_compile_definitions(phonebox_speech PRIVATE CST_AUDIO_NONE CST_NO_SOCKETS)
target_compile_options(phonebox_speech PRIVATE -fvisibility=hidden)
target_include_directories(phonebox_speech PRIVATE
    "${flite}/include" "${flite}/lang/usenglish" "${flite}/lang/cmulex")
target_link_libraries(phonebox_speech PRIVATE m)
function(flite_sources dir)
    foreach(name IN LISTS ARGN)
        target_sources(phonebox_speech PRIVATE "${flite}/${dir}/${name}.c")
    endforeach()
endfunction()
flite_sources(src/audio
    auclient auserver audio au_streaming au_none)
flite_sources(src/cg
    cst_cg cst_mlsa cst_mlpg cst_vc cst_cg_load_voice cst_cg_dump_voice cst_cg_map cst_spamf0)
flite_sources(src/hrg
    cst_utterance cst_relation cst_item cst_ffeature cst_rel_io)
flite_sources(src/lexicon
    cst_lexicon cst_lts cst_lts_rewrites)
flite_sources(src/regex
    cst_regex regexp regsub)
flite_sources(src/speech
    cst_wave cst_wave_io cst_track cst_track_io cst_wave_utils cst_lpcres rateconv g721 g72x g723_24 g723_40)
flite_sources(src/stats
    cst_cart cst_viterbi cst_ss)
flite_sources(src/synth
    cst_synth cst_utt_utils cst_voice cst_phoneset cst_ffeatures cst_ssml flite)
flite_sources(src/utils
    cst_alloc cst_error cst_string cst_wchar cst_tokenstream cst_val cst_features cst_endian cst_socket cst_val_const cst_val_user cst_args cst_url cst_file_stdio cst_mmap_posix)
flite_sources(src/wavesynth
    cst_units cst_clunits cst_diphone cst_sigpr cst_sts cst_reflpc)
flite_sources(lang/cmulex
    cmu_lts_rules cmu_lts_model cmu_lex cmu_lex_entries cmu_lex_data cmu_postlex)
flite_sources(lang/usenglish
    us_int_accent_cart us_int_tone_cart us_f0_model us_dur_stats us_durz_cart us_f0lr us_phoneset us_ffeatures us_phrasing_cart us_gpos us_text us_expand us_nums_cart us_aswd usenglish us_pos_cart)
foreach(voice slt rms awb)
    file(GLOB voice_sources "${flite}/lang/cmu_us_${voice}/*.c")
    target_sources(phonebox_speech PRIVATE ${voice_sources})
endforeach()
