package com.liveon;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import okhttp3.*;
import okio.Buffer;

/** Disk operations are invoked only from the delivery executor or OkHttp callbacks. */
final class DropOutbox
{
    private final Path directory;
    private final Gson gson;

    DropOutbox(Path directory, Gson gson) { this.directory = directory; this.gson = gson; }

    String save(Request initial, Request retry, String destination) throws IOException
    {
        Entry entry = new Entry();
        entry.id = UUID.randomUUID().toString();
        entry.url = initial.url().toString();
        entry.player = initial.header("X-Live-On-Player");
        entry.destination = destination;
        entry.eventId = initial.header("X-Live-On-Event");
        entry.initialImage = initial.header("X-Live-On-Image");
        entry.retryImage = retry.header("X-Live-On-Image");
        entry.body = body(initial);
        entry.retryBody = body(retry);
        entry.contentType = initial.body().contentType().toString();
        entry.retryContentType = retry.body().contentType().toString();
        write(entry);
        return entry.id;
    }

    private static String body(Request request) throws IOException
    {
        Buffer buffer = new Buffer();
        request.body().writeTo(buffer);
        return Base64.getEncoder().encodeToString(buffer.readByteArray());
    }

    private void write(Entry entry) throws IOException
    {
        Files.createDirectories(directory);
        Path target = directory.resolve(entry.id + ".json");
        Path temporary = directory.resolve(entry.id + ".tmp");
        Files.write(temporary, gson.toJson(entry).getBytes(StandardCharsets.UTF_8));
        try { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
    }

    void complete(String id) throws IOException { if (id != null) Files.deleteIfExists(directory.resolve(id + ".json")); }

    List<Entry> pending(HttpUrl base, String player) throws IOException
    {
        List<Entry> result = new ArrayList<>();
        if (!Files.isDirectory(directory)) return result;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.json"))
        {
            for (Path file : files)
            {
                try
                {
                    Entry entry = gson.fromJson(Files.readString(file), Entry.class);
                    if (entry == null || !file.getFileName().toString().equals(entry.id + ".json")
                        || entry.player == null || !entry.player.equalsIgnoreCase(player)) continue;
                    HttpUrl url = HttpUrl.parse(entry.url);
                    if (url == null || !url.scheme().equals(base.scheme()) || !url.host().equals(base.host())
                        || url.port() != base.port() || url.query() != null) continue;
                    boolean allowed = false;
                    for (String path : new String[] {"stats/drops", "stats/pbs", "notifications/discord", "notifications/bingo"})
                        allowed |= url.equals(base.newBuilder().addPathSegments(path).build());
                    if (allowed) { entry.request(false); entry.request(true); result.add(entry); }
                }
                catch (IOException | RuntimeException malformed) { /* Retain unreadable records for diagnosis. */ }
            }
        }
        return result;
    }

    static final class Entry
    {
        String id, url, player, destination, eventId, initialImage, retryImage,
            body, retryBody, contentType, retryContentType;
        Request request(boolean retry)
        {
            return new Request.Builder().url(url).header("X-Live-On-Player", player)
                .header("Authorization", "LiveOnPlayer " + player)
                .header("X-Live-On-Event", eventId == null ? "" : eventId)
                .header("X-Live-On-Image", (retry ? retryImage : initialImage) == null
                    ? "unknown" : (retry ? retryImage : initialImage))
                .post(RequestBody.create(MediaType.parse(retry ? retryContentType : contentType),
                    Base64.getDecoder().decode(retry ? retryBody : body))).build();
        }
    }
}
