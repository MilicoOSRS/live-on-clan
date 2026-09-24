package com.liveon;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.*;

public class DropDiagnosticJournalTest
{
	@Test
	public void writesCorrelatedStagesWithoutAcceptingArbitraryIdentifiers() throws Exception
	{
		Path directory = Files.createTempDirectory("live-on-drop-diagnostics");
		Path file = directory.resolve("drop-diagnostics.log");
		DropDiagnosticJournal journal = new DropDiagnosticJournal(file, Runnable::run);
		String eventId = "c3a471a5-c8ce-4695-bc5d-fcc7c525ef53";
		journal.record(eventId, "DETECTED", "items=1");
		journal.record("user-supplied-text", "DETECTED", "ignored");
		String timeline = Files.readString(file);
		assertTrue(timeline.contains(eventId + " DETECTED items=1"));
		assertFalse(timeline.contains("user-supplied-text"));
	}
}
