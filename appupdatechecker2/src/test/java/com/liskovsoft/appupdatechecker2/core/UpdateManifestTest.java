package com.liskovsoft.appupdatechecker2.core;

import com.liskovsoft.appupdatechecker2.ReleaseNotes;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * NEWTUBE(update-flow): the update screen shows each version's notes on their own, in the user's
 * language, and the download size when the manifest carries it. The manifest below is the shape
 * tools/update_manifest.py writes (keys in no particular order, as JSONObject iterates them).
 */
public class UpdateManifestTest {
    private static final String MANIFEST = "{"
            + "\"1.10.3\": {\"versionCode\": 11003, \"changelog\": [\"Dislike counts are now opt-in.\"],"
            + "  \"changelog_es\": [\"El contador de «no me gusta» ahora es opcional.\"]},"
            + "\"package\": {"
            + "  \"downloadUrlList_arm64-v8a\": [\"https://example.org/NewTube_1.10.5_arm64-v8a.apk\"],"
            + "  \"downloadSize_arm64-v8a\": 41234567,"
            + "  \"downloadUrlList\": [\"https://example.org/NewTube_1.10.5_universal.apk\"],"
            + "  \"downloadSize\": 68860517},"
            + "\"1.10.5\": {\"versionCode\": 11005, \"changelog\": [\"Updates show their progress.\", \"What's new after updating.\"]},"
            + "\"1.10.4\": {\"versionCode\": 11004, \"changelog\": [\"Text follows your phone's font size.\"],"
            + "  \"changelog_es\": [\"El texto sigue el tamaño de letra del móvil.\"]}"
            + "}";

    private static UpdateManifest parse(String abi, String language) throws JSONException {
        return UpdateManifest.parse(new JSONObject(MANIFEST), abi, language);
    }

    @Test
    public void releasesAreNewestFirst() throws JSONException {
        UpdateManifest manifest = parse("arm64-v8a", "en");

        assertEquals(Arrays.asList(11005, 11004, 11003), codes(manifest.releases));
        assertEquals("1.10.5", manifest.latest().versionName);
    }

    @Test
    public void newerThanTheInstalledVersionOnly() throws JSONException {
        UpdateManifest manifest = parse("arm64-v8a", "en");

        assertEquals(Arrays.asList(11005, 11004), codes(manifest.newerThan(11003)));
        assertEquals(Collections.emptyList(), codes(manifest.newerThan(11005)));
        assertEquals("1.10.3", manifest.find(11003).versionName);
        assertNull(manifest.find(11002)); // no longer listed
    }

    @Test
    public void notesInTheUsersLanguageElseEnglish() throws JSONException {
        UpdateManifest manifest = parse("arm64-v8a", "es");

        assertEquals(Collections.singletonList("El texto sigue el tamaño de letra del móvil."), manifest.find(11004).lines);
        // 1.10.5 has no Spanish list
        assertEquals(Arrays.asList("Updates show their progress.", "What's new after updating."), manifest.find(11005).lines);
    }

    @Test
    public void abiListAndItsSize() throws JSONException {
        UpdateManifest manifest = parse("arm64-v8a", "en");

        assertEquals(Collections.singletonList("https://example.org/NewTube_1.10.5_arm64-v8a.apk"), manifest.downloadUrls);
        assertEquals(41234567L, manifest.downloadSize);
    }

    @Test
    public void otherAbisTakeTheUniversalApk() throws JSONException {
        UpdateManifest manifest = parse("x86_64", "en");

        assertEquals(Collections.singletonList("https://example.org/NewTube_1.10.5_universal.apk"), manifest.downloadUrls);
        assertEquals(68860517L, manifest.downloadSize);
    }

    @Test
    public void sizeIsUnknownInOlderManifests() throws JSONException {
        JSONObject jo = new JSONObject("{\"package\": {\"downloadUrlList\": [\"https://example.org/a.apk\"]},"
                + "\"1.10.4\": {\"versionCode\": 11004, \"changelog\": [\"Check for updates works.\"]}}");

        UpdateManifest manifest = UpdateManifest.parse(jo, "arm64-v8a", "en");

        assertEquals(-1L, manifest.downloadSize);
        assertTrue(manifest.newerThan(11003).size() == 1);
    }

    @Test(expected = JSONException.class)
    public void aManifestWithoutVersionsIsAnError() throws JSONException {
        UpdateManifest.parse(new JSONObject("{\"package\": {\"downloadUrl\": \"https://example.org/a.apk\"}}"), "arm64-v8a", "en");
    }

    private static List<Integer> codes(List<ReleaseNotes> releases) {
        Integer[] codes = new Integer[releases.size()];

        for (int i = 0; i < codes.length; i++) {
            codes[i] = releases.get(i).versionCode;
        }

        return Arrays.asList(codes);
    }
}
