import android.Manifest;
import android.content.ContentValues;
import android.content.res.Configuration;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
public class MainActivity extends AppCompatActivity {
    private ActivityResultLauncher<String> videoPickerLauncher;
    private TextView audioSizeText;
    private ImageView clickSelection;
    private PermissionHelper permissionHelper;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private ImageView playStop;
    private File extractedAudioFile;
    private MediaPlayer mediaPlayer;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishAffinity();
                overridePendingTransition(0, 0);
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback);

        audioSizeText = findViewById(R.id.audioSizeText);
        clickSelection = findViewById(R.id.clickSelection);
        permissionHelper = new PermissionHelper(this);
        playStop = findViewById(R.id.playStop);
        playStop.setTag("Start");

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        clickSelection.setOnClickListener(view -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionHelper.check(Manifest.permission.READ_MEDIA_VIDEO, status -> {
                    if (status == 1) {
                        open_files();
                    } else {
                        Toast.makeText(this, "Нет доступа к медиафайлам!", Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                permissionHelper.check(Manifest.permission.READ_EXTERNAL_STORAGE, status -> {
                    if (status == 1) {
                        open_files();
                    } else {
                        Toast.makeText(this, "Нет доступа к файлам!", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        videoPickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                releaseMediaPlayer();
                Video_Decoder.extractAudio(this, uri, new Video_Decoder.OnExtractionListener() {
                    @Override
                    public void onSuccess(File audioFile) {
                        extractedAudioFile = audioFile;
                        runOnUiThread(() -> {
                            audioSizeText.setText("Вес аудио: " + formatFileSize(audioFile.length()));
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, "Ошибка: " + error, Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            }
        });

        playStop.setOnClickListener(view -> {
            if (extractedAudioFile == null || !extractedAudioFile.exists()) {
                Toast.makeText(this, "Аудио файл не найден!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (mediaPlayer == null) {
                try {
                    mediaPlayer = new MediaPlayer();
                    mediaPlayer.setDataSource(extractedAudioFile.getAbsolutePath());
                    mediaPlayer.prepare();
                    mediaPlayer.setOnCompletionListener(mp -> {
                        playStop.setTag("Start");
                        playStop.setImageResource(R.drawable.baseline_play_arrow_24);
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(this, "Ошибка воспроизведения аудио", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                playStop.setTag("Start");
                playStop.setImageResource(R.drawable.baseline_play_arrow_24);
            } else {
                mediaPlayer.start();
                playStop.setTag("Stop");
                playStop.setImageResource(R.drawable.baseline_pause_24);
            }
        });
    }

    public void dowloadAudio(View view) {
        if (extractedAudioFile == null || !extractedAudioFile.exists()) {
            Toast.makeText(this, "Нет аудио файла для сохранения!", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, extractedAudioFile.getName());
            values.put(MediaStore.MediaColumns.MIME_TYPE, "audio/m4a");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/ConvertedAudio");
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            }

            Uri uri = getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                try (OutputStream out = getContentResolver().openOutputStream(uri);
                     InputStream in = new FileInputStream(extractedAudioFile)) {
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear();
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                    getContentResolver().update(uri, values, null, null);
                }

                Toast.makeText(this, "Аудио сохранено в папку Музыка!", Toast.LENGTH_LONG).show();
                releaseMediaPlayer();
                if (extractedAudioFile != null && extractedAudioFile.exists()) {
                    extractedAudioFile.delete();
                }
                extractedAudioFile = null;
                audioSizeText.setText("Вес аудио: ");
            } else {
                Toast.makeText(this, "Не удалось создать файл в музыке", Toast.LENGTH_SHORT).show();
                releaseMediaPlayer();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Ошибка при сохранении: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            releaseMediaPlayer();
        }
    }

    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (playStop != null) {
            playStop.setTag("Start");
            playStop.setImageResource(R.drawable.baseline_play_arrow_24);
        }
    }

    private String formatFileSize(long sizeInBytes) {
        if (sizeInBytes < 1024) {
            return sizeInBytes + " B";
        } else if (sizeInBytes < 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.1f KB", sizeInBytes / 1024.0);
        } else {
            return String.format(Locale.getDefault(), "%.1f MB", sizeInBytes / (1024.0 * 1024.0));
        }
    }

    private void open_files() {
        videoPickerLauncher.launch("video/*");
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateSystemBar();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseMediaPlayer();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        getWindow().getDecorView().post(() -> updateSystemBar());
    }

    private void updateSystemBar() {
        getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.gray));
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.gray));
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(false);
            controller.setAppearanceLightNavigationBars(false);
        }
    }
}
