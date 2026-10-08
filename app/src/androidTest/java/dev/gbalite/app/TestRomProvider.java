package dev.gbalite.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** TEST ONLY provider lives in the standalone test APK process, without target Kotlin dependencies. */
public final class TestRomProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "application/octet-stream"; }
    private File rom(Uri uri) {
        if ("phase6-revoked.gba".equals(uri.getLastPathSegment()))
            throw new SecurityException("TEST ONLY: source URI access revoked");
        String asset = ("persistence.gba".equals(uri.getLastPathSegment()) || "renamed.gba".equals(uri.getLastPathSegment())) ? "persistence.gba" : "bringup.gba";
        if ("color-pattern.gba".equals(uri.getLastPathSegment()) || "lcd-pattern.gba".equals(uri.getLastPathSegment())) asset = uri.getLastPathSegment();
        String requested=uri.getLastPathSegment();
        // Explicitly staged, audited homebrew only; no external path or arbitrary file URI.
        if ("blob-goes-3d-v1.1.gba".equals(requested) || "hyperspace-roll-agb-v0.25.0.gba".equals(requested)) {
            String expected = "blob-goes-3d-v1.1.gba".equals(requested)
                ? "313cbb23444ef35497b4de23a459311f732c885d392f5e465ebe1861a26746f4"
                : "de5a61698452d5f409970f1f5e042d0d4ace21ccc0ea7d2a770b8c311b8f3c57";
            File staged = new File(getContext().getFilesDir(), "phase7-gameplay/" + requested);
            try {
                java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
                try (InputStream in=new java.io.FileInputStream(staged)) {
                    byte[] buffer=new byte[16384];int n;
                    while((n=in.read(buffer))!=-1) digest.update(buffer,0,n);
                }
                StringBuilder hex=new StringBuilder();
                for(byte b:digest.digest()) hex.append(String.format(java.util.Locale.ROOT,"%02x",b & 255));
                if(!hex.toString().equals(expected))
                    throw new SecurityException("Staged homebrew hash mismatch");
                return staged;
            } catch (IOException | java.security.NoSuchAlgorithmException e) {
                throw new IllegalStateException("Audited homebrew must be staged explicitly",e);
            }
        }
        if ("phase7-stress-a.gba".equals(requested) || "phase7-stress-b.gba".equals(requested) || "phase7-stress-c.gba".equals(requested) || "phase7-upgrade.gba".equals(requested) || "mgba-suite-shifter.gba".equals(requested)) asset=requested;
        if ("phase6-library.gba".equals(requested) || "phase6-other.gba".equals(requested) || "phase6-single.zip".equals(requested) || "phase6-multiple.zip".equals(requested) || "phase6-crud.gba".equals(requested) || "phase6-crud.zip".equals(requested)) asset=requested;
        if ("phase6-slow.gba".equals(requested) || "phase6-timeout.gba".equals(requested)) asset="phase6-slow.gba";
        if ("rtc-probe.gba".equals(requested) || "tilt-probe.gba".equals(requested) || "rotation-probe.gba".equals(requested) || "solar-probe.gba".equals(requested) || "rumble-probe.gba".equals(requested)) asset=requested;
        File file = new File(getContext().getCacheDir(), "phase3-test-" + asset);
        if (!file.exists()) {
            try (InputStream input = getContext().getAssets().open(asset);
                 FileOutputStream output = new FileOutputStream(file)) {
                byte[] buffer = new byte[4096];
                int n;
                while ((n = input.read(buffer)) != -1) output.write(buffer, 0, n);
                if (!asset.startsWith("phase6-") && !asset.startsWith("phase7-") && !asset.equals("mgba-suite-shifter.gba")) output.write("PHASE3_TEST_ONLY".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            } catch (IOException e) { throw new IllegalStateException("Test fixture unavailable", e); }
        }
        return file;
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        String[] columns = projection != null ? projection : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor cursor = new MatrixCursor(columns);
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; ++i) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) row[i] = "renamed.gba".equals(uri.getLastPathSegment()) ? "renamed.gba" : rom(uri).getName();
            else if (OpenableColumns.SIZE.equals(columns[i])) row[i] = rom(uri).length();
        }
        cursor.addRow(row);
        return cursor;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read only test fixture");
        if ("phase6-slow.gba".equals(uri.getLastPathSegment()) || "phase6-timeout.gba".equals(uri.getLastPathSegment())) {
            try {
                final File source=rom(uri);
                final ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe();
                final long delay="phase6-timeout.gba".equals(uri.getLastPathSegment()) ? 1000 : 100;
                Thread worker=new Thread(() -> {
                    try (java.io.InputStream in=new java.io.FileInputStream(source);
                         java.io.OutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                        byte[] buffer=new byte[16384];int n;
                        while ((n=in.read(buffer))!=-1) {Thread.sleep(delay);out.write(buffer,0,n);out.flush();}
                    } catch (Exception ignored) { /* Consumer cancellation/timeout closes the pipe. */ }
                },"test-only-slow-rom-provider");worker.setDaemon(true);worker.start();return pipe[0];
            } catch (IOException e) {throw new FileNotFoundException("Slow fixture pipe unavailable");}
        }
        return ParcelFileDescriptor.open(rom(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
