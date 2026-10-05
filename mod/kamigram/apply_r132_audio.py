#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""r132: перемотка аудио/музыки не зависает и не вылетает.

Корень, а не «поймать Exception»:
  * SeekParameters.EXACT на аудио без индекса блокирует UI-поток внутри
    экстрактора — приложение замирает и его убивает система;
  * onStateChanged считает BUFFERING при audioProgress >= 0.999 концом трека
    и уничтожает плеер прямо во время seekTo — native-вылет;
  * pending-seek умножает C.TIME_UNSET на прогресс и зовёт seek с мусором;
  * системная перемотка делит на нулевую длительность.
"""
import io
import os
import sys

TG = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("TG_DIR", ".")
J = os.path.join(TG, "TMessagesProj/src/main/java")
VIDEO = os.path.join(J, "org/telegram/ui/Components/VideoPlayer.java")
MEDIA = os.path.join(J, "org/telegram/messenger/MediaController.java")
MUSIC = os.path.join(J, "org/telegram/messenger/MusicPlayerService.java")
MARK = "KAMIGRAM_AUDIO_GUARD_R132"


def die(msg):
    sys.stderr.write("r132 audio: %s\n" % msg)
    sys.exit(1)


def read(path):
    with io.open(path, encoding="utf-8") as fh:
        return fh.read()


def write(path, text):
    with io.open(path, "w", encoding="utf-8") as fh:
        fh.write(text)


def replace_once(path, old, new, label):
    src = read(path)
    if MARK in src and old not in src:
        print("r132 audio: %s уже применён" % label)
        return
    n = src.count(old)
    if n != 1:
        die("%s: якорь найден %d раз" % (label, n))
    write(path, src.replace(old, new, 1))
    print("r132 audio: %s" % label)


SAFE = '''
    /* KAMIGRAM_AUDIO_GUARD_R132: аудио перематывается по ближайшему кадру и
       никогда не блокирует чужой поток. Точный seek по музыке без индекса
       замораживал UI и ронял процесс. */
    private void safeSeek(long positionMs, boolean fast, SeekParameters parameters, Runnable whenDone) {
        if (player == null) {
            return;
        }
        final boolean audioOnly = textureView == null && surface == null && surfaceView == null;
        final SeekParameters params = audioOnly ? SeekParameters.CLOSEST_SYNC : parameters;
        final Runnable job = new Runnable() {
            @Override
            public void run() {
                try {
                    if (player == null) {
                        return;
                    }
                    org.telegram.messenger.kamigram.KamiGramAudioGuard.noteSeek();
                    long target = positionMs < 0 ? 0 : positionMs;
                    long duration = player.getDuration();
                    if (duration > 0 && duration != C.TIME_UNSET && target >= duration) {
                        target = duration - 1;
                    }
                    player.setSeekParameters(params);
                    if (whenDone != null) {
                        seekFinishedListeners.add(whenDone);
                    }
                    player.seekTo(target);
                } catch (Throwable ignored) {
                }
            }
        };
        try {
            android.os.Looper playerLooper = player.getApplicationLooper();
            if (playerLooper != null && playerLooper != android.os.Looper.myLooper()) {
                new android.os.Handler(playerLooper).post(job);
            } else {
                job.run();
            }
        } catch (Throwable ignored) {
            try {
                job.run();
            } catch (Throwable ignored2) {
            }
        }
    }

'''


def main():
    for path in (VIDEO, MEDIA, MUSIC):
        if not os.path.isfile(path):
            die("нет %s" % path)

    video = read(VIDEO)
    if "void safeSeek(" not in video:
        anchor = "    public void seekTo(long positionMs) {\n"
        if video.count(anchor) != 1:
            die("seekTo(long) найден %d раз" % video.count(anchor))
        video = video.replace(anchor, SAFE + anchor, 1)
        write(VIDEO, video)

    replace_once(
        VIDEO,
        "    public void seekTo(long positionMs) {\n"
        "        seekTo(positionMs, false);\n"
        "    }\n",
        "    public void seekTo(long positionMs) {\n"
        "        safeSeek(positionMs, false, SeekParameters.EXACT, null);\n"
        "    }\n",
        "seekTo(long)",
    )
    replace_once(
        VIDEO,
        "    public void seekTo(long positionMs, boolean fast) {\n"
        "        if (player != null) {\n"
        "            player.setSeekParameters(fast ? SeekParameters.CLOSEST_SYNC : SeekParameters.EXACT);\n"
        "            player.seekTo(positionMs);\n"
        "        }\n"
        "    }\n",
        "    public void seekTo(long positionMs, boolean fast) {\n"
        "        safeSeek(positionMs, fast, fast ? SeekParameters.CLOSEST_SYNC : SeekParameters.EXACT, null);\n"
        "    }\n",
        "seekTo(long, boolean)",
    )
    replace_once(
        VIDEO,
        "    public void seekTo(long positionMs, boolean fast, Runnable whenDone) {\n"
        "        if (player != null) {\n"
        "            if (whenDone != null) {\n"
        "                seekFinishedListeners.add(whenDone);\n"
        "            }\n"
        "            player.setSeekParameters(fast ? SeekParameters.CLOSEST_SYNC : SeekParameters.EXACT);\n"
        "            player.seekTo(positionMs);\n"
        "        }\n"
        "    }\n",
        "    public void seekTo(long positionMs, boolean fast, Runnable whenDone) {\n"
        "        safeSeek(positionMs, fast, fast ? SeekParameters.CLOSEST_SYNC : SeekParameters.EXACT, whenDone);\n"
        "    }\n",
        "seekTo with callback",
    )
    replace_once(
        VIDEO,
        "    public void seekToBack(long positionMs, boolean fast, Runnable whenDone) {\n"
        "        if (player != null) {\n"
        "            if (whenDone != null) {\n"
        "                seekFinishedListeners.add(whenDone);\n"
        "            }\n"
        "            player.setSeekParameters(fast ? SeekParameters.PREVIOUS_SYNC : SeekParameters.EXACT);\n"
        "            player.seekTo(positionMs);\n"
        "        }\n"
        "    }\n",
        "    public void seekToBack(long positionMs, boolean fast, Runnable whenDone) {\n"
        "        safeSeek(positionMs, fast, fast ? SeekParameters.PREVIOUS_SYNC : SeekParameters.EXACT, whenDone);\n"
        "    }\n",
        "seekToBack",
    )
    replace_once(
        VIDEO,
        "    public void seekToForward(long positionMs, boolean fast, Runnable whenDone) {\n"
        "        if (player != null) {\n"
        "            if (whenDone != null) {\n"
        "                seekFinishedListeners.add(whenDone);\n"
        "            }\n"
        "            player.setSeekParameters(fast ? SeekParameters.NEXT_SYNC : SeekParameters.EXACT);\n"
        "            player.seekTo(positionMs);\n"
        "        }\n"
        "    }\n",
        "    public void seekToForward(long positionMs, boolean fast, Runnable whenDone) {\n"
        "        safeSeek(positionMs, fast, fast ? SeekParameters.NEXT_SYNC : SeekParameters.EXACT, whenDone);\n"
        "    }\n",
        "seekToForward",
    )

    ended = ("if (playbackState == ExoPlayer.STATE_ENDED || (playbackState == ExoPlayer.STATE_IDLE "
             "|| playbackState == ExoPlayer.STATE_BUFFERING) && playWhenReady && messageObject.audioProgress >= 0.999f)")
    replace_once(
        MEDIA,
        ended,
        "if (!org.telegram.messenger.kamigram.KamiGramAudioGuard.seeking() && " + ended[3:] + ")",
        "не уничтожать плеер во время перемотки",
    )
    replace_once(
        MEDIA,
        "        try {\n"
        "            if (audioPlayer != null) {\n"
        "                long duration = audioPlayer.getDuration();\n"
        "                if (duration == C.TIME_UNSET) {\n"
        "                    seekToProgressPending = progress;\n",
        "        progress = org.telegram.messenger.kamigram.KamiGramAudioGuard.clampProgress(progress);\n"
        "        org.telegram.messenger.kamigram.KamiGramAudioGuard.noteSeek();\n"
        "        try {\n"
        "            if (audioPlayer != null) {\n"
        "                long duration = audioPlayer.getDuration();\n"
        "                if (duration == C.TIME_UNSET || duration <= 0) {\n"
        "                    seekToProgressPending = progress;\n",
        "seekToProgress не зовёт native с мусорной длительностью",
    )
    replace_once(
        MEDIA,
        "                            int seekTo = (int) (audioPlayer.getDuration() * seekToProgressPending);\n"
        "                            audioPlayer.seekTo(seekTo);\n",
        "                            long kamiDuration = audioPlayer.getDuration();\n"
        "                            int seekTo = lastProgress;\n"
        "                            if (kamiDuration > 0 && kamiDuration != C.TIME_UNSET) {\n"
        "                                seekTo = (int) (kamiDuration * seekToProgressPending);\n"
        "                                audioPlayer.seekTo(seekTo);\n"
        "                            }\n",
        "отложенный seek без TIME_UNSET",
    )
    replace_once(
        MUSIC,
        "MediaController.getInstance().seekToProgress(object, pos / 1000 / (float) object.getDuration());",
        "double kamiDuration = object.getDuration(); "
        "if (kamiDuration > 0) { "
        "MediaController.getInstance().seekToProgress(object, (float) (pos / 1000.0 / kamiDuration)); "
        "}",
        "системная перемотка не делит на ноль",
    )

    video = read(VIDEO)
    if video.count("void safeSeek(") != 1 or video.count(MARK) < 1:
        die("safeSeek не встал")
    media = read(MEDIA)
    if "KamiGramAudioGuard.seeking()" not in media or "clampProgress" not in media:
        die("MediaController не защищён")
    print("r132 audio: перемотка защищена")


if __name__ == "__main__":
    main()
