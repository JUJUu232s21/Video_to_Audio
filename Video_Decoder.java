import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaMuxer;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
public class Video_Decoder {
    private static final String TAG = "NativeAudioExtractor";

    public interface OnExtractionListener {
        void onSuccess(File audioFile);
        void onError(String error);
    }

    public static void extractAudio(Context context, Uri videoUri, OnExtractionListener listener) {
        new Thread(() -> {
            File tempVideoFile = null;
            File outputAudioFile = null;
            MediaExtractor extractor = null;
            MediaMuxer muxer = null;

            try {
                tempVideoFile = getFileFromUri(context, videoUri);
                if (tempVideoFile == null || !tempVideoFile.exists()) {
                    if (listener != null) listener.onError("Не удалось прочитать файл видео");
                    return;
                }

                File outputDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC);
                if (outputDir != null && !outputDir.exists()) {
                    outputDir.mkdirs();
                }
                outputAudioFile = new File(outputDir, "audio_" + System.currentTimeMillis() + ".m4a");

                extractor = new MediaExtractor();
                extractor.setDataSource(tempVideoFile.getAbsolutePath());

                int audioTrackIndex = -1;
                int trackCount = extractor.getTrackCount();
                for (int i = 0; i < trackCount; i++) {
                    android.media.MediaFormat format = extractor.getTrackFormat(i);
                    String mime = format.getString(android.media.MediaFormat.KEY_MIME);
                    if (mime != null && mime.startsWith("audio/")) {
                        audioTrackIndex = i;
                        break;
                    }
                }

                if (audioTrackIndex == -1) {
                    if (listener != null) listener.onError("В видеофайле нет звука!");
                    return;
                }

                extractor.selectTrack(audioTrackIndex);
                android.media.MediaFormat trackFormat = extractor.getTrackFormat(audioTrackIndex);

                muxer = new MediaMuxer(outputAudioFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                int writeAudioIndex = muxer.addTrack(trackFormat);
                muxer.start();
                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                int maxChunkSize = 1024 * 1024;
                ByteBuffer buffer = ByteBuffer.allocate(maxChunkSize);
                boolean isRunning = true;
                while (isRunning) {
                    bufferInfo.offset = 0;
                    bufferInfo.size = extractor.readSampleData(buffer, 0);
                    if (bufferInfo.size < 0) {
                        isRunning = false;
                        bufferInfo.size = 0;
                    } else {
                        bufferInfo.presentationTimeUs = extractor.getSampleTime();
                        bufferInfo.flags = MediaCodec.BUFFER_FLAG_SYNC_FRAME;
                        muxer.writeSampleData(writeAudioIndex, buffer, bufferInfo);
                        extractor.advance();
                    }
                }
                if (listener != null) {
                    listener.onSuccess(outputAudioFile);
                }

            } catch (Exception e) {
                if (listener != null) {
                    listener.onError(e.getMessage());
                }
            } finally {
                try {
                    if (muxer != null) {
                        muxer.stop();
                        muxer.release();
                    }
                } catch (Exception ignored) {}

                try {
                    if (extractor != null) {
                        extractor.release();
                    }
                } catch (Exception ignored) {}

                if (tempVideoFile != null && tempVideoFile.exists()) {
                    tempVideoFile.delete();
                }
            }
        }).start();
    }

    private static File getFileFromUri(Context context, Uri uri) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            File tempFile = new File(context.getCacheDir(), "temp_video_" + System.currentTimeMillis() + ".mp4");

            try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
            }
            if (inputStream != null) inputStream.close();
            return tempFile;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
