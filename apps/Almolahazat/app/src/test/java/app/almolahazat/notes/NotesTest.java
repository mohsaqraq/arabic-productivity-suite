package app.almolahazat.notes;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipInputStream;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class NotesTest {
    private static final byte[] JPEG = {(byte)0xff,(byte)0xd8,(byte)0xff,(byte)0xd9};
    private Context context;
    private NotesStore store;

    @Before public void prepare() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("notes.db");
        store = new NotesStore(context);
    }
    @After public void close() {
        store.close();
        context.deleteDatabase("notes.db");
    }

    @Test public void filtersArabicChecklistAndExactLabelsWhileKeepingPinsFirst() throws Exception {
        Note pinned = note("زراعة");
        pinned.kind = "list";
        pinned.pinned = true;
        pinned.items.add(new Note.Row("مُرَاجَعَة الأَسْباب", true));
        pinned.labels.add("طب");
        store.save(pinned);
        Note normal = note("أدوية");
        normal.body = "مراجعة الأسباب";
        normal.labels.add("طب عام");
        store.save(normal);
        JSONArray matches = new JSONArray(store.list("active", "", "", "مراجعة الاسباب", "title", 0));
        assertEquals(2, matches.length());
        assertEquals(pinned.id, matches.getJSONObject(0).getString("id"));
        assertEquals(1, matches.getJSONObject(0).getInt("doneCount"));
        assertEquals(1, new JSONArray(store.list("active", "list", "طب", "", "edited", 0)).length());
        assertEquals(1, new JSONArray(store.list("active", "", "طب", "", "edited", 0)).length());
        assertEquals(2, new JSONArray(store.labels("active")).length());
        pinned.state = "archive";
        pinned.pinned = false;
        store.save(pinned);
        assertEquals(1, new JSONArray(store.list("archive", "", "", "", "edited", 0)).length());
        assertEquals(1, new JSONArray(store.list("active", "", "", "", "edited", 0)).length());
    }

    @Test public void photoOnlyDraftKeepsColorAndTagsAndAttachmentsFollowDeletion() throws Exception {
        Note draft = note("");
        draft.color = "gold";
        draft.labels.add("صور");
        store.save(draft);
        assertNull(store.get(draft.id));
        String photo = UUID.randomUUID().toString();
        store.attach(draft, photo, JPEG);
        Note saved = store.get(draft.id);
        assertEquals("gold", saved.color);
        assertEquals("صور", saved.labels.get(0));
        assertEquals(photo, saved.photos.get(0));
        assertArrayEquals(JPEG, store.photo(photo));
        assertFalse(store.erase(draft.id));
        saved.state = "trash";
        store.save(saved);
        assertTrue(store.erase(draft.id));
        assertNull(store.photo(photo));
    }

    @Test public void backupRoundTripPreservesPhotosAndMergesWithoutOverwriting() throws Exception {
        Note note = note("دراسة");
        note.kind = "list";
        note.items.add(new Note.Row("بداية الفصل", true));
        note.labels.add("المصادر");
        note.pinned = true;
        store.save(note);
        String photo = UUID.randomUUID().toString();
        store.attach(note, photo, JPEG);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        NotesFiles.export(store, output, false);
        byte[] archive = output.toByteArray();
        reset();
        assertEquals(1, restore(archive, false).added);
        Note restored = store.get(note.id);
        assertEquals(note.title, restored.title);
        assertEquals(note.created, restored.created);
        assertTrue(restored.pinned);
        assertTrue(restored.items.get(0).done);
        assertEquals("المصادر", restored.labels.get(0));
        assertArrayEquals(JPEG, store.photo(photo));
        restored.title = "دراسة معدلة";
        store.save(restored);
        assertEquals(1, restore(archive, false).existing);
        assertEquals("دراسة معدلة", store.get(note.id).title);
        ByteArrayOutputStream markdown = new ByteArrayOutputStream();
        NotesFiles.export(store, markdown, true);
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(markdown.toByteArray()))) {
            assertTrue(zip.getNextEntry().getName().endsWith(".md"));
            String text = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(text.contains("- [x] بداية الفصل"));
            assertTrue(text.contains("photos/" + photo + ".jpg"));
            assertEquals("photos/" + photo + ".jpg", zip.getNextEntry().getName());
        }
    }

    @Test public void malformedBackupRollsBackAllNewNotes() throws Exception {
        Note valid = note("عنوان");
        byte[] backup = zip("format.json", "{\"format\":\"almolahazat\",\"version\":1}",
            "notes/" + valid.id + ".json", valid.json(false).toString(), "notes/invalid.json", "{}");
        assertThrows(Exception.class, () -> restore(backup, false));
        assertNull(store.get(valid.id));
    }

    @Test public void keepTakeoutImportsNoteAndChecklistAndSkipsTrashAndDuplicates() throws Exception {
        String one = "{\"title\":\"مذكرة\",\"textContent\":\"بند نصي\",\"color\":\"YELLOW\","
            + "\"isPinned\":true,\"attachments\":[{\"filePath\":\"image.png\"}],"
            + "\"createdTimestampUsec\":1764900000000001,\"userEditedTimestampUsec\":1764900000000100,"
            + "\"labels\":[{\"name\":\"كتب\"}]}";
        String two = "{\"title\":\"قائمة\",\"isArchived\":true,\"createdTimestampUsec\":1764900000000002,"
            + "\"listContent\":[{\"text\":\"قراءة\",\"isChecked\":true}]}";
        byte[] backup = zip("Takeout/Keep/first.json",one,"Takeout/Keep/list.json",two,"Takeout/Keep/image.png","placeholder",
            "Takeout/Keep/trash.json","{\"title\":\"محذوف\",\"createdTimestampUsec\":1764900000000003,\"isTrashed\":true}");
        NotesFiles.ImportResult result = restore(backup, true);
        assertEquals(2, result.added);
        assertEquals(1, result.attachments);
        assertEquals(2, restore(backup, true).existing);
        JSONObject active = new JSONArray(store.list("active", "", "كتب", "بند", "edited", 0)).getJSONObject(0);
        assertEquals("gold", active.getString("color"));
        assertTrue(active.getBoolean("pinned"));
        JSONObject archived = new JSONArray(store.list("archive", "list", "", "", "edited", 0)).getJSONObject(0);
        assertTrue(archived.getJSONArray("items").getJSONObject(0).getBoolean("done"));
        assertEquals(0, new JSONArray(store.list("trash", "", "", "", "edited", 0)).length());
        assertThrows(IOException.class, () -> restore(zip("unrelated.json","{}"), true));
    }

    private void reset() {
        store.close();
        context.deleteDatabase("notes.db");
        store = new NotesStore(context);
    }

    private NotesFiles.ImportResult restore(byte[] archive, boolean keep) throws Exception {
        return NotesFiles.restore(store, new ByteArrayInputStream(archive), keep);
    }

    private static Note note(String title) throws Exception {
        return Note.parse(new JSONObject().put("id", UUID.randomUUID().toString()).put("title", title));
    }

    private static byte[] zip(String... pairs) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (int i=0; i<pairs.length; i+=2) {
                zip.putNextEntry(new ZipEntry(pairs[i]));
                zip.write(pairs[i+1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
