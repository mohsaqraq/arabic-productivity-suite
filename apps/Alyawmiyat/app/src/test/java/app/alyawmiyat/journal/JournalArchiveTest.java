package app.alyawmiyat.journal;

import static org.junit.Assert.*;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class JournalArchiveTest {
    private static final String TEXT_ID = "00000001-0000-0000-0000-000000000000";
    private static final String INK_ID = "00000002-0000-0000-0000-000000000000";
    private static final String PHOTO_ID = "00000003-0000-0000-0000-000000000000";
    private static final String TITLE = "أوّل يومٍ في المشفى";
    private static final String BODY = "\nوصلتُ باكرًا.\nالتقيت زميلي <صورة> 🙂";
    private static final String INK = "[[false,[[20,45,26],[90,110,74]]],[true,[[33,55,50]]]]";
    private Context context;
    private JournalStore store;

    @Before public void create() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("journal.db");
        store = new JournalStore(context);
    }
    @After public void clean() {
        store.close();
        context.deleteDatabase("journal.db");
    }

    @Test public void datedSearchAndWritingRetention() throws Exception {
        fill();
        store.close();
        store = new JournalStore(context);
        JSONObject list = new JSONObject(store.list("اول يوم في المشفي","",0));
        JSONArray matches = list.getJSONArray("entries");
        assertEquals(1,matches.length());
        assertEquals(TEXT_ID,matches.getJSONObject(0).getString("id"));
        assertEquals(1,matches.getJSONObject(0).getInt("photos"));
        assertEquals(0,new JSONObject(store.list("","2026-11-22",0)).getJSONArray("entries").length());
        assertEquals(BODY,new JSONObject(store.read(TEXT_ID)).getString("body"));
        assertEquals(INK,new JSONObject(store.read(INK_ID)).getJSONArray("ink").toString());
        assertTrue(store.hasEntryOn("2026-11-23"));
    }

    @Test public void fullBackupRestoresArabicHandwritingAndPhoto() throws Exception {
        byte[] photo = fill();
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        ArchiveIO.exportBackup(store,zip);
        store.deleteEntry(TEXT_ID);
        store.deleteEntry(INK_ID);
        assertNull(store.photo(PHOTO_ID));
        assertEquals(2,ArchiveIO.importBackup(store,new ByteArrayInputStream(zip.toByteArray())));
        assertEquals(BODY,new JSONObject(store.read(TEXT_ID)).getString("body"));
        assertArrayEquals(photo,store.photo(PHOTO_ID));
        assertEquals(INK,new JSONObject(store.read(INK_ID)).getJSONArray("ink").toString());

        ByteArrayOutputStream exported = new ByteArrayOutputStream();
        ArchiveIO.exportReadable(store,exported,"");
        String html = exported.toString("UTF-8");
        assertTrue(html.contains("lang=ar dir=rtl"));
        assertTrue(html.contains("&lt;صورة&gt;"));
        assertTrue(html.contains("data:image/jpeg;base64,"));
        assertTrue(html.contains("data:image/png;base64,"));
    }

    @Test public void missingFooterDoesNotReplaceExistingEntries() throws Exception {
        fill();
        ByteArrayOutputStream original = new ByteArrayOutputStream();
        ArchiveIO.exportBackup(store,original);
        ByteArrayOutputStream incomplete = new ByteArrayOutputStream();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(original.toByteArray()));
             ZipOutputStream output = new ZipOutputStream(incomplete)) {
            ZipEntry next;
            while ((next = input.getNextEntry()) != null) {
                if (next.getName().equals("complete.json")) break;
                output.putNextEntry(new ZipEntry(next.getName()));
                output.write(ArchiveIO.read(input,2_000_000));
                output.closeEntry();
            }
        }
        store.save(new EntryData(TEXT_ID,"2026-11-23","تنقيح لاحق","موجود","text",new JSONArray(),33));
        try {
            ArchiveIO.importBackup(store,new ByteArrayInputStream(incomplete.toByteArray()));
            fail("Incomplete backup was accepted");
        } catch (IOException expected) {
            assertEquals("تنقيح لاحق",new JSONObject(store.read(TEXT_ID)).getString("title"));
            assertNotNull(store.photo(PHOTO_ID));
        }
    }

    @Test public void validatesLeapDaysAndPaperCoordinateLimits() throws Exception {
        assertTrue(EntryData.validDate("2028-02-29"));
        assertFalse(EntryData.validDate("2027-02-29"));
        assertFalse(EntryData.validDate("2026-13-20"));
        EntryData data = new EntryData(INK_ID,"2026-11-23","","","ink",new JSONArray(INK),22);
        assertEquals(INK,EntryData.parse(data.json().toString()).ink.toString());
        assertThrows(org.json.JSONException.class,() -> EntryData.parse(data.json().put("ink",
            new JSONArray("[[false,[[801,500,100]]]]")).toString()));
    }

    private byte[] fill() throws Exception {
        store.save(new EntryData(TEXT_ID,"2026-11-23",TITLE,BODY,"text",new JSONArray(),21));
        store.save(new EntryData(INK_ID,"2026-11-20","حبر","","ink",new JSONArray(INK),22));
        Bitmap image = Bitmap.createBitmap(50,30,Bitmap.Config.ARGB_8888);
        image.eraseColor(Color.GREEN);
        ByteArrayOutputStream jpg = new ByteArrayOutputStream();
        assertTrue(image.compress(Bitmap.CompressFormat.JPEG,85,jpg));
        image.recycle();
        byte[] photo = jpg.toByteArray();
        assertTrue(store.addPhoto(EntryData.emptyText(TEXT_ID,"2026-11-23"),PHOTO_ID,photo));
        return photo;
    }
}
