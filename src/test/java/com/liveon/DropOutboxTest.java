package com.liveon;

import com.google.gson.Gson;
import java.nio.file.*;
import java.util.*;
import okhttp3.*;
import okio.Buffer;
import org.junit.Test;
import static org.junit.Assert.*;

public class DropOutboxTest
{
    @Test public void actualTransportRecoversAfterShutdown() throws Exception
    {
        Path folder = Files.createTempDirectory("drop-transport-restart");
        java.util.concurrent.ScheduledExecutorService executor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        java.util.concurrent.BlockingQueue<Runnable> callbacks = new java.util.concurrent.LinkedBlockingQueue<>();
        java.util.concurrent.BlockingQueue<Request> requests = new java.util.concurrent.LinkedBlockingQueue<>();
        java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger();
        OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain -> {
            requests.add(chain.request());
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(attempts.incrementAndGet() == 1 ? 503 : 200).message("test")
                .body(ResponseBody.create(null, "{}")).build();
        }).build();
        try {
            DropOutbox outbox = new DropOutbox(folder, new Gson());
            HttpUrl base = HttpUrl.parse("https://example.invalid/");
            Request request = new Request.Builder().url(base.resolve("stats/drops"))
                .header("X-Live-On-Player", "Tamzz")
                .header("X-Live-On-Event", "c3a471a5-c8ce-4695-bc5d-fcc7c525ef53")
                .post(RequestBody.create(MediaType.parse("application/json"), "{\"eventId\":\"fixed-event\"}")).build();
            try (DropDeliveryClient first = new DropDeliveryClient(http, callbacks::add, executor)) {
                first.setOutbox(outbox);
                first.send(request, request, "MVP", () -> DropSessionGate.State.READY);
                Runnable dispatch = callbacks.poll(5, java.util.concurrent.TimeUnit.SECONDS);
                assertNotNull(dispatch); dispatch.run();
                assertNotNull(requests.poll(5, java.util.concurrent.TimeUnit.SECONDS));
                assertNotNull(callbacks.poll(5, java.util.concurrent.TimeUnit.SECONDS));
            }
            assertEquals(1, outbox.pending(base, "Tamzz").size());
            try (DropDeliveryClient restarted = new DropDeliveryClient(http, callbacks::add, executor)) {
                restarted.setOutbox(new DropOutbox(folder, new Gson()));
                restarted.recover(base, "Tamzz", path -> DropSessionGate.State.READY);
                Runnable dispatch = callbacks.poll(5, java.util.concurrent.TimeUnit.SECONDS);
                assertNotNull(dispatch); dispatch.run();
                Request recovered = requests.poll(5, java.util.concurrent.TimeUnit.SECONDS);
                assertNotNull(recovered);
                Buffer body = new Buffer(); recovered.body().writeTo(body);
                assertEquals("{\"eventId\":\"fixed-event\"}", body.readUtf8());
                assertEquals("Tamzz", recovered.header("X-Live-On-Player"));
                assertEquals("c3a471a5-c8ce-4695-bc5d-fcc7c525ef53",
                    recovered.header("X-Live-On-Event"));
            }
        } finally {
            executor.shutdownNow();
            http.dispatcher().executorService().shutdown();
            http.dispatcher().executorService().awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
            http.connectionPool().evictAll();
            try (java.util.stream.Stream<Path> files = Files.list(folder)) {
                for (Path file : (Iterable<Path>) files::iterator) Files.delete(file);
            }
            Files.delete(folder);
        }
    }

    @Test public void restartPreservesBodyIdentityAndIdempotency() throws Exception
    {
        Path folder = Files.createTempDirectory("drop-outbox-test");
        try {
            Gson gson = new Gson();
            HttpUrl base = HttpUrl.parse("https://example.invalid/");
            Request original = new Request.Builder().url(base.resolve("notifications/discord"))
                .header("X-Live-On-Player", "Tamzz").header("Authorization", "Bearer do-not-store")
                .post(RequestBody.create(MediaType.parse("application/json"),
                    "{\"idempotencyKey\":\"stable-drop\",\"playerName\":\"Tamzz\"}")).build();
            DropOutbox first = new DropOutbox(folder, gson);
            String id = first.save(original, original, "CM drop");
            assertFalse(Files.readString(folder.resolve(id + ".json")).contains("do-not-store"));
            DropOutbox restarted = new DropOutbox(folder, gson);
            assertTrue(restarted.pending(base, "Other Account").isEmpty());
            assertTrue(restarted.pending(HttpUrl.parse("https://other.invalid/"), "Tamzz").isEmpty());
            List<DropOutbox.Entry> pending = restarted.pending(base, "tamzz");
            assertEquals(1, pending.size());
            Request restored = pending.get(0).request(false);
            assertEquals("Tamzz", restored.header("X-Live-On-Player"));
            assertEquals("LiveOnPlayer Tamzz", restored.header("Authorization"));
            Buffer before = new Buffer(), after = new Buffer();
            original.body().writeTo(before); restored.body().writeTo(after);
            assertEquals(before.readUtf8(), after.readUtf8());
            restarted.complete(id);
            assertTrue(restarted.pending(base, "Tamzz").isEmpty());
        } finally {
            try (java.util.stream.Stream<Path> files = Files.list(folder)) {
                for (Path file : (Iterable<Path>) files::iterator) Files.delete(file);
            }
            Files.delete(folder);
        }
    }

    @Test public void corruptRecordDoesNotHideFollowingRecords() throws Exception
    {
        Path folder = Files.createTempDirectory("drop-outbox-corrupt");
        try {
            Files.writeString(folder.resolve("broken.json"), "{");
            DropOutbox box = new DropOutbox(folder, new Gson());
            Request request = new Request.Builder().url("https://example.invalid/stats/drops")
                .header("X-Live-On-Player", "Milico")
                .post(RequestBody.create(MediaType.parse("application/json"), "{}")).build();
            String id = box.save(request, request, "MVP");
            assertEquals(1, box.pending(HttpUrl.parse("https://example.invalid/"), "Milico").size());
            box.complete(id);
            assertTrue(Files.exists(folder.resolve("broken.json")));
        } finally {
            try (java.util.stream.Stream<Path> files = Files.list(folder)) {
                for (Path file : (Iterable<Path>) files::iterator) Files.delete(file);
            }
            Files.delete(folder);
        }
    }
}
