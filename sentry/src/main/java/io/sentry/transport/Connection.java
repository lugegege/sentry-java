package io.sentry.transport;

import io.sentry.SentryEnvelope;
import java.io.IOException;
import org.jetbrains.annotations.Nullable;

public interface Connection {
  void send(SentryEnvelope event, @Nullable Object hint) throws IOException;

  default void send(SentryEnvelope envelope) throws IOException {
    send(envelope, null);
  }

  void close() throws IOException;
}
