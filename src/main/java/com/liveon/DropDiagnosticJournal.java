package com.liveon;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;

/** Small local delivery timeline. Never writes payloads, tokens, names or screenshots. */
@Slf4j
final class DropDiagnosticJournal
{
	private static final long MAX_BYTES = 1024 * 1024;
	private final Path file;
	private final Executor executor;

	DropDiagnosticJournal(Path file, Executor executor)
	{
		this.file = file;
		this.executor = executor;
	}

	void record(String eventId, String stage, String detail)
	{
		if (eventId == null || !eventId.matches("[0-9a-fA-F-]{36}")) return;
		String line = Instant.now() + " " + eventId + " " + stage + " " + detail + System.lineSeparator();
		try
		{
			executor.execute(() -> {
				try
				{
					Files.createDirectories(file.getParent());
					if (Files.exists(file) && Files.size(file) > MAX_BYTES)
						Files.move(file, file.resolveSibling(file.getFileName() + ".old"),
							java.nio.file.StandardCopyOption.REPLACE_EXISTING);
					Files.write(file, line.getBytes(StandardCharsets.UTF_8),
						StandardOpenOption.CREATE, StandardOpenOption.APPEND);
				}
				catch (IOException exception)
				{
					log.debug("Unable to write drop diagnostic journal", exception);
				}
			});
		}
		catch (java.util.concurrent.RejectedExecutionException ignored)
		{
			// Shutdown can race with a final event.
		}
	}
}
