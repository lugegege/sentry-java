package io.sentry.samples;

import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import io.sentry.Attachment;
import io.sentry.Sentry;
import io.sentry.UserFeedback;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.User;
import io.sentry.samples.databinding.ActivityMainBinding;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import org.greenrobot.greendao.query.Query;

public class MainActivity extends AppCompatActivity {

  private NoteDao noteDao;
  private Query<Note> notesQuery;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());

    // get the note DAO
    DaoSession daoSession = ((MyApplication) getApplication()).getDaoSession();
    noteDao = daoSession.getNoteDao();

    // query all notes, sorted a-z by their text
    notesQuery = noteDao.queryBuilder().orderAsc(NoteDao.Properties.Text).build();

    File imageFile = getApplicationContext().getFileStreamPath("sentry.png");
    try (InputStream inputStream =
            getApplicationContext().getResources().openRawResource(R.raw.sentry);
        FileOutputStream outputStream = new FileOutputStream(imageFile)) {
      byte[] bytes = new byte[1024];
      while (inputStream.read(bytes) != -1) {
        outputStream.write(bytes);
      }
      outputStream.flush();
    } catch (IOException e) {
      Sentry.captureException(e);
    }

    Attachment image = new Attachment(imageFile.getAbsolutePath());
    image.setContentType("image/png");
    Sentry.configureScope(
        scope -> {
          scope.addAttachment(image);
        });

    binding.crashFromJava.setOnClickListener(
        view -> {
          throw new RuntimeException("Uncaught Exception from Java.");
        });

    binding.sendMessage.setOnClickListener(view -> Sentry.captureMessage("Some message."));

    binding.sendUserFeedback.setOnClickListener(
        view -> {
          SentryId sentryId = Sentry.captureException(new Exception("I have feedback"));

          UserFeedback userFeedback = new UserFeedback(sentryId);
          userFeedback.setComments("It broke on Android. I don't know why, but this happens.");
          userFeedback.setEmail("john@me.com");
          userFeedback.setName("John Me");
          Sentry.captureUserFeedback(userFeedback);
        });

    binding.addAttachment.setOnClickListener(
        view -> {
          String fileName = Calendar.getInstance().getTimeInMillis() + "_file.txt";
          File file = getApplication().getFileStreamPath(fileName);
          try (FileOutputStream fileOutputStream = new FileOutputStream(file);
              OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutputStream)) {
            for (int i = 0; i < 1024; i++) {
              // To keep the sample code simple this happens on the main thread. Don't do this in a
              // real app.
              outputStreamWriter.write("1");
            }
            outputStreamWriter.flush();
          } catch (IOException e) {
            Sentry.captureException(e);
          }

          Sentry.configureScope(
              scope -> {
                String json = "{ \"number\": 10 }";
                Attachment attachment = new Attachment(json.getBytes(), "log.json");
                scope.addAttachment(attachment);
                scope.addAttachment(new Attachment(file.getPath()));
              });
        });

    // adds the current db as an attachment to the main scope
    File noteDbFile = getApplicationContext().getDatabasePath("notes-db");

    Attachment noteDb = new Attachment(noteDbFile.getAbsolutePath());
    Sentry.configureScope(
        scope -> {
          scope.addAttachment(noteDb);
        });

    binding.sendNotes.setOnClickListener(
        view -> {
          // every click will add a new comment
          int size = notesQuery.list().size();
          size += 1;
          Note note = new Note();
          note.setText(size + " note");
          note.setComment(size + " comment");
          note.setDate(new Date());
          note.setType(NoteType.TEXT);
          noteDao.insert(note);
          Log.d("DaoExample", "Inserted new note, ID: " + note.getId());

          // after this, click on any other button that captures a message or exception
          // you should expect an attachment along with all the previous + this new note
        });

    binding.captureException.setOnClickListener(
        view ->
            Sentry.captureException(
                new Exception(new Exception(new Exception("Some exception.")))));

    binding.breadcrumb.setOnClickListener(
        view -> {
          Sentry.addBreadcrumb("Breadcrumb");
          Sentry.setExtra("extra", "extra");
          Sentry.setFingerprint(Collections.singletonList("fingerprint"));
          Sentry.setTransaction("transaction");
          Sentry.captureException(new Exception("Some exception with scope."));
        });

    binding.unsetUser.setOnClickListener(
        view -> {
          Sentry.setTag("user_set", "null");
          Sentry.setUser(null);
        });

    binding.setUser.setOnClickListener(
        view -> {
          Sentry.setTag("user_set", "instance");
          User user = new User();
          user.setUsername("username_from_java");
          // works with some null properties?
          // user.setId("id_from_java");
          user.setEmail("email_from_java");
          // Use the client's IP address
          user.setIpAddress("{{auto}}");
          Sentry.setUser(user);
        });

    binding.nativeCrash.setOnClickListener(view -> NativeSample.crash());

    binding.nativeCapture.setOnClickListener(view -> NativeSample.message());

    binding.anr.setOnClickListener(
        view -> {
          // Try cause ANR by blocking for 2.5 seconds.
          // By default the SDK sends an event if blocked by at least 4 seconds.
          // The time was configurable (see manifest) to 1 second for demo purposes.
          // NOTE: By default it doesn't raise if the debugger is attached. That can also be
          // configured.
          try {
            Thread.sleep(2500);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        });

    setContentView(binding.getRoot());
  }
}
